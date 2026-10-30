package com.netvision.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netvision.app.models.DeviceStatus
import com.netvision.app.models.Manufacturer
import com.netvision.app.models.NetworkDevice
import com.netvision.app.repository.DeviceRepository
import kotlinx.coroutines.flow.*

data class DevicesUiState(
    val devices: List<NetworkDevice> = emptyList(),
    val searchQuery: String = "",
    val selectedManufacturer: Manufacturer? = null,
    val selectedStatus: DeviceStatus? = null,
    val sortBy: String = "name"
)

class DevicesViewModel(
    private val repository: DeviceRepository = DeviceRepository()
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedManufacturer = MutableStateFlow<Manufacturer?>(null)
    private val _selectedStatus = MutableStateFlow<DeviceStatus?>(null)
    private val _sortBy = MutableStateFlow("name")

    val uiState: StateFlow<DevicesUiState> = combine(
        repository.getAllDevices(),
        _searchQuery,
        _selectedManufacturer,
        _selectedStatus,
        _sortBy
    ) { devices, query, vendor, status, sort ->
        val filtered = devices.filter { dev ->
            val matchesQuery = query.isEmpty() ||
                    dev.name.contains(query, ignoreCase = true) ||
                    dev.ipAddress.contains(query) ||
                    dev.model.contains(query, ignoreCase = true)
            val matchesVendor = vendor == null || dev.manufacturer == vendor
            val matchesStatus = status == null || dev.status == status
            matchesQuery && matchesVendor && matchesStatus
        }.sortedWith { a, b ->
            when (sort) {
                "ip" -> a.ipAddress.compareTo(b.ipAddress)
                "latency" -> a.metrics.pingLatencyMs.compareTo(b.metrics.pingLatencyMs)
                else -> a.name.compareTo(b.name, ignoreCase = true)
            }
        }

        DevicesUiState(
            devices = filtered,
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
}
