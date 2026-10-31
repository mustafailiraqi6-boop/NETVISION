package com.netvision.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netvision.app.NetVisionApplication
import com.netvision.app.models.DeviceFolder
import com.netvision.app.models.DeviceStatus
import com.netvision.app.models.NetworkDevice
import com.netvision.app.monitoring.SnmpDeviceInfo
import com.netvision.app.monitoring.SnmpDiagnosticRecord
import com.netvision.app.monitoring.SnmpV2Client
import com.netvision.app.network.NetworkReachabilityService
import com.netvision.app.repository.DeviceRepository
import com.netvision.app.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MonitoringUiState(
    val selectedDeviceId: String? = null,
    val selectedFolderId: String? = null,
    val searchQuery: String = "",
    val isAutoPolling: Boolean = true,
    val refreshIntervalSec: Int = 10,
    val lastRefreshedTime: Long = 0L,
    val isPollingNow: Boolean = false,
    val snmpStatusByDevice: Map<String, SnmpDeviceInfo> = emptyMap(),
    val diagnosticLogs: List<SnmpDiagnosticRecord> = emptyList()
)

class MonitoringViewModel(
    private val repository: DeviceRepository = try {
        NetVisionApplication.instance.deviceRepository
    } catch (e: Exception) {
        DeviceRepository()
    },
    private val settingsRepository: SettingsRepository = try {
        NetVisionApplication.instance.settingsRepository
    } catch (e: Exception) {
        SettingsRepository(NetVisionApplication.instance)
    },
    private val reachabilityService: NetworkReachabilityService = NetworkReachabilityService()
) : ViewModel() {

    val savedDevices: StateFlow<List<NetworkDevice>> = repository.getAllDevices()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val savedFolders: StateFlow<List<DeviceFolder>> = repository.getAllFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _uiState = MutableStateFlow(MonitoringUiState())
    val uiState: StateFlow<MonitoringUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        startPeriodicPolling()
    }

    fun selectDevice(deviceId: String) {
        _uiState.value = _uiState.value.copy(selectedDeviceId = deviceId)
    }

    fun selectFolder(folderId: String?) {
        _uiState.value = _uiState.value.copy(selectedFolderId = folderId)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun toggleAutoPolling(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isAutoPolling = enabled)
        if (enabled) {
            startPeriodicPolling()
        } else {
            pollingJob?.cancel()
        }
    }

    fun setRefreshInterval(seconds: Int) {
        _uiState.value = _uiState.value.copy(refreshIntervalSec = seconds)
        if (_uiState.value.isAutoPolling) {
            startPeriodicPolling()
        }
    }

    fun refreshNow() {
        viewModelScope.launch {
            pollAllDevices()
        }
    }

    private fun startPeriodicPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                if (_uiState.value.isAutoPolling) {
                    pollAllDevices()
                }
                delay((_uiState.value.refreshIntervalSec * 1000L).coerceAtLeast(3000L))
            }
        }
    }

    private suspend fun pollAllDevices() = withContext(Dispatchers.IO) {
        val currentDevices = savedDevices.value
        if (currentDevices.isEmpty()) return@withContext

        _uiState.value = _uiState.value.copy(isPollingNow = true)
        val updatedSnmpMap = _uiState.value.snmpStatusByDevice.toMutableMap()

        val settings = settingsRepository.getSettings()
        val community = settings.snmpCommunity.ifBlank { "netvision" }
        val snmpPort = settings.snmpPort

        for (device in currentDevices) {
            // Check reachability via TCP Connect / ICMP
            val reachability = reachabilityService.checkHostReachability(device.ipAddress)
            val isOnline = reachability.isReachable
            val latencyMs = reachability.latencyMs.toInt().coerceAtLeast(0)

            // Perform SNMP query with saved SNMP community string (netvision)
            var uptime: String? = null
            var negotiatedVersion: String? = device.snmpVersion
            try {
                val snmpRes = SnmpV2Client.queryDeviceInfo(
                    targetIp = device.ipAddress,
                    community = community,
                    port = snmpPort,
                    preferredVersion = device.snmpVersion,
                    timeoutMs = 1000
                )
                if (snmpRes.isSuccess) {
                    val info = snmpRes.getOrNull()
                    if (info != null) {
                        updatedSnmpMap[device.id] = info
                        if (info.isSuccess) {
                            uptime = info.uptimeFormatted
                            negotiatedVersion = info.snmpVersion
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore SNMP exception so device connection status remains ONLINE if TCP/ICMP succeeded
            }

            repository.updateDeviceStatus(
                deviceId = device.id,
                isOnline = isOnline,
                latencyMs = latencyMs,
                uptime = uptime,
                snmpVersion = negotiatedVersion
            )
        }

        _uiState.value = _uiState.value.copy(
            isPollingNow = false,
            lastRefreshedTime = System.currentTimeMillis(),
            snmpStatusByDevice = updatedSnmpMap,
            diagnosticLogs = SnmpV2Client.getDiagnosticHistory().reversed()
        )
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
