package com.netvision.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netvision.app.NetVisionApplication
import com.netvision.app.models.NetworkDevice
import com.netvision.app.models.NetworkRange
import com.netvision.app.network.CidrUtils
import com.netvision.app.network.RemoteNetworkScanner
import com.netvision.app.repository.DeviceRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScannerUiState(
    val isScanning: Boolean = false,
    val progress: Float = 0f,
    val scannedHostsCount: Int = 0,
    val totalHostsCount: Int = 0,
    val currentScannedIp: String = "",
    val activeSubnet: String = "",
    val discoveredDevices: List<NetworkDevice> = emptyList(),
    val addedDeviceIds: Set<String> = emptySet(),
    val errorMessage: String? = null
)

class ScannerViewModel(
    private val repository: DeviceRepository = try {
        NetVisionApplication.instance.deviceRepository
    } catch (e: Exception) {
        DeviceRepository()
    },
    private val scanner: RemoteNetworkScanner = RemoteNetworkScanner()
) : ViewModel() {

    val savedRanges: StateFlow<List<NetworkRange>> = repository.getAllRanges()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val savedDevices: StateFlow<List<NetworkDevice>> = repository.getAllDevices()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        // Track which devices are already saved in Room
        viewModelScope.launch {
            savedDevices.collect { list ->
                val ids = list.map { it.id }.toSet()
                val ips = list.map { it.ipAddress }.toSet()
                _uiState.value = _uiState.value.copy(
                    addedDeviceIds = ids + ips
                )
            }
        }
    }

    fun addRange(cidrInput: String, labelInput: String) {
        val trimmedCidr = cidrInput.trim()
        if (!CidrUtils.isValidCidr(trimmedCidr)) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "نطاق CIDR غير صالح. يرجى إدخال نطاق مثل 192.168.1.0/24 أو 10.197.56.224/27"
            )
            return
        }

        val label = if (labelInput.isNotBlank()) labelInput.trim() else "نطاق $trimmedCidr"
        val id = "range-${System.currentTimeMillis()}"
        viewModelScope.launch {
            repository.saveRange(
                NetworkRange(
                    id = id,
                    cidr = trimmedCidr,
                    label = label,
                    isEnabled = true
                )
            )
            _uiState.value = _uiState.value.copy(errorMessage = null)
        }
    }

    fun deleteRange(rangeId: String) {
        viewModelScope.launch {
            repository.deleteRange(rangeId)
        }
    }

    fun toggleRange(rangeId: String, isEnabled: Boolean) {
        viewModelScope.launch {
            repository.toggleRangeEnabled(rangeId, isEnabled)
        }
    }

    fun startScan(targetCidr: String? = null) {
        if (_uiState.value.isScanning) return

        val rangesToScan = if (!targetCidr.isNullOrBlank()) {
            if (!CidrUtils.isValidCidr(targetCidr)) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "نطاق غير صالح للبدء بالفحص"
                )
                return
            }
            listOf(NetworkRange("custom", targetCidr.trim(), targetCidr.trim(), true))
        } else {
            val enabled = savedRanges.value.filter { it.isEnabled }
            if (enabled.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "يرجى تحديد أو إدخال نطاق CIDR واحد على الأقل للفحص"
                )
                return
            }
            enabled
        }

        _uiState.value = _uiState.value.copy(
            isScanning = true,
            progress = 0f,
            scannedHostsCount = 0,
            totalHostsCount = 0,
            currentScannedIp = "",
            discoveredDevices = emptyList(),
            errorMessage = null
        )

        scanJob = viewModelScope.launch {
            var totalScannedAll = 0
            val allHosts = rangesToScan.flatMap { CidrUtils.getUsableHostIps(it.cidr) }
            val grandTotal = allHosts.size.coerceAtLeast(1)

            _uiState.value = _uiState.value.copy(totalHostsCount = grandTotal)

            for (range in rangesToScan) {
                if (!_uiState.value.isScanning) break

                _uiState.value = _uiState.value.copy(activeSubnet = range.cidr)

                scanner.scanRemoteSubnet(range.cidr).collect { status ->
                    totalScannedAll++
                    val progressRatio = (totalScannedAll.toFloat() / grandTotal).coerceIn(0f, 1f)

                    val currentList = _uiState.value.discoveredDevices.toMutableList()
                    if (status.newlyFoundDevice != null) {
                        // Prevent duplicate display
                        val alreadyDiscovered = currentList.any { it.ipAddress == status.newlyFoundDevice.ipAddress }
                        if (!alreadyDiscovered) {
                            currentList.add(status.newlyFoundDevice)
                        }
                    }

                    _uiState.value = _uiState.value.copy(
                        progress = progressRatio,
                        scannedHostsCount = totalScannedAll,
                        currentScannedIp = status.currentIp,
                        discoveredDevices = currentList
                    )
                }
            }

            _uiState.value = _uiState.value.copy(
                isScanning = false,
                progress = 1f
            )
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        _uiState.value = _uiState.value.copy(isScanning = false)
    }

    fun addDeviceToMonitoring(device: NetworkDevice) {
        viewModelScope.launch {
            repository.saveDevice(device)
            val updated = _uiState.value.addedDeviceIds.toMutableSet()
            updated.add(device.id)
            updated.add(device.ipAddress)
            _uiState.value = _uiState.value.copy(addedDeviceIds = updated)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
