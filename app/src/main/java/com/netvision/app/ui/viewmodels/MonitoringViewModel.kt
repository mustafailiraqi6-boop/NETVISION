package com.netvision.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netvision.app.NetVisionApplication
import com.netvision.app.models.DeviceStatus
import com.netvision.app.models.NetworkDevice
import com.netvision.app.monitoring.SnmpDeviceInfo
import com.netvision.app.monitoring.SnmpV2Client
import com.netvision.app.network.NetworkReachabilityService
import com.netvision.app.repository.DeviceRepository
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
    val isAutoPolling: Boolean = true,
    val refreshIntervalSec: Int = 10,
    val lastRefreshedTime: Long = 0L,
    val isPollingNow: Boolean = false,
    val snmpStatusByDevice: Map<String, SnmpDeviceInfo> = emptyMap()
)

class MonitoringViewModel(
    private val repository: DeviceRepository = try {
        NetVisionApplication.instance.deviceRepository
    } catch (e: Exception) {
        DeviceRepository()
    },
    private val reachabilityService: NetworkReachabilityService = NetworkReachabilityService()
) : ViewModel() {

    val savedDevices: StateFlow<List<NetworkDevice>> = repository.getAllDevices()
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

        for (device in currentDevices) {
            // Check reachability via TCP port 80 (WebFig / Web UI), 443, 8291, 8728, etc. and fallback to ICMP
            val reachability = reachabilityService.checkHostReachability(device.ipAddress)
            val isOnline = reachability.isReachable
            val latencyMs = reachability.latencyMs.toInt().coerceAtLeast(0)

            // Try SNMP query separately — failure of SNMP or API query does NOT override connection status
            var uptime: String? = null
            try {
                val snmpRes = SnmpV2Client.queryDeviceInfo(device.ipAddress, timeoutMs = 800)
                if (snmpRes.isSuccess) {
                    val info = snmpRes.getOrNull()
                    if (info != null) {
                        updatedSnmpMap[device.id] = info
                        uptime = info.uptimeFormatted
                    }
                }
            } catch (e: Exception) {
                // Ignore SNMP errors so device status remains ONLINE if TCP/ICMP succeeded
            }

            repository.updateDeviceStatus(
                deviceId = device.id,
                isOnline = isOnline,
                latencyMs = latencyMs,
                uptime = uptime
            )
        }

        _uiState.value = _uiState.value.copy(
            isPollingNow = false,
            lastRefreshedTime = System.currentTimeMillis(),
            snmpStatusByDevice = updatedSnmpMap
        )
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
