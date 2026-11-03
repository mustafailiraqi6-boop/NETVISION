package com.netvision.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netvision.app.NetVisionApplication
import com.netvision.app.models.DeviceStatus
import com.netvision.app.models.Manufacturer
import com.netvision.app.models.RecentDeviceActivity
import com.netvision.app.repository.DeviceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val totalDevices: Int = 0,
    val onlineDevices: Int = 0,
    val offlineDevices: Int = 0,
    val uncheckedDevices: Int = 0,
    val ubntDevicesCount: Int = 0,
    val mikrotikDevicesCount: Int = 0,
    val avgLatencyMs: Double = 0.0,
    val healthPercentage: Int = 100,
    val recentActivity: List<RecentDeviceActivity> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false
)

/**
 * Dashboard is a read-only projection of the shared Room device state.
 * MonitoringViewModel is the single writer of ONLINE/OFFLINE status.
 */
class DashboardViewModel(
    private val repository: DeviceRepository = try {
        NetVisionApplication.instance.deviceRepository
    } catch (e: Exception) {
        DeviceRepository()
    }
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.getAllDevices(),
        repository.recentActivities
    ) { devices, activities ->
        val total = devices.size
        val online = devices.count { it.status == DeviceStatus.ONLINE }
        val offline = devices.count { it.lastPingTimestamp > 0L && it.status == DeviceStatus.OFFLINE }
        val unchecked = devices.count { it.lastPingTimestamp == 0L }
        val latencies = devices.filter { it.status == DeviceStatus.ONLINE }
            .mapNotNull { it.metrics.pingLatencyMs.takeIf { latency -> latency > 0 }?.toDouble() }
        DashboardUiState(
            totalDevices = total,
            onlineDevices = online,
            offlineDevices = offline,
            uncheckedDevices = unchecked,
            ubntDevicesCount = devices.count { it.manufacturer == Manufacturer.UBIQUITI },
            mikrotikDevicesCount = devices.count { it.manufacturer == Manufacturer.MIKROTIK },
            avgLatencyMs = latencies.average().takeIf { latencies.isNotEmpty() } ?: 0.0,
            healthPercentage = if (online + offline > 0) ((online.toDouble() / (online + offline)) * 100).toInt() else 0,
            recentActivity = activities,
            isLoading = false,
            isRefreshing = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(isLoading = true)
    )

    /** Requests the central monitoring engine to refresh shared device status. */
    fun refreshDashboardStatuses() {
        NetVisionApplication.notifyAppForegrounded()
    }
}
