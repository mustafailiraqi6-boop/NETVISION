package com.netvision.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netvision.app.NetVisionApplication
import com.netvision.app.models.DeviceStatus
import com.netvision.app.models.Manufacturer
import com.netvision.app.models.NetworkDevice
import com.netvision.app.models.RecentDeviceActivity
import com.netvision.app.network.NetworkReachabilityService
import com.netvision.app.repository.DeviceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private data class DashboardProbeResult(
    val isOnline: Boolean,
    val latencyMs: Long
)

data class DashboardUiState(
    val totalDevices: Int = 0,
    val onlineDevices: Int = 0,
    val offlineDevices: Int = 0,
    val ubntDevicesCount: Int = 0,
    val mikrotikDevicesCount: Int = 0,
    val avgLatencyMs: Double = 0.0,
    val healthPercentage: Int = 100,
    val recentActivity: List<RecentDeviceActivity> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false
)

class DashboardViewModel(
    private val repository: DeviceRepository = try {
        NetVisionApplication.instance.deviceRepository
    } catch (e: Exception) {
        DeviceRepository()
    }
) : ViewModel() {

    // Dashboard-only results: never write these quick checks into the shared
    // device status in Room, so Monitoring keeps its existing multi-port logic.
    private val dashboardProbeResults =
        MutableStateFlow<Map<String, DashboardProbeResult>>(emptyMap())
    private val _isRefreshing = MutableStateFlow(false)
    private val probeSemaphore = Semaphore(10)
    private var probeJob: Job? = null
    private val reachabilityService by lazy {
        NetworkReachabilityService(NetVisionApplication.instance)
    }

    init {
        viewModelScope.launch {
            repository.getAllDevices()
                .distinctUntilChanged { old, new ->
                    old.map { it.id to it.ipAddress } == new.map { it.id to it.ipAddress }
                }
                .collect { devices ->
                    startDashboardProbe(devices)
                }
        }
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.getAllDevices(),
        repository.recentActivities,
        dashboardProbeResults,
        _isRefreshing
    ) { devices, activities, probeResults, isRefreshing ->
        val total = devices.size
        val online = devices.count { device ->
            probeResults[device.id]?.isOnline
                ?: (device.status == DeviceStatus.ONLINE)
        }
        val offline = devices.count { device ->
            probeResults[device.id]?.let { !it.isOnline }
                ?: (device.status == DeviceStatus.OFFLINE)
        }
        val ubnt = devices.count { it.manufacturer == Manufacturer.UBIQUITI }
        val mtk = devices.count { it.manufacturer == Manufacturer.MIKROTIK }

        val onlineLatencies = devices.mapNotNull { device ->
            val result = probeResults[device.id]
            when {
                result != null && result.isOnline && result.latencyMs > 0 -> result.latencyMs.toDouble()
                result == null && device.status == DeviceStatus.ONLINE &&
                    device.metrics.pingLatencyMs > 0 -> device.metrics.pingLatencyMs.toDouble()
                else -> null
            }
        }
        val avgLatency = onlineLatencies.average().takeIf { onlineLatencies.isNotEmpty() } ?: 0.0
        val health = if (total > 0) ((online.toDouble() / total) * 100).toInt() else 100

        DashboardUiState(
            totalDevices = total,
            onlineDevices = online,
            offlineDevices = offline,
            ubntDevicesCount = ubnt,
            mikrotikDevicesCount = mtk,
            avgLatencyMs = avgLatency,
            healthPercentage = health,
            recentActivity = activities,
            isLoading = false,
            isRefreshing = isRefreshing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(isLoading = true)
    )

    fun refreshDashboardStatuses() {
        viewModelScope.launch {
            val devices = repository.getAllDevices().first()
            startDashboardProbe(devices)
        }
    }

    private fun startDashboardProbe(devices: List<NetworkDevice>) {
        probeJob?.cancel()
        if (devices.isEmpty()) {
            dashboardProbeResults.value = emptyMap()
            _isRefreshing.value = false
            return
        }

        // Never carry cached ONLINE states into the dashboard. Until the VPN
        // confirms reachability, every device is considered offline here.
        dashboardProbeResults.value = devices.associate { device ->
            device.id to DashboardProbeResult(isOnline = false, latencyMs = 0L)
        }
        _isRefreshing.value = true

        probeJob = viewModelScope.launch {
            try {
                val vpnConnected = withContext(Dispatchers.IO) {
                    reachabilityService.isVpnConnected()
                }
                // Without an active Android VPN, keep all dashboard devices offline.
                if (!vpnConnected) return@launch

                coroutineScope {
                    devices.map { device ->
                        async(Dispatchers.IO) {
                            probeSemaphore.withPermit {
                                val result = try {
                                    reachabilityService.checkHostReachability(
                                        targetIp = device.ipAddress,
                                        portsToProbe = listOf(80),
                                        timeoutMs = 400
                                    )
                                } catch (_: Exception) {
                                    null
                                }

                                // A failed probe (including VPN loss mid-scan) is OFFLINE.
                                val updated = dashboardProbeResults.value.toMutableMap()
                                updated[device.id] = DashboardProbeResult(
                                    isOnline = result?.isReachable == true,
                                    latencyMs = result?.latencyMs?.coerceAtLeast(0L) ?: 0L
                                )
                                dashboardProbeResults.value = updated
                            }
                        }
                    }.awaitAll()
                }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    override fun onCleared() {
        probeJob?.cancel()
        super.onCleared()
    }
}
