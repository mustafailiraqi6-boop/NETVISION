package com.netvision.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netvision.app.models.NetworkDevice
import com.netvision.app.models.NetworkRange
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScannerUiState(
    val isScanning: Boolean = false,
    val progress: Float = 0f,
    val scannedHostsCount: Int = 0,
    val currentScannedIp: String = "",
    val activeRanges: List<NetworkRange> = emptyList(),
    val discoveredDevices: List<NetworkDevice> = emptyList()
)

class ScannerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(
        ScannerUiState(
            activeRanges = listOf(
                NetworkRange("r1", "192.168.1.0/24", "LAN Main", true),
                NetworkRange("r2", "10.0.0.0/24", "Management VLAN", true)
            )
        )
    )
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    fun startScan() {
        if (_uiState.value.isScanning) return
        _uiState.value = _uiState.value.copy(
            isScanning = true,
            progress = 0f,
            scannedHostsCount = 0,
            discoveredDevices = emptyList()
        )

        scanJob = viewModelScope.launch {
            for (step in 1..100) {
                delay(60)
                _uiState.value = _uiState.value.copy(
                    progress = step / 100f,
                    scannedHostsCount = (step * 2.54).toInt(),
                    currentScannedIp = "192.168.1.${(step * 2.54).toInt()}"
                )
            }
            _uiState.value = _uiState.value.copy(isScanning = false)
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        _uiState.value = _uiState.value.copy(isScanning = false)
    }
}
