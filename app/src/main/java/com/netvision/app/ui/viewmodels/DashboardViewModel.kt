package com.netvision.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netvision.app.models.DeviceStatus
import com.netvision.app.models.Manufacturer
import com.netvision.app.models.NetworkDevice
import com.netvision.app.models.RecentDeviceActivity
import com.netvision.app.repository.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class DashboardUiState(
    val totalDevices: Int = 0,
    val onlineDevices: Int = 0,
    val offlineDevices: Int = 0,
    val ubntDevicesCount: Int = 0,
    val mikrotikDevicesCount: Int = 0,
    val recentActivity: List<RecentDeviceActivity> = emptyList(),
    val isLoading: Boolean = false
)

class DashboardViewModel(
    private val repository: DeviceRepository = DeviceRepository()
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = repository.getAllDevices()
        .map { devices ->
            DashboardUiState(
                totalDevices = devices.size,
                onlineDevices = devices.count { it.status == DeviceStatus.ONLINE },
                offlineDevices = devices.count { it.status == DeviceStatus.OFFLINE },
                ubntDevicesCount = devices.count { it.manufacturer == Manufacturer.UBIQUITI },
                mikrotikDevicesCount = devices.count { it.manufacturer == Manufacturer.MIKROTIK },
                recentActivity = repository.getSampleActivity(),
                isLoading = false
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DashboardUiState(isLoading = true)
        )
}
