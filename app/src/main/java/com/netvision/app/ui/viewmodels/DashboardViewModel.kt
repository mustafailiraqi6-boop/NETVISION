package com.netvision.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netvision.app.NetVisionApplication
import com.netvision.app.models.DeviceStatus
import com.netvision.app.models.Manufacturer
import com.netvision.app.models.NetworkDevice
import com.netvision.app.models.RecentDeviceActivity
import com.netvision.app.repository.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val totalDevices: Int = 0,
    val onlineDevices: Int = 0,
    val offlineDevices: Int = 0,
    val ubntDevicesCount: Int = 0,
    val mikrotikDevicesCount: Int = 0,
    val avgLatencyMs: Double = 0.0,
    val healthPercentage: Int = 100,
    val recentActivity: List<RecentDeviceActivity> = emptyList(),
    val isLoading: Boolean = false
)

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
        val offline = devices.count { it.status == DeviceStatus.OFFLINE }
        val ubnt = devices.count { it.manufacturer == Manufacturer.UBIQUITI }
        val mtk = devices.count { it.manufacturer == Manufacturer.MIKROTIK }

        val onlineDevicesWithLatency = devices.filter { it.status == DeviceStatus.ONLINE && it.metrics.pingLatencyMs > 0 }
        val avgLatency = if (onlineDevicesWithLatency.isNotEmpty()) {
            onlineDevicesWithLatency.map { it.metrics.pingLatencyMs }.average()
        } else {
            0.0
        }

        val health = if (total > 0) {
            ((online.toDouble() / total) * 100).toInt()
        } else {
            100
        }

        DashboardUiState(
            totalDevices = total,
            onlineDevices = online,
            offlineDevices = offline,
            ubntDevicesCount = ubnt,
            mikrotikDevicesCount = mtk,
            avgLatencyMs = avgLatency,
            healthPercentage = health,
            recentActivity = activities,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState(isLoading = true)
    )
}
