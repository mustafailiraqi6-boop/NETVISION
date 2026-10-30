package com.netvision.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netvision.app.models.NetworkDevice
import com.netvision.app.repository.DeviceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MonitoringUiState(
    val monitoredDevice: NetworkDevice? = null,
    val refreshIntervalSec: Int = 5,
    val isAutoPolling: Boolean = true
)

class MonitoringViewModel(
    private val repository: DeviceRepository = DeviceRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(MonitoringUiState())
    val uiState: StateFlow<MonitoringUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllDevices().collect { devices ->
                if (_uiState.value.monitoredDevice == null && devices.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(monitoredDevice = devices.first())
                }
            }
        }
    }

    fun selectDevice(device: NetworkDevice) {
        _uiState.value = _uiState.value.copy(monitoredDevice = device)
    }

    fun setRefreshInterval(seconds: Int) {
        _uiState.value = _uiState.value.copy(refreshIntervalSec = seconds)
    }
}
