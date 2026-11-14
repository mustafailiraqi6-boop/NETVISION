package com.netvision.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.netvision.app.NetVisionApplication
import com.netvision.app.database.DeviceFolderEntity
import com.netvision.app.models.DeviceFolder
import com.netvision.app.models.DeviceStatus
import com.netvision.app.models.NetworkDevice
import com.netvision.app.models.DeviceType
import com.netvision.app.monitoring.SnmpDeviceInfo
import com.netvision.app.monitoring.MimosaTrafficSnapshot
import com.netvision.app.monitoring.SnmpDiagnosticRecord
import com.netvision.app.monitoring.SnmpV2Client
import com.netvision.app.monitoring.EthernetPortMonitor
import com.netvision.app.monitoring.EthernetMonitoringSnapshot
import com.netvision.app.monitoring.EthernetPortSnapshot
import com.netvision.app.network.NetworkReachabilityService
import com.netvision.app.network.RouterOsApiClient
import com.netvision.app.network.WirelessTrafficSnapshot
import com.netvision.app.models.Manufacturer
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class MonitoringUiState(
    val selectedDeviceId: String? = null,
    val selectedFolderId: String? = null,
    val searchQuery: String = "",
    val isAutoPolling: Boolean = false,
    val refreshIntervalSec: Int = 10,
    val lastRefreshedTime: Long = 0L,
    val lastRefreshedByFolder: Map<String, Long> = emptyMap(),
    val isPollingNow: Boolean = false,
    val isNetworkUnavailable: Boolean = false,
    val snmpStatusByDevice: Map<String, SnmpDeviceInfo> = emptyMap(),
    val diagnosticLogs: List<SnmpDiagnosticRecord> = emptyList(),
    val ethernetPortsByDevice: Map<String, EthernetMonitoringSnapshot> = emptyMap(),
    val wirelessTrafficByDevice: Map<String, WirelessTrafficSnapshot> = emptyMap(),
    val mimosaTrafficByDevice: Map<String, MimosaTrafficSnapshot> = emptyMap()
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

    private val deviceCredentialStore = NetVisionApplication.instance.deviceCredentialStore
    private val routerOsApiClient = RouterOsApiClient(NetVisionApplication.instance)

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

    private val monitoringPreferences = NetVisionApplication.instance.getSharedPreferences("netvision_monitoring", 0)
    private val _uiState = MutableStateFlow(MonitoringUiState(selectedFolderId = monitoringPreferences.getString("last_selected_folder_id", null)))
    val uiState: StateFlow<MonitoringUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var groupRefreshJob: Job? = null
    private var centralPingJob: Job? = null
    private var wirelessTrafficJob: Job? = null
    private var mimosaTrafficJob: Job? = null
    private val pollSemaphore = Semaphore(10)
    private val snmpMapMutex = Mutex()
    private val previousPortCounters = mutableMapOf<String, Pair<Long, Long>>()
    // Require three consecutive failed reachability checks before declaring an online device offline.
    private val consecutiveFailures = ConcurrentHashMap<String, AtomicInteger>()
    private val offlineFailureThreshold = 3

    init {
        startCentralPingMonitoring()
        startWirelessTrafficMonitoring()
        startMimosaTrafficMonitoring()
    }

    fun selectDevice(deviceId: String) {
        _uiState.value = _uiState.value.copy(selectedDeviceId = deviceId)
    }

    fun selectFolder(folderId: String?) {
        _uiState.value = _uiState.value.copy(selectedFolderId = folderId)
        monitoringPreferences.edit().putString("last_selected_folder_id", folderId).apply()
    }

    fun refreshFolderIfStale(folderId: String) {
        val last = _uiState.value.lastRefreshedByFolder[folderId] ?: 0L
        if (System.currentTimeMillis() - last >= 60_000L) refreshFolderNow(folderId)
    }

    fun refreshFolderNow(folderId: String) {
        groupRefreshJob?.cancel()
        _uiState.value = _uiState.value.copy(selectedFolderId = folderId, isPollingNow = true)
        groupRefreshJob = viewModelScope.launch { pollAllDevices(folderId) }
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
        _uiState.value.selectedFolderId?.let(::refreshFolderNow)
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

    /** One app-wide ICMP monitor shared by every screen. */
    private fun startCentralPingMonitoring() {
        if (centralPingJob?.isActive == true) return
        centralPingJob = viewModelScope.launch {
            delay(1500L)
            while (isActive) {
                val vpnAvailable = reachabilityService.isVpnConnected()
                _uiState.value = _uiState.value.copy(isNetworkUnavailable = !vpnAvailable)
                if (vpnAvailable) {
                    val devices = repository.getAllDevices().first()
                    coroutineScope {
                        devices.map { device ->
                            async(Dispatchers.IO) {
                                pollSemaphore.withPermit {
                                    val result = reachabilityService.checkHostReachability(device.ipAddress, timeoutMs = 2000)
                                    if (result.failureReason == "VPN is not connected") return@withPermit
                                    if (result.isReachable) {
                                        consecutiveFailures.remove(device.id)
                                        repository.updateDeviceStatus(device.id, true, result.latencyMs.toInt().coerceAtLeast(0))
                                    } else {
                                        val failures = consecutiveFailures.computeIfAbsent(device.id) { AtomicInteger(0) }.incrementAndGet()
                                        if (failures >= offlineFailureThreshold) repository.updateDeviceStatus(device.id, false, 0)
                                    }
                                }
                            }
                        }.awaitAll()
                    }
                }
                delay(10_000L)
            }
        }
    }

    /** Poll only the selected tower's MikroTik wireless links/sectors for live WLAN traffic. */
    private fun startWirelessTrafficMonitoring() {
        if (wirelessTrafficJob?.isActive == true) return
        wirelessTrafficJob = viewModelScope.launch {
            while (isActive) {
                val folderId = _uiState.value.selectedFolderId
                if (folderId != null) {
                    val devices = savedDevices.value.filter { device ->
                        device.manufacturer == Manufacturer.MIKROTIK &&
                            device.deviceType in setOf(DeviceType.LINK, DeviceType.SECTOR_AP, DeviceType.ACCESS_POINT) &&
                            (if (folderId == DeviceFolderEntity.DEFAULT_FOLDER_ID) {
                                device.folderId.isBlank() || device.folderId == folderId
                            } else {
                                device.folderId == folderId
                            })
                    }
                    val settings = settingsRepository.getSettings()
                    coroutineScope {
                        devices.map { device ->
                            async(Dispatchers.IO) {
                                val credentials = deviceCredentialStore.get(device.id) ?: deviceCredentialStore.getDefault()
                                val snapshot = credentials?.let {
                                    routerOsApiClient.readWirelessTraffic(
                                        ipAddress = device.ipAddress,
                                        username = it.username,
                                        password = it.password,
                                        port = device.apiPort ?: settings.defaultMikroTikApiPort
                                    ).getOrNull()
                                }
                                snmpMapMutex.withLock {
                                    val updated = _uiState.value.wirelessTrafficByDevice.toMutableMap()
                                    if (snapshot != null) updated[device.id] = snapshot else updated.remove(device.id)
                                    _uiState.value = _uiState.value.copy(wirelessTrafficByDevice = updated)
                                }
                            }
                        }.awaitAll()
                    }
                }
                delay(2000L)
            }
        }
    }
    /** Poll live Mimosa wireless traffic for devices in the currently selected tower. */
    private fun startMimosaTrafficMonitoring() {
        if (mimosaTrafficJob?.isActive == true) return
        mimosaTrafficJob = viewModelScope.launch {
            while (isActive) {
                val folderId = _uiState.value.selectedFolderId
                if (folderId != null) {
                    val devices = savedDevices.value.filter { device ->
                        device.manufacturer == Manufacturer.MIMOSA &&
                            device.deviceType in setOf(DeviceType.LINK, DeviceType.SECTOR_AP, DeviceType.ACCESS_POINT) &&
                            (if (folderId == DeviceFolderEntity.DEFAULT_FOLDER_ID) {
                                device.folderId.isBlank() || device.folderId == folderId
                            } else {
                                device.folderId == folderId
                            })
                    }
                    val settings = settingsRepository.getSettings()
                    coroutineScope {
                        devices.map { device ->
                            async(Dispatchers.IO) {
                                var traffic: MimosaTrafficSnapshot? = null
                                val communities = settingsRepository.getCommunitiesForDevice(device.ipAddress)
                                for (community in communities) {
                                    val result = SnmpV2Client.queryMimosaTraffic(
                                        ip = device.ipAddress,
                                        community = community,
                                        port = settings.snmpPort,
                                        version = device.snmpVersion ?: settings.snmpVersion,
                                        timeoutMs = 900
                                    )
                                    if (result.isSuccess) {
                                        traffic = result.getOrNull()
                                        break
                                    }
                                }
                                snmpMapMutex.withLock {
                                    val updated = _uiState.value.mimosaTrafficByDevice.toMutableMap()
                                    if (traffic != null) updated[device.id] = traffic!! else updated.remove(device.id)
                                    _uiState.value = _uiState.value.copy(mimosaTrafficByDevice = updated)
                                }
                            }
                        }.awaitAll()
                    }
                }
                delay(2000L)
            }
        }
    }

    private suspend fun pollAllDevices(folderId: String? = null) = withContext(Dispatchers.IO) {
        // Wait for Room to emit the saved devices instead of missing the first
        // polling cycle while StateFlow still contains its initial empty list.
        val allDevices = savedDevices.value
        val currentDevices = if (folderId == null) allDevices else allDevices.filter { device ->
            if (folderId == DeviceFolderEntity.DEFAULT_FOLDER_ID) device.folderId.isBlank() || device.folderId == folderId
            else device.folderId == folderId
        }
        if (currentDevices.isEmpty()) {
            if (_uiState.value.selectedFolderId == folderId) _uiState.value = _uiState.value.copy(isPollingNow = false)
            return@withContext
        }

        try {
            val settings = settingsRepository.getSettings()
            val snmpPort = settings.snmpPort

            coroutineScope {
                currentDevices.map { device ->
                    async(Dispatchers.IO) {
                        pollSemaphore.withPermit {
                            // Reachability is handled by the shared ICMP monitor; this pass only refreshes SNMP telemetry.
                            // SNMP must never determine reachability. It runs after
                            // status persistence and cannot delay the visible status.
                            try {
                                val snmpRes = settingsRepository.queryDeviceInfo(
                                    ip = device.ipAddress,
                                    port = snmpPort,
                                    preferredVersion = device.snmpVersion,
                                    timeoutMs = 1000
                                )
                                val baseInfo = snmpRes.getOrNull()
                                var info = baseInfo
                                // Keep the existing Switches classification rules exactly as they are.
                                val isSwitchDevice = device.deviceType == DeviceType.SWITCH ||
                                    device.name.contains("switch", ignoreCase = true) ||
                                    device.model.contains("CRS", ignoreCase = true) ||
                                    device.model.contains("switch", ignoreCase = true)
                                val isMikroTikSwitch = isSwitchDevice && (
                                    device.manufacturer == com.netvision.app.models.Manufacturer.MIKROTIK ||
                                        device.model.contains("CRS", ignoreCase = true) ||
                                        device.model.contains("MikroTik", ignoreCase = true) ||
                                        baseInfo?.sysDescr?.contains("MikroTik", ignoreCase = true) == true
                                    )
                                var apiSwitchPortsLoaded = false
                                if (isMikroTikSwitch) {
                                    val credentials = deviceCredentialStore.get(device.id) ?: deviceCredentialStore.getDefault()
                                    if (credentials != null) {
                                        val apiSnapshot = routerOsApiClient.readEthernetSwitchPorts(
                                            ipAddress = device.ipAddress,
                                            username = credentials.username,
                                            password = credentials.password,
                                            port = device.apiPort ?: settings.defaultMikroTikApiPort
                                        ).getOrNull()
                                        if (apiSnapshot != null) {
                                            // API supplies cumulative byte counters in one response.
                                            // Calculate live rates from the difference between consecutive polls.
                                            val withRates = applyPortRates(device.id, apiSnapshot)
                                            snmpMapMutex.withLock {
                                                val updated = _uiState.value.ethernetPortsByDevice.toMutableMap()
                                                updated[device.id] = withRates
                                                _uiState.value = _uiState.value.copy(ethernetPortsByDevice = updated)
                                            }
                                            apiSwitchPortsLoaded = true
                                        }
                                    }
                                }
                                // MikroTik wireless sectors/links use RouterOS API for station details.
                                // This is deliberately separate from the Switches API path above.
                                val isMikroTikWirelessDevice =
                                    device.manufacturer == com.netvision.app.models.Manufacturer.MIKROTIK &&
                                        !isSwitchDevice &&
                                        device.deviceType in setOf(DeviceType.LINK, DeviceType.SECTOR_AP, DeviceType.ACCESS_POINT)
                                if (isMikroTikWirelessDevice) {
                                    val credentials = deviceCredentialStore.get(device.id)
                                        ?: deviceCredentialStore.getDefault()
                                    if (credentials != null) {
                                        val apiStations = routerOsApiClient.readWirelessRegistrationTable(
                                            ipAddress = device.ipAddress,
                                            username = credentials.username,
                                            password = credentials.password,
                                            port = device.apiPort ?: settings.defaultMikroTikApiPort
                                        ).getOrNull()
                                        if (apiStations != null) {
                                            val currentInfo = info
                                            info = if (currentInfo != null) {
                                                currentInfo.copy(
                                                    connectedMikrotikStations = apiStations,
                                                    connectedStationsMacs = if (apiStations.isNotEmpty()) {
                                                        apiStations.map { it.macAddress }
                                                    } else currentInfo.connectedStationsMacs
                                                )
                                            } else {
                                                SnmpDeviceInfo(
                                                    isSuccess = false,
                                                    errorMessage = "SNMP unavailable; station details loaded through RouterOS API",
                                                    connectedMikrotikStations = apiStations,
                                                    connectedStationsMacs = apiStations.map { it.macAddress }
                                                )
                                            }
                                        }
                                    }
                                }

                                if (info != null) {
                                    val info = info
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
                                            device.manufacturer == com.netvision.app.models.Manufacturer.MIKROTIK || info.mikrotikCcqPercent != null || info.mikrotikVoltageDecivolts != null ->
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
                                        // Prefer the device's configured SNMP sysName.
                                        // Only generated Host/IP labels are replaced; manual labels stay intact.
                                        info.sysName
                                            ?.trim()
                                            ?.takeIf { it.isNotBlank() && !it.equals(device.ipAddress, ignoreCase = true) }
                                            ?.let { repository.updateDeviceSnmpName(device.id, it) }
                                        repository.updateDeviceTelemetry(
                                            deviceId = device.id,
                                            uptime = info.uptimeFormatted,
                                            snmpVersion = info.snmpVersion
                                        )

                                        // Read physical Ethernet/SFP interfaces for MikroTik and Ubiquiti.
                                        // The collector follows the negotiated SNMP version (v1 or v2c)
                                        // and binds each UDP request to the active VPN network.
                                        if (
                                            !(isMikroTikSwitch && apiSwitchPortsLoaded) && (
                                            device.manufacturer == com.netvision.app.models.Manufacturer.MIKROTIK ||
                                            manufacturer == com.netvision.app.models.Manufacturer.MIKROTIK ||
                                            device.manufacturer == com.netvision.app.models.Manufacturer.UBIQUITI ||
                                            manufacturer == com.netvision.app.models.Manufacturer.UBIQUITI ||
                                            info.model?.contains("MikroTik", ignoreCase = true) == true ||
                                            info.sysDescr?.contains("MikroTik", ignoreCase = true) == true ||
                                            info.model?.contains("Ubiquiti", ignoreCase = true) == true ||
                                            info.sysDescr?.let { it.contains("AirOS", true) || it.contains("UBNT", true) || it.contains("Ubiquiti", true) } == true ||
                                            device.model.contains("CRS", ignoreCase = true) ||
                                            device.model.contains("RouterBOARD", ignoreCase = true)
                                            )
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

            val refreshedAt = System.currentTimeMillis()
            val updatedTimes = _uiState.value.lastRefreshedByFolder.toMutableMap()
            val refreshedFolderIds = if (folderId == null) allDevices.map { it.folderId.ifBlank { DeviceFolderEntity.DEFAULT_FOLDER_ID } }.toSet() else setOf(folderId)
            refreshedFolderIds.forEach { updatedTimes[it] = refreshedAt }
            _uiState.value = _uiState.value.copy(
                lastRefreshedTime = refreshedAt,
                lastRefreshedByFolder = updatedTimes,
                diagnosticLogs = SnmpV2Client.getDiagnosticHistory().reversed()
            )
        } finally {
            if (_uiState.value.selectedFolderId == folderId) _uiState.value = _uiState.value.copy(isPollingNow = false)
        }
    }

    /**
     * Converts RouterOS cumulative byte counters into Mbps using the elapsed time
     * between successful API snapshots. The first sample remains unavailable.
     */
    private fun applyPortRates(
        deviceId: String,
        snapshot: EthernetMonitoringSnapshot
    ): EthernetMonitoringSnapshot {
        val now = System.currentTimeMillis()
        val updatedPorts = snapshot.ports.map { port ->
            val rxKey = "$deviceId:${port.index}:rx"
            val txKey = "$deviceId:${port.index}:tx"
            val previousRx = previousPortCounters[rxKey]
            val previousTx = previousPortCounters[txKey]

            val rxElapsed = previousRx?.let { ((now - it.first).coerceAtLeast(1L)) / 1000.0 }
            val txElapsed = previousTx?.let { ((now - it.first).coerceAtLeast(1L)) / 1000.0 }

            val rxRate = if (previousRx != null && rxElapsed != null &&
                port.rxOctets != null && port.rxOctets >= previousRx.second
            ) {
                (port.rxOctets - previousRx.second) * 8.0 / rxElapsed / 1_000_000.0
            } else null
            val txRate = if (previousTx != null && txElapsed != null &&
                port.txOctets != null && port.txOctets >= previousTx.second
            ) {
                (port.txOctets - previousTx.second) * 8.0 / txElapsed / 1_000_000.0
            } else null

            port.rxOctets?.let { previousPortCounters[rxKey] = now to it }
            port.txOctets?.let { previousPortCounters[txKey] = now to it }
            port.copy(rxMbps = rxRate, txMbps = txRate)
        }
        return snapshot.copy(ports = updatedPorts)
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
        groupRefreshJob?.cancel()
        centralPingJob?.cancel()
        wirelessTrafficJob?.cancel()
        mimosaTrafficJob?.cancel()
    }
}
