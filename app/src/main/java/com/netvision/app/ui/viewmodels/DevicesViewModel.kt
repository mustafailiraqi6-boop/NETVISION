package com.netvision.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netvision.app.NetVisionApplication
import com.netvision.app.models.DeviceFolder
import com.netvision.app.models.DeviceStatus
import com.netvision.app.models.DeviceType
import com.netvision.app.models.Manufacturer
import com.netvision.app.models.NetworkDevice
import com.netvision.app.repository.DeviceRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DevicesUiState(
    val devices: List<NetworkDevice> = emptyList(),
    val totalCount: Int = 0,
    val folders: List<DeviceFolder> = emptyList(),
    val selectedFolderId: String? = null,
    val searchQuery: String = "",
    val selectedManufacturer: Manufacturer? = null,
    val selectedStatus: DeviceStatus? = null,
    val sortBy: String = "name"
)

class DevicesViewModel(
    private val repository: DeviceRepository = try {
        NetVisionApplication.instance.deviceRepository
    } catch (e: Exception) {
        DeviceRepository()
    }
) : ViewModel() {

    private val credentialStore = NetVisionApplication.instance.deviceCredentialStore

    private val _searchQuery = MutableStateFlow("")
    private val _selectedManufacturer = MutableStateFlow<Manufacturer?>(null)
    private val _selectedStatus = MutableStateFlow<DeviceStatus?>(null)
    private val _sortBy = MutableStateFlow("name")
    private val _selectedFolderId = MutableStateFlow<String?>(null)

    private data class FilterParams(
        val query: String,
        val vendor: Manufacturer?,
        val status: DeviceStatus?,
        val folderId: String?,
        val sort: String
    )

    private val filterParamsFlow = combine(
        _searchQuery,
        _selectedManufacturer,
        _selectedStatus,
        _selectedFolderId,
        _sortBy
    ) { query, vendor, status, folderId, sort ->
        FilterParams(query, vendor, status, folderId, sort)
    }

    val uiState: StateFlow<DevicesUiState> = combine(
        repository.getAllDevices(),
        repository.getAllFolders(),
        filterParamsFlow
    ) { devices, folders, filter ->
        val (query, vendor, status, folderId, sort) = filter
        val filtered = devices.filter { dev ->
            val matchesFolder = folderId == null || dev.folderId == folderId
            val matchesQuery = query.isEmpty() ||
                    dev.name.contains(query, ignoreCase = true) ||
                    dev.ipAddress.contains(query) ||
                    dev.macAddress.contains(query, ignoreCase = true) ||
                    dev.model.contains(query, ignoreCase = true)
            val matchesVendor = vendor == null || dev.manufacturer == vendor
            val matchesStatus = status == null || dev.status == status
            matchesFolder && matchesQuery && matchesVendor && matchesStatus
        }.sortedWith { a, b ->
            when (sort) {
                "ip" -> a.ipAddress.compareTo(b.ipAddress)
                "latency" -> a.metrics.pingLatencyMs.compareTo(b.metrics.pingLatencyMs)
                else -> a.name.compareTo(b.name, ignoreCase = true)
            }
        }

        DevicesUiState(
            devices = filtered,
            totalCount = devices.size,
            folders = folders,
            selectedFolderId = folderId,
            searchQuery = query,
            selectedManufacturer = vendor,
            selectedStatus = status,
            sortBy = sort
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DevicesUiState()
    )

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun onManufacturerSelected(manufacturer: Manufacturer?) {
        _selectedManufacturer.value = manufacturer
    }

    fun onStatusSelected(status: DeviceStatus?) {
        _selectedStatus.value = status
    }

    fun onSortChange(sort: String) {
        _sortBy.value = sort
    }

    fun selectFolder(folderId: String?) {
        _selectedFolderId.value = folderId
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                val newId = repository.createFolder(name.trim())
                _selectedFolderId.value = newId
            }
        }
    }

    fun updateFolderName(folderId: String, newName: String) {
        viewModelScope.launch {
            if (newName.isNotBlank()) {
                repository.updateFolderName(folderId, newName.trim())
            }
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            repository.deleteFolder(folderId)
            if (_selectedFolderId.value == folderId) {
                _selectedFolderId.value = null
            }
        }
    }

    fun addDevice(
        name: String,
        ipAddress: String,
        manufacturer: Manufacturer,
        deviceType: DeviceType,
        folderId: String,
        apiPort: Int? = null,
        apiUsername: String = "",
        apiPassword: String = ""
    ) {
        viewModelScope.launch {
            val saved = repository.addManualDevice(name, ipAddress, manufacturer, deviceType, folderId, apiPort)
            if (manufacturer == Manufacturer.MIKROTIK && apiUsername.isNotBlank() && apiPassword.isNotBlank()) {
                withContext(Dispatchers.IO) { credentialStore.save(saved.id, apiUsername.trim(), apiPassword) }
            }
        }
    }

    fun updateDeviceFull(
        deviceId: String,
        name: String,
        ipAddress: String,
        manufacturer: Manufacturer,
        deviceType: DeviceType,
        folderId: String,
        apiPort: Int? = null,
        apiUsername: String = "",
        apiPassword: String = ""
    ) {
        viewModelScope.launch {
            repository.updateDeviceFull(deviceId, name, ipAddress, manufacturer, deviceType, folderId, apiPort)
            if (manufacturer == Manufacturer.MIKROTIK) {
                if (apiUsername.isNotBlank() && apiPassword.isNotBlank()) {
                    withContext(Dispatchers.IO) { credentialStore.save(deviceId, apiUsername.trim(), apiPassword) }
                }
            } else {
                withContext(Dispatchers.IO) { credentialStore.delete(deviceId) }
            }
        }
    }

    fun deleteDevice(device: NetworkDevice) {
        viewModelScope.launch {
            repository.deleteDevice(device)
            withContext(Dispatchers.IO) { credentialStore.delete(device.id) }
        }
    }
}
