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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

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

    private suspend fun pollAllDevices() {
        val currentDevices = savedDevices.value
        if (currentDevices.isEmpty()) return

        _uiState.value = _uiState.value.copy(isPollingNow = true)
        val updatedSnmpMap = _uiState.value.snmpStatusByDevice.toMutableMap()

        for (device in currentDevices) {
            val (isOnline, latencyMs) = testHostReachability(device.ipAddress)

            // Try SNMP query
            var uptime: String? = null
            val snmpRes = SnmpV2Client.queryDeviceInfo(device.ipAddress, timeoutMs = 800)
            if (snmpRes.isSuccess) {
                val info = snmpRes.getOrNull()
                if (info != null) {
                    updatedSnmpMap[device.id] = info
                    uptime = info.uptimeFormatted
                }
            }

            // CRITICAL: Reachability is independent of SNMP. If ICMP/TCP succeeds, device is ONLINE!
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

    private fun testHostReachability(ip: String): Pair<Boolean, Int> {
        val portsToProbe = listOf(8291, 8728, 80, 443, 22)
        val startTime = System.currentTimeMillis()

        for (port in portsToProbe) {
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(ip, port), 400)
                    val latency = (System.currentTimeMillis() - startTime).toInt().coerceAtLeast(1)
                    return Pair(true, latency)
                }
            } catch (e: Exception) {}
        }

        // Fallback to ICMP ping
        try {
            val addr = InetAddress.getByName(ip)
            if (addr.isReachable(500)) {
                val latency = (System.currentTimeMillis() - startTime).toInt().coerceAtLeast(1)
                return Pair(true, latency)
            }
        } catch (e: Exception) {}

        return Pair(false, 0)
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
