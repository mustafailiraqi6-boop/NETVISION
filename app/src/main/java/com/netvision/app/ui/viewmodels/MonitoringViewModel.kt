package com.netvision.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netvision.app.NetVisionApplication
import com.netvision.app.models.DeviceFolder
import com.netvision.app.models.DeviceStatus
import com.netvision.app.models.NetworkDevice
import com.netvision.app.monitoring.SnmpDeviceInfo
import com.netvision.app.monitoring.SnmpDiagnosticRecord
import com.netvision.app.monitoring.SnmpV2Client
import com.netvision.app.monitoring.EthernetPortMonitor
import com.netvision.app.monitoring.EthernetMonitoringSnapshot
import com.netvision.app.monitoring.EthernetPortSnapshot
import com.netvision.app.network.NetworkReachabilityService
import com.netvision.app.repository.DeviceRepository
import com.netvision.app.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MonitoringUiState(
    val selectedDeviceId: String? = null,
    val selectedFolderId: String? = null,
    val searchQuery: String = "",
    val isAutoPolling: Boolean = true,
    val refreshIntervalSec: Int = 10,
    val lastRefreshedTime: Long = 0L,
    val isPollingNow: Boolean = false,
    val snmpStatusByDevice: Map<String, SnmpDeviceInfo> = emptyMap(),
    val diagnosticLogs: List<SnmpDiagnosticRecord> = emptyList(),
    val ethernetPortsByDevice: Map<String, EthernetMonitoringSnapshot> = emptyMap()
)

class MonitoringViewModel(
    private val repository: DeviceRepository = try {
        NetVisionApplication.instance.deviceRepository
    } catch (e: Exception) {
        DeviceRepository()
    },
    private val settingsRepository: SettingsRepository = try {
        NetVisionApplication.instance.settingsRepository
    } catch (e: Exception) {
        SettingsRepository(NetVisionApplication.instance)
    },
    private val reachabilityService: NetworkReachabilityService = NetworkReachabilityService(NetVisionApplication.instance)
) : ViewModel() {

    val savedDevices: StateFlow<List<NetworkDevice>> = repository.getAllDevices()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val savedFolders: StateFlow<List<DeviceFolder>> = repository.getAllFolders()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _uiState = MutableStateFlow(MonitoringUiState())
    val uiState: StateFlow<MonitoringUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private val pollSemaphore = Semaphore(10)
    private val snmpMapMutex = Mutex()
    private val previousPortCounters = mutableMapOf<String, Pair<Long, Long>>()

    init {
        startPeriodicPolling()
        viewModelScope.launch {
            NetVisionApplication.foregroundEvents.collect {
                refreshNow()
            }
        }
    }

    fun selectDevice(deviceId: String) {
        _uiState.value = _uiState.value.copy(selectedDeviceId = deviceId)
    }

    fun selectFolder(folderId: String?) {
        _uiState.value = _uiState.value.copy(selectedFolderId = folderId)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
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

    private suspend fun pollAllDevices() = withContext(Dispatchers.IO) {
        // Wait for Room to emit the saved devices instead of missing the first
        // polling cycle while StateFlow still contains its initial empty list.
        val currentDevices = savedDevices.value.ifEmpty {
            savedDevices.first { it.isNotEmpty() }
        }
        if (currentDevices.isEmpty()) return@withContext

        if (_uiState.value.isPollingNow) return@withContext
        _uiState.value = _uiState.value.copy(isPollingNow = true)

        try {
            val settings = settingsRepository.getSettings()
            val snmpPort = settings.snmpPort

            // Reachability checks run concurrently (bounded to 10 devices).
            // Each device's online/offline state is persisted immediately after
            // its reachability result; SNMP is deliberately independent.
            coroutineScope {
                currentDevices.map { device ->
                    async(Dispatchers.IO) {
                        pollSemaphore.withPermit {
                            val reachability = reachabilityService.checkHostReachability(
                                targetIp = device.ipAddress,
                                timeoutMs = 500
                            )
                            val isOnline = reachability.isReachable
                            val latencyMs = reachability.latencyMs.toInt().coerceAtLeast(0)

                            repository.updateDeviceStatus(
                                deviceId = device.id,
                                isOnline = isOnline,
                                latencyMs = latencyMs,
                                uptime = null,
                                snmpVersion = device.snmpVersion
                            )

                            // SNMP must never determine reachability. It runs after
                            // status persistence and cannot delay the visible status.
                            try {
                                val snmpRes = settingsRepository.queryDeviceInfo(
                                    ip = device.ipAddress,
                                    port = snmpPort,
                                    preferredVersion = device.snmpVersion,
                                    timeoutMs = 1000
                                )
                                val info = snmpRes.getOrNull()
                                if (snmpRes.isSuccess && info != null) {
                                    snmpMapMutex.withLock {
                                        val updated = _uiState.value.snmpStatusByDevice.toMutableMap()
                                        updated[device.id] = info
                                        _uiState.value = _uiState.value.copy(snmpStatusByDevice = updated)
                                    }
                                    if (info.isSuccess) {
                                        val manufacturer = when {
                                            info.ubntApMac != null ||
                                                info.ubntNoiseFloorDbm != null ||
                                                info.ubntTransmitCcqPercent != null ||
                                                info.ubntAirMaxQualityPercent != null ||
                                                info.ubntAirMaxCapacityPercent != null ->
                                                com.netvision.app.models.Manufacturer.UBIQUITI
                                            info.mikrotikCcqPercent != null || info.mikrotikVoltageDecivolts != null ->
                                                com.netvision.app.models.Manufacturer.MIKROTIK
                                            info.sysDescr?.contains("Mimosa", ignoreCase = true) == true ||
                                                info.model?.contains("Mimosa", ignoreCase = true) == true ||
                                                info.mimosaLinkUptimeTicks != null ||
                                                info.mimosaSignalChain1Dbm != null ->
                                                com.netvision.app.models.Manufacturer.MIMOSA
                                            else -> null
                                        }
                                        if (manufacturer != null) {
                                            repository.updateDeviceManufacturer(device.id, manufacturer)
                                        }
                                        repository.updateDeviceStatus(
                                            deviceId = device.id,
                                            isOnline = isOnline,
                                            latencyMs = latencyMs,
                                            uptime = info.uptimeFormatted,
                                            snmpVersion = info.snmpVersion
                                        )

                                        // Read physical Ethernet/SFP interfaces for MikroTik devices.
                                        // The collector uses SnmpV2Client, which binds each UDP socket to VPN.
                                        if (
                                            device.manufacturer == com.netvision.app.models.Manufacturer.MIKROTIK ||
                                            manufacturer == com.netvision.app.models.Manufacturer.MIKROTIK ||
                                            info.model?.contains("MikroTik", ignoreCase = true) == true ||
                                            info.sysDescr?.contains("MikroTik", ignoreCase = true) == true ||
                                            device.model.contains("CRS", ignoreCase = true) ||
                                            device.model.contains("RouterBOARD", ignoreCase = true)
                                        ) {
                                            try {
                                                val snapshot = EthernetPortMonitor.read(
                                                    ip = device.ipAddress,
                                                    community = info.communityUsed,
                                                    port = snmpPort,
                                                    version = info.snmpVersion,
                                                    timeoutMs = 900
                                                )
                                                val now = System.currentTimeMillis()
                                                val withRates = snapshot.copy(ports = snapshot.ports.map { portInfo ->
                                                    val key = "${device.id}:${portInfo.index}"
                                                    val previous = previousPortCounters[key]
                                                    val elapsed = if (previous != null) (now - previous.first).coerceAtLeast(1L) / 1000.0 else 0.0
                                                    previousPortCounters[key] = now to (portInfo.rxOctets ?: 0L)
                                                    val previousTxKey = "${key}:tx"
                                                    val previousTx = previousPortCounters[previousTxKey]
                                                    previousPortCounters[previousTxKey] = now to (portInfo.txOctets ?: 0L)
                                                    val rx = if (previous != null && elapsed > 0 && portInfo.rxOctets != null && portInfo.rxOctets >= previous.second) ((portInfo.rxOctets - previous.second) * 8.0 / elapsed / 1_000_000.0) else null
                                                    val tx = if (previousTx != null && elapsed > 0 && portInfo.txOctets != null && portInfo.txOctets >= previousTx.second) ((portInfo.txOctets - previousTx.second) * 8.0 / elapsed / 1_000_000.0) else null
                                                    portInfo.copy(rxMbps = rx, txMbps = tx)
                                                })
                                                snmpMapMutex.withLock {
                                                    val updated = _uiState.value.ethernetPortsByDevice.toMutableMap()
                                                    updated[device.id] = withRates
                                                    _uiState.value = _uiState.value.copy(ethernetPortsByDevice = updated)
                                                }
                                            } catch (_: Exception) {
                                                // Keep the last successful interface snapshot on a transient SNMP failure.
                                            }
                                        }
                                    }
                                }
                            } catch (_: Exception) {
                                // SNMP failure does not change the online/offline result.
                            }
                        }
                    }
                }.awaitAll()
            }

            _uiState.value = _uiState.value.copy(
                lastRefreshedTime = System.currentTimeMillis(),
                diagnosticLogs = SnmpV2Client.getDiagnosticHistory().reversed()
            )
        } finally {
            _uiState.value = _uiState.value.copy(isPollingNow = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
    }
}
