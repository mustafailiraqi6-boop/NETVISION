package com.netvision.app.monitoring

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.netvision.app.NetVisionApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.Locale

data class SnmpDeviceInfo(
    val isSuccess: Boolean,
    val sysDescr: String? = null,
    val sysName: String? = null,
    val uptimeHundredths: Long? = null,
    val uptimeFormatted: String? = null,
    val model: String? = null,
    val ifCount: Int? = null,
    val connectedClientsCount: Int? = null,
    val connectedStationsMacs: List<String> = emptyList(),
    val connectedStations: List<UbiquitiStationInfo> = emptyList(),
    val connectedMikrotikStations: List<MikrotikStationInfo> = emptyList(),
    val errorMessage: String? = null,
    val communityUsed: String = "netvision",
    val latencyMs: Long = 0,
    val networkInterface: String = "VPN (Auto)",
    val snmpVersion: String = "v2c",
    val negotiationSummary: String? = null
)

data class UbiquitiStationInfo(
    val macAddress: String,
    val deviceName: String? = null,
    val signalDbm: Int? = null,
    val noiseDbm: Int? = null,
    val distance: Int? = null,
    val ccqPercent: Int? = null,
    val airMaxQualityPercent: Int? = null,
    val airMaxCapacityPercent: Int? = null,
    val lastIpAddress: String? = null,
    val txRate: Int? = null,
    val rxRate: Int? = null,
    val connectionTime: String? = null,
    val latencyMs: Int? = null
)

data class MikrotikStationInfo(
    val macAddress: String,
    val deviceName: String? = null,
    val signalDbm: Int? = null,
    val txRateMbps: Double? = null,
    val rxRateMbps: Double? = null,
    val connectionTime: String? = null
)

data class SnmpDiagnosticRecord(
    val timestamp: Long = System.currentTimeMillis(),
    val targetIp: String,
    val port: Int,
    val version: String = "v2c",
    val communityMasked: String,
    val oidName: String,
    val bytesSent: Int,
    val bytesReceived: Int,
    val timeSentMs: Long,
    val timeReceivedMs: Long,
    val latencyMs: Long,
    val networkInterface: String,
    val isSuccess: Boolean,
    val resultSummary: String,
    val exceptionDetail: String? = null,
    val attemptNumber: Int = 1
)

data class StationCountResult(
    val isSupported: Boolean,
    val count: Int = 0,
    val source: String = "None",
    val macAddresses: List<String> = emptyList()
)

object SnmpV2Client {

    private const val TAG = "NetVision_SNMP"
    private val requestIdCounter = AtomicInteger(1000)

    // In-memory circular diagnostic log buffer (retains last 40 transactions)
    private val diagnosticHistory = ConcurrentLinkedQueue<SnmpDiagnosticRecord>()

    fun getDiagnosticHistory(): List<SnmpDiagnosticRecord> = diagnosticHistory.toList()

    fun maskCommunity(community: String): String {
        if (community.length <= 2) return "**"
        return "${community.first()}${"*".repeat(community.length - 2)}${community.last()}"
    }

    // 1.3.6.1.2.1.1.1.0 (sysDescr)
    val OID_SYS_DESCR = byteArrayOf(0x2b, 0x06, 0x01, 0x02, 0x01, 0x01, 0x01, 0x00)
    // 1.3.6.1.2.1.1.2.0 (sysObjectID)
    val OID_SYS_OBJECT_ID = byteArrayOf(0x2b, 0x06, 0x01, 0x02, 0x01, 0x01, 0x02, 0x00)
    // 1.3.6.1.2.1.1.3.0 (sysUpTime)
    val OID_SYS_UPTIME = byteArrayOf(0x2b, 0x06, 0x01, 0x02, 0x01, 0x01, 0x03, 0x00)
    // 1.3.6.1.2.1.1.5.0 (sysName)
    val OID_SYS_NAME = byteArrayOf(0x2b, 0x06, 0x01, 0x02, 0x01, 0x01, 0x05, 0x00)
    // 1.3.6.1.2.1.2.1.0 (ifNumber)
    val OID_IF_NUMBER = byteArrayOf(0x2b, 0x06, 0x01, 0x02, 0x01, 0x02, 0x01, 0x00)

    // Table OIDs for wireless stations walk:
    // MikroTik Wireless Registration Table MAC Address Column: 1.3.6.1.4.1.14988.1.1.1.2.1.1
    val OID_MIKROTIK_REG_TABLE_MAC_STR = "1.3.6.1.4.1.14988.1.1.1.2.1.1"
    val OID_MIKROTIK_REG_TABLE_MAC_BYTES = encodeOidString(OID_MIKROTIK_REG_TABLE_MAC_STR)

    // Ubiquiti AirOS Station Table MAC Address Column: 1.3.6.1.4.1.41112.1.4.7.1.1.1
    val OID_UBNT_STA_TABLE_MAC_STR = "1.3.6.1.4.1.41112.1.4.7.1.1"
    val OID_UBNT_STA_TABLE_MAC_BYTES = encodeOidString(OID_UBNT_STA_TABLE_MAC_STR)

    // Scalar Fallbacks:
    // Ubiquiti ubntAirMaxStationCount (1.3.6.1.4.1.41112.1.4.5.1.15.1)
    val OID_UBNT_STA_COUNT_BYTES = byteArrayOf(0x2b, 0x06, 0x01, 0x04, 0x01, 0x82.toByte(), 0xc1.toByte(), 0x28, 0x01, 0x04, 0x05, 0x01, 0x0f, 0x01, 0x00)
    // IEEE 802.11 dot11AssociatedStationCount (1.2.840.10036.1.1.1.9.1.0)
    val OID_DOT11_STA_COUNT_BYTES = byteArrayOf(0x2a, 0x86.toByte(), 0x48, 0x82.toByte(), 0x8e.toByte(), 0x24, 0x01, 0x01, 0x01, 0x09, 0x01, 0x00)
    // MikroTik Wireless Registration Table count scalar (1.3.6.1.4.1.14988.1.1.1.3.1.6.1)
    val OID_MIKROTIK_WLAN_COUNT_BYTES = byteArrayOf(0x2b, 0x06, 0x01, 0x04, 0x01, 0xf5.toByte(), 0x0c, 0x01, 0x01, 0x01, 0x03, 0x01, 0x06, 0x01)

    fun extractModel(descr: String?, sysObjId: String? = null): String {
        val cleanDescr = descr?.trim() ?: ""
        val cleanObjId = sysObjId?.trim() ?: ""

        // 1. Check Ubiquiti markers first
        if (cleanObjId.contains("41112") || cleanDescr.contains("AirOS", ignoreCase = true) || cleanDescr.contains("UBNT", ignoreCase = true) || cleanDescr.contains("Ubiquiti", ignoreCase = true)) {
            val ubntKeywords = listOf("LiteBeam", "PowerBeam", "NanoStation", "Rocket", "AirGrid", "NanoBeam", "Bullet", "IsoStation", "GigaBeam", "UniFi", "EdgeRouter", "EdgeSwitch")
            for (kw in ubntKeywords) {
                if (cleanDescr.contains(kw, ignoreCase = true)) {
                    val idx = cleanDescr.indexOf(kw, ignoreCase = true)
                    val extracted = cleanDescr.substring(idx).split('\n', ',', ';').firstOrNull()?.trim()?.take(28) ?: kw
                    return "Ubiquiti $extracted"
                }
            }
            return "Ubiquiti AirOS"
        }

        // 2. Check MikroTik markers
        if (cleanObjId.contains("14988") || cleanDescr.contains("RouterOS", ignoreCase = true) || cleanDescr.contains("MikroTik", ignoreCase = true)) {
            val rbMatch = Regex("""(RB\w+(-\w+)?|CRS\w+|CCR\w+|hEX\w*|hAP\w*|NetMetal\w*|BaseBox\w*|LHG\w*|cAP\w*|OmniTIK\w*|SXT\w*)""", RegexOption.IGNORE_CASE).find(cleanDescr)
            if (rbMatch != null) return "MikroTik ${rbMatch.value}"
            return "MikroTik RouterOS"
        }

        if (cleanDescr.isNotBlank()) return cleanDescr.take(30)
        return "Unknown Device"
    }

    suspend fun queryDeviceInfo(
        targetIp: String,
        community: String = "netvision",
        port: Int = 161,
        preferredVersion: String? = null,
        timeoutMs: Int = 1000
    ): Result<SnmpDeviceInfo> = withContext(Dispatchers.IO) {
        val totalStart = System.currentTimeMillis()

        val versionsToTry = if (preferredVersion?.equals("v1", ignoreCase = true) == true) {
            listOf("v1", "v2c")
        } else {
            listOf("v2c", "v1")
        }

        var lastErrorMessage: String? = null
        var lastInterface = "VPN (Auto)"

        for (index in versionsToTry.indices) {
            val currentVersion = versionsToTry[index]
            val attemptNumber = index + 1

            // Probe sysDescr
            val descrRes = querySingleOid(
                ip = targetIp,
                community = community,
                port = port,
                oidBytes = OID_SYS_DESCR,
                oidName = "sysDescr",
                version = currentVersion,
                timeoutMs = timeoutMs,
                attemptNumber = attemptNumber
            )
            lastInterface = descrRes.networkInterface

            if (descrRes.isSuccess) {
                // Query sysObjectID
                val sysObjRes = querySingleOid(
                    ip = targetIp,
                    community = community,
                    port = port,
                    oidBytes = OID_SYS_OBJECT_ID,
                    oidName = "sysObjectID",
                    version = currentVersion,
                    timeoutMs = timeoutMs,
                    attemptNumber = attemptNumber
                )

                val nameRes = querySingleOid(
                    ip = targetIp,
                    community = community,
                    port = port,
                    oidBytes = OID_SYS_NAME,
                    oidName = "sysName",
                    version = currentVersion,
                    timeoutMs = timeoutMs,
                    attemptNumber = attemptNumber
                )

                val uptimeRes = querySingleOid(
                    ip = targetIp,
                    community = community,
                    port = port,
                    oidBytes = OID_SYS_UPTIME,
                    oidName = "sysUpTime",
                    version = currentVersion,
                    timeoutMs = timeoutMs,
                    attemptNumber = attemptNumber
                )

                val ifRes = querySingleOid(
                    ip = targetIp,
                    community = community,
                    port = port,
                    oidBytes = OID_IF_NUMBER,
                    oidName = "ifNumber",
                    version = currentVersion,
                    timeoutMs = timeoutMs,
                    attemptNumber = attemptNumber
                )

                // Query wireless connected stations count using GETNEXT table walk
                val stationResult = queryWirelessStationCount(
                    ip = targetIp,
                    community = community,
                    port = port,
                    version = currentVersion,
                    timeoutMs = 800,
                    attemptNumber = attemptNumber
                )

                val clientCount: Int? = if (stationResult.isSupported) stationResult.count else null
                // AirOS reports a generic Linux sysDescr and sysObjectID is an ASN.1 OID,
                // so identify Ubiquiti from the station-table walk source as well.
                val isUbiquiti = stationResult.source.contains("Ubiquiti", ignoreCase = true) ||
                    sysObjRes.stringValue?.contains("41112") == true ||
                    descrRes.stringValue?.let { it.contains("AirOS", true) || it.contains("UBNT", true) || it.contains("Ubiquiti", true) } == true
                val isMikroTik = stationResult.source.contains("MikroTik", ignoreCase = true) ||
                    sysObjRes.stringValue?.contains("14988") == true ||
                    descrRes.stringValue?.let { it.contains("RouterOS", true) || it.contains("MikroTik", true) } == true
                val detailedMikrotikStations = if (isMikroTik) {
                    walkMikrotikStations(targetIp, community, port, currentVersion, 800, attemptNumber)
                } else emptyList()
                val detailedStations = if (isUbiquiti) {
                    walkUbiquitiStations(
                        ip = targetIp,
                        community = community,
                        port = port,
                        version = currentVersion,
                        timeoutMs = 800,
                        attemptNumber = attemptNumber
                    )
                } else emptyList()

                val totalLatency = System.currentTimeMillis() - totalStart
                val descr = descrRes.stringValue
                val sysObjId = sysObjRes.stringValue
                val name = nameRes.stringValue
                val uptimeTicks = uptimeRes.longValue
                val uptimeFormatted = uptimeTicks?.let { formatTimeTicks(it) }
                val ifCount = ifRes.intValue
                val modelName = extractModel(descr, sysObjId)

                val negSummary = if (attemptNumber == 1) {
                    if (preferredVersion != null) "SNMP $currentVersion (Saved)" else "SNMP $currentVersion (Primary)"
                } else {
                    "Attempt 1 (${versionsToTry[0]}) failed -> Attempt 2 ($currentVersion) succeeded"
                }

                return@withContext Result.success(
                    SnmpDeviceInfo(
                        isSuccess = true,
                        sysDescr = descr,
                        sysName = name,
                        uptimeHundredths = uptimeTicks,
                        uptimeFormatted = uptimeFormatted,
                        model = modelName,
                        ifCount = ifCount,
                        connectedClientsCount = clientCount,
                        connectedStationsMacs = if (detailedStations.isNotEmpty()) detailedStations.map { it.macAddress } else stationResult.macAddresses,
                        connectedStations = detailedStations,
                        connectedMikrotikStations = detailedMikrotikStations,
                        communityUsed = community,
                        latencyMs = totalLatency,
                        networkInterface = lastInterface,
                        snmpVersion = currentVersion,
                        negotiationSummary = negSummary
                    )
                )
            } else {
                lastErrorMessage = descrRes.errorMessage
            }
        }

        val totalLatency = System.currentTimeMillis() - totalStart
        val primaryError = lastErrorMessage
            ?: "Timeout - No response from $targetIp:$port (Tried ${versionsToTry.joinToString(" & ")}, Community: '${maskCommunity(community)}')"

        val negSummary = "Attempt 1 (${versionsToTry[0]}) failed, Attempt 2 (${versionsToTry[1]}) failed"

        Result.success(
            SnmpDeviceInfo(
                isSuccess = false,
                errorMessage = primaryError,
                communityUsed = community,
                latencyMs = totalLatency,
                networkInterface = lastInterface,
                snmpVersion = versionsToTry[0],
                negotiationSummary = negSummary
            )
        )
    }

    /**
     * Walks SNMP wireless station tables (MikroTik / Ubiquiti) or queries fallback scalars
     */
    private fun queryWirelessStationCount(
        ip: String,
        community: String,
        port: Int,
        version: String,
        timeoutMs: Int,
        attemptNumber: Int
    ): StationCountResult {
        // 1. Try MikroTik Wireless Registration Table (1.3.6.1.4.1.14988.1.1.1.2.1.1)
        val mtxWalk = walkTable(
            ip = ip,
            community = community,
            port = port,
            rootOidStr = OID_MIKROTIK_REG_TABLE_MAC_STR,
            oidName = "mtxRegistrationTable",
            version = version,
            maxRows = 200,
            timeoutMs = timeoutMs,
            attemptNumber = attemptNumber
        )
        if (mtxWalk.isSupported) {
            return StationCountResult(isSupported = true, count = mtxWalk.rowCount, source = "MikroTik Registration Table", macAddresses = mtxWalk.macAddresses)
        }

        // 2. Try Ubiquiti AirOS Station Table (1.3.6.1.4.1.41112.1.4.7.1.1.1)
        val ubntWalk = walkTable(
            ip = ip,
            community = community,
            port = port,
            rootOidStr = OID_UBNT_STA_TABLE_MAC_STR,
            oidName = "ubntStationTable",
            version = version,
            maxRows = 200,
            timeoutMs = timeoutMs,
            attemptNumber = attemptNumber
        )
        if (ubntWalk.isSupported) {
            return StationCountResult(isSupported = true, count = ubntWalk.rowCount, source = "Ubiquiti AirOS Station Table", macAddresses = ubntWalk.macAddresses)
        }

        // 3. Fallback: Ubiquiti ubntAirMaxStationCount scalar
        val ubntStaScalar = querySingleOid(
            ip = ip, community = community, port = port,
            oidBytes = OID_UBNT_STA_COUNT_BYTES, oidName = "ubntStaCountScalar",
            version = version, timeoutMs = 500, attemptNumber = attemptNumber
        )
        if (ubntStaScalar.isSuccess && ubntStaScalar.intValue != null) {
            return StationCountResult(isSupported = true, count = ubntStaScalar.intValue.coerceAtLeast(0), source = "Ubiquiti Scalar")
        }

        // 4. Fallback: IEEE 802.11 dot11AssociatedStationCount
        val dot11StaScalar = querySingleOid(
            ip = ip, community = community, port = port,
            oidBytes = OID_DOT11_STA_COUNT_BYTES, oidName = "dot11StaCountScalar",
            version = version, timeoutMs = 500, attemptNumber = attemptNumber
        )
        if (dot11StaScalar.isSuccess && dot11StaScalar.intValue != null) {
            return StationCountResult(isSupported = true, count = dot11StaScalar.intValue.coerceAtLeast(0), source = "dot11 Scalar")
        }

        // 5. Fallback: MikroTik WLAN count scalar
        val mtxWlanScalar = querySingleOid(
            ip = ip, community = community, port = port,
            oidBytes = OID_MIKROTIK_WLAN_COUNT_BYTES, oidName = "mtxWlanCountScalar",
            version = version, timeoutMs = 500, attemptNumber = attemptNumber
        )
        if (mtxWlanScalar.isSuccess && mtxWlanScalar.intValue != null) {
            return StationCountResult(isSupported = true, count = mtxWlanScalar.intValue.coerceAtLeast(0), source = "MikroTik Scalar")
        }

        // Table / MIB not supported on this device
        return StationCountResult(isSupported = false, count = 0, source = "Unsupported")
    }


    /**
     * Reads the complete AirMAX station entry table. Each station row is correlated
     * by its SNMP index so names and radio metrics cannot be assigned to another MAC.
     */
    private fun walkUbiquitiStations(
        ip: String,
        community: String,
        port: Int,
        version: String,
        timeoutMs: Int,
        attemptNumber: Int
    ): List<UbiquitiStationInfo> {
        val tableRoot = "1.3.6.1.4.1.41112.1.4.7.1"
        var currentOid = encodeOidString(tableRoot)
        data class MutableStation(
            var mac: String? = null,
            var name: String? = null,
            var signal: Int? = null,
            var noise: Int? = null,
            var distance: Int? = null,
            var ccq: Int? = null,
            var quality: Int? = null,
            var capacity: Int? = null,
            var lastIp: String? = null,
            var txRate: Int? = null,
            var rxRate: Int? = null,
            var connectionTicks: Long? = null,
            var latency: Int? = null
        )
        val rows = linkedMapOf<String, MutableStation>()

        for (step in 1..400) {
            val reqId = requestIdCounter.incrementAndGet()
            val versionInt = if (version.equals("v1", ignoreCase = true)) 0 else 1
            val packet = buildGetNextRequest(reqId, community, currentOid, versionInt)
            var shouldStop = false
            try {
                DatagramSocket().use { socket ->
                    socket.soTimeout = timeoutMs
                    val vpnInfo = resolveVpnNetwork()
                    if (vpnInfo.first != null) {
                        try { vpnInfo.first!!.bindSocket(socket) } catch (_: Exception) { /* retain existing VPN fallback */ }
                    }
                    val address = InetAddress.getByName(ip)
                    socket.send(DatagramPacket(packet, packet.size, address, port))
                    val buffer = ByteArray(2048)
                    val received = DatagramPacket(buffer, buffer.size)
                    socket.receive(received)
                    val parsed = parseSnmpResponse(buffer.copyOf(received.length), reqId, community)
                    val oid = parsed.responseOid
                    if (!parsed.isSuccess || oid == null || !oid.startsWith("$tableRoot.")) {
                        shouldStop = true
                    } else {
                        val parts = oid.removePrefix("$tableRoot.").split('.')
                        // OID layout: ubntStaEntry.<column>.<ifIndex>.<six MAC octets>.
                        if (parts.size < 8) {
                            shouldStop = true
                        } else {
                            val column = parts[0].toIntOrNull()
                            val index = parts.drop(1).joinToString(".")
                            if (index.isNotBlank()) {
                                val row = rows.getOrPut(index) { MutableStation() }
                                val macOctets = parts.takeLast(6).mapNotNull { it.toIntOrNull() }
                                if (macOctets.size == 6 && macOctets.all { it in 0..255 }) {
                                    row.mac = macOctets.joinToString(":") { "%02X".format(it) }
                                }
                                when (column) {
                                    1 -> parsed.stringValue?.let { value ->
                                        if (value.matches(Regex("^(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$"))) row.mac = value.uppercase(Locale.ROOT)
                                    }
                                    2 -> row.name = parsed.stringValue?.takeIf { it.isNotBlank() }
                                    3 -> row.signal = parsed.intValue
                                    4 -> row.noise = parsed.intValue
                                    5 -> row.distance = parsed.intValue
                                    6 -> row.ccq = parsed.intValue
                                    8 -> row.quality = parsed.intValue
                                    9 -> row.capacity = parsed.intValue
                                    10 -> row.lastIp = parsed.stringValue
                                    11 -> row.txRate = parsed.intValue
                                    12 -> row.rxRate = parsed.intValue
                                    15 -> row.connectionTicks = parsed.longValue
                                    21 -> row.latency = parsed.intValue
                                }
                            }
                            currentOid = encodeOidString(oid)
                        }
                    }
                }
            } catch (_: Exception) {
                shouldStop = true
            }
            if (shouldStop) break
        }

        return rows.values.mapNotNull { row ->
            val mac = row.mac?.takeIf { it.matches(Regex("^(?:[0-9A-F]{2}:){5}[0-9A-F]{2}$")) }
                ?: return@mapNotNull null
            UbiquitiStationInfo(
                macAddress = mac,
                deviceName = row.name,
                signalDbm = row.signal,
                noiseDbm = row.noise,
                distance = row.distance,
                ccqPercent = row.ccq,
                airMaxQualityPercent = row.quality,
                airMaxCapacityPercent = row.capacity,
                lastIpAddress = row.lastIp,
                txRate = row.txRate,
                rxRate = row.rxRate,
                connectionTime = row.connectionTicks?.let { formatTimeTicks(it) },
                latencyMs = row.latency
            )
        }
    }

    /**
     * Reads MikroTik wireless registration entries. Index format is
     * <interfaceIndex>.<six MAC octets>; columns are read by their table OID.
     */
    private fun walkMikrotikStations(
        ip: String,
        community: String,
        port: Int,
        version: String,
        timeoutMs: Int,
        attemptNumber: Int
    ): List<MikrotikStationInfo> {
        val tableRoot = "1.3.6.1.4.1.14988.1.1.1.2.1"
        var currentOid = encodeOidString(tableRoot)
        data class MutableStation(
            var mac: String? = null,
            var name: String? = null,
            var signal: Int? = null,
            var txRate: Long? = null,
            var rxRate: Long? = null,
            var uptimeTicks: Long? = null
        )
        val rows = linkedMapOf<String, MutableStation>()

        for (step in 1..500) {
            val requestId = requestIdCounter.incrementAndGet()
            val versionInt = if (version.equals("v1", ignoreCase = true)) 0 else 1
            val packet = buildGetNextRequest(requestId, community, currentOid, versionInt)
            var shouldStop = false
            try {
                DatagramSocket().use { socket ->
                    socket.soTimeout = timeoutMs
                    val vpnInfo = resolveVpnNetwork()
                    if (vpnInfo.first != null) {
                        try { vpnInfo.first!!.bindSocket(socket) } catch (_: Exception) { }
                    }
                    val address = InetAddress.getByName(ip)
                    socket.send(DatagramPacket(packet, packet.size, address, port))
                    val buffer = ByteArray(2048)
                    val received = DatagramPacket(buffer, buffer.size)
                    socket.receive(received)
                    val parsed = parseSnmpResponse(buffer.copyOf(received.length), requestId, community)
                    val oid = parsed.responseOid
                    if (!parsed.isSuccess || oid == null || !oid.startsWith("$tableRoot.")) {
                        shouldStop = true
                    } else {
                        val parts = oid.removePrefix("$tableRoot.").split('.')
                        // Column + interface index + six MAC octets.
                        if (parts.size < 8) {
                            shouldStop = true
                        } else {
                            val column = parts[0].toIntOrNull()
                            val indexParts = parts.drop(1)
                            val index = indexParts.joinToString(".")
                            val macOctets = indexParts.takeLast(6).mapNotNull { it.toIntOrNull() }
                            val row = rows.getOrPut(index) { MutableStation() }
                            if (macOctets.size == 6 && macOctets.all { it in 0..255 }) {
                                row.mac = macOctets.joinToString(":") { "%02X".format(it) }
                            }
                            when (column) {
                                1 -> parsed.stringValue?.let { raw ->
                                    val hex = raw.trim().split(Regex("\\s+"))
                                    if (hex.size == 6 && hex.all { it.toIntOrNull(16) in 0..255 }) {
                                        row.mac = hex.joinToString(":") { "%02X".format(it.toInt(16)) }
                                    }
                                }
                                3 -> row.signal = parsed.intValue
                                8 -> row.txRate = parsed.longValue
                                9 -> row.rxRate = parsed.longValue
                                11 -> row.uptimeTicks = parsed.longValue
                                20 -> row.name = parsed.stringValue?.takeIf { it.isNotBlank() }
                            }
                            currentOid = encodeOidString(oid)
                        }
                    }
                }
            } catch (_: Exception) {
                shouldStop = true
            }
            if (shouldStop) break
        }

        return rows.values.mapNotNull { row ->
            val mac = row.mac?.takeIf { it.matches(Regex("^(?:[0-9A-F]{2}:){5}[0-9A-F]{2}$")) }
                ?: return@mapNotNull null
            MikrotikStationInfo(
                macAddress = mac,
                deviceName = row.name,
                signalDbm = row.signal,
                txRateMbps = row.txRate?.div(1_000_000.0),
                rxRateMbps = row.rxRate?.div(1_000_000.0),
                connectionTime = row.uptimeTicks?.let { formatTimeTicks(it) }
            )
        }
    }

    data class TableWalkResult(
        val isSupported: Boolean,
        val rowCount: Int = 0,
        val macAddresses: List<String> = emptyList()
    )

    /**
     * Executes GETNEXT walk starting at rootOidStr until the returned OID leaves rootOidStr prefix or maxRows is reached.
     */
    fun walkTable(
        ip: String,
        community: String,
        port: Int,
        rootOidStr: String,
        oidName: String,
        version: String = "v2c",
        maxRows: Int = 200,
        timeoutMs: Int = 800,
        attemptNumber: Int = 1
    ): TableWalkResult {
        var currentOidBytes = encodeOidString(rootOidStr)
        val uniqueRows = mutableSetOf<String>()
        val macAddresses = linkedSetOf<String>()
        var isTableSupported = false

        for (step in 1..maxRows) {
            val reqId = requestIdCounter.incrementAndGet()
            val versionInt = if (version.equals("v1", ignoreCase = true)) 0 else 1
            val packetBytes = buildGetNextRequest(reqId, community, currentOidBytes, versionInt)
            val maskedComm = maskCommunity(community)

            var timeSent = 0L
            var timeReceived = 0L
            var ifaceName = "Default"

            var shouldExitLoop = false
            try {
                DatagramSocket().use { socket ->
                    socket.soTimeout = timeoutMs

                    val vpnInfo = resolveVpnNetwork()
                    ifaceName = vpnInfo.second
                    if (vpnInfo.first != null) {
                        try {
                            vpnInfo.first!!.bindSocket(socket)
                        } catch (e: Exception) {
                            // Keep the existing fallback behavior if VPN binding is unavailable.
                        }
                    }

                    val address = InetAddress.getByName(ip)
                    val sendPacket = DatagramPacket(packetBytes, packetBytes.size, address, port)

                    timeSent = System.currentTimeMillis()
                    socket.send(sendPacket)

                    val buffer = ByteArray(2048)
                    val receivePacket = DatagramPacket(buffer, buffer.size)
                    socket.receive(receivePacket)
                    timeReceived = System.currentTimeMillis()

                    val responseData = buffer.copyOf(receivePacket.length)
                    val parsed = parseSnmpResponse(responseData, reqId, community)

                    if (!parsed.isSuccess || parsed.responseOid == null) {
                        // Error, endOfMibView, or noSuchInstance.
                        if (step == 1) {
                            return TableWalkResult(isSupported = false, rowCount = 0)
                        }
                        shouldExitLoop = true
                    } else {
                        val respOidStr = parsed.responseOid
                        if (!respOidStr.startsWith(rootOidStr)) {
                            // GETNEXT left this subtree immediately: this agent does not
                            // expose this table/column. Let the caller try the next vendor OID.
                            shouldExitLoop = true
                        } else {
                            isTableSupported = true
                            uniqueRows.add(respOidStr)
                            parsed.stringValue?.let { value ->
                                val mac = value.trim().uppercase(Locale.ROOT)
                                if (Regex("^(?:[0-9A-F]{2}:){5}[0-9A-F]{2}$").matches(mac)) {
                                    macAddresses.add(mac)
                                }
                            }
                            currentOidBytes = encodeOidString(respOidStr)
                        }
                    }
                }
            } catch (e: Exception) {
                if (step == 1) {
                    return TableWalkResult(isSupported = false, rowCount = 0)
                }
                shouldExitLoop = true
            }

            if (shouldExitLoop) {
                break
            }
        }

        return TableWalkResult(isSupported = isTableSupported, rowCount = macAddresses.size.takeIf { it > 0 } ?: uniqueRows.size, macAddresses = macAddresses.toList())
    }

    data class SingleOidResult(
        val isSuccess: Boolean,
        val stringValue: String? = null,
        val longValue: Long? = null,
        val intValue: Int? = null,
        val errorMessage: String? = null,
        val networkInterface: String = "Default",
        val latencyMs: Long = 0,
        val versionUsed: String = "v2c"
    )

    fun querySingleOid(
        ip: String,
        community: String,
        port: Int,
        oidBytes: ByteArray,
        oidName: String,
        version: String = "v2c",
        timeoutMs: Int = 1000,
        attemptNumber: Int = 1
    ): SingleOidResult {
        val reqId = requestIdCounter.incrementAndGet()
        val versionInt = if (version.equals("v1", ignoreCase = true)) 0 else 1
        val packetBytes = buildGetRequest(reqId, community, oidBytes, versionInt)
        val maskedComm = maskCommunity(community)

        var timeSent = 0L
        var timeReceived = 0L
        var bytesReceived = 0
        var ifaceName = "Default"

        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs

                val vpnInfo = resolveVpnNetwork()
                ifaceName = vpnInfo.second
                if (vpnInfo.first != null) {
                    try {
                        vpnInfo.first!!.bindSocket(socket)
                    } catch (e: Exception) {
                        if (ifaceName.contains("VPN", ignoreCase = true) || ifaceName.startsWith("tun")) {
                            throw IOException("VPN active ($ifaceName) but socket bind failed: ${e.message}")
                        }
                    }
                }

                val address = InetAddress.getByName(ip)
                val sendPacket = DatagramPacket(packetBytes, packetBytes.size, address, port)

                timeSent = System.currentTimeMillis()
                socket.send(sendPacket)

                val buffer = ByteArray(2048)
                val receivePacket = DatagramPacket(buffer, buffer.size)

                socket.receive(receivePacket)
                timeReceived = System.currentTimeMillis()
                bytesReceived = receivePacket.length

                val latency = timeReceived - timeSent
                val responseData = buffer.copyOf(receivePacket.length)

                val parsed = parseSnmpResponse(responseData, reqId, community)

                val record = SnmpDiagnosticRecord(
                    targetIp = ip,
                    port = port,
                    version = parsed.versionDecoded,
                    communityMasked = maskedComm,
                    oidName = oidName,
                    bytesSent = packetBytes.size,
                    bytesReceived = bytesReceived,
                    timeSentMs = timeSent,
                    timeReceivedMs = timeReceived,
                    latencyMs = latency,
                    networkInterface = ifaceName,
                    isSuccess = parsed.isSuccess,
                    resultSummary = parsed.summary,
                    exceptionDetail = parsed.errorDetail,
                    attemptNumber = attemptNumber
                )
                logDiagnostic(record)

                if (parsed.isSuccess) {
                    return SingleOidResult(
                        isSuccess = true,
                        stringValue = parsed.stringValue,
                        longValue = parsed.longValue,
                        intValue = parsed.intValue,
                        networkInterface = ifaceName,
                        latencyMs = latency,
                        versionUsed = parsed.versionDecoded
                    )
                } else {
                    return SingleOidResult(
                        isSuccess = false,
                        errorMessage = parsed.errorDetail ?: "SNMP response error",
                        networkInterface = ifaceName,
                        latencyMs = latency,
                        versionUsed = parsed.versionDecoded
                    )
                }
            }
        } catch (e: SocketTimeoutException) {
            val latency = if (timeSent > 0) System.currentTimeMillis() - timeSent else timeoutMs.toLong()
            val msg = "Timeout - No response on UDP $port via $ifaceName (SNMP $version)"
            logDiagnostic(
                SnmpDiagnosticRecord(
                    targetIp = ip,
                    port = port,
                    version = version,
                    communityMasked = maskedComm,
                    oidName = oidName,
                    bytesSent = packetBytes.size,
                    bytesReceived = 0,
                    timeSentMs = timeSent,
                    timeReceivedMs = 0L,
                    latencyMs = latency,
                    networkInterface = ifaceName,
                    isSuccess = false,
                    resultSummary = "Timeout",
                    exceptionDetail = "${e.javaClass.simpleName}: ${e.message ?: "SocketTimeout"}",
                    attemptNumber = attemptNumber
                )
            )
            return SingleOidResult(isSuccess = false, errorMessage = msg, networkInterface = ifaceName, latencyMs = latency, versionUsed = version)
        } catch (e: Exception) {
            val latency = if (timeSent > 0) System.currentTimeMillis() - timeSent else 0L
            val msg = "${e.javaClass.simpleName}: ${e.message}"
            logDiagnostic(
                SnmpDiagnosticRecord(
                    targetIp = ip,
                    port = port,
                    version = version,
                    communityMasked = maskedComm,
                    oidName = oidName,
                    bytesSent = packetBytes.size,
                    bytesReceived = 0,
                    timeSentMs = timeSent,
                    timeReceivedMs = 0L,
                    latencyMs = latency,
                    networkInterface = ifaceName,
                    isSuccess = false,
                    resultSummary = "Error",
                    exceptionDetail = msg,
                    attemptNumber = attemptNumber
                )
            )
            return SingleOidResult(isSuccess = false, errorMessage = msg, networkInterface = ifaceName, latencyMs = latency, versionUsed = version)
        }
    }

    private fun logDiagnostic(entry: SnmpDiagnosticRecord) {
        diagnosticHistory.add(entry)
        while (diagnosticHistory.size > 40) {
            diagnosticHistory.poll()
        }
        Log.i(TAG, "SNMP [${entry.oidName} - ${entry.version} (Att ${entry.attemptNumber})] to ${entry.targetIp}:${entry.port} via ${entry.networkInterface} | Success: ${entry.isSuccess} | Latency: ${entry.latencyMs}ms | ${entry.resultSummary}")
    }

    private fun resolveVpnNetwork(): Pair<Network?, String> {
        try {
            val app = try { NetVisionApplication.instance } catch (e: Exception) { null } ?: return Pair(null, "No AppContext")
            val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return Pair(null, "No CM")

            val networks = cm.allNetworks
            for (network in networks) {
                val caps = cm.getNetworkCapabilities(network)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                    val linkProps = cm.getLinkProperties(network)
                    val name = linkProps?.interfaceName ?: "tun0 (VPN)"
                    return Pair(network, name)
                }
            }

            val activeNet = cm.activeNetwork
            if (activeNet != null) {
                val caps = cm.getNetworkCapabilities(activeNet)
                if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                    val linkProps = cm.getLinkProperties(activeNet)
                    val name = linkProps?.interfaceName ?: "tun0 (VPN)"
                    return Pair(activeNet, name)
                }
                val activeProps = cm.getLinkProperties(activeNet)
                val name = activeProps?.interfaceName ?: "Default"
                return Pair(activeNet, name)
            }
            return Pair(null, "Default")
        } catch (e: Exception) {
            return Pair(null, "Default")
        }
    }

    fun buildGetRequest(reqId: Int, community: String, oidBytes: ByteArray, version: Int = 1): ByteArray {
        return buildPduMessage(reqId, community, oidBytes, pduTag = 0xA0, version = version)
    }

    fun buildGetNextRequest(reqId: Int, community: String, oidBytes: ByteArray, version: Int = 1): ByteArray {
        return buildPduMessage(reqId, community, oidBytes, pduTag = 0xA1, version = version)
    }

    private fun buildPduMessage(reqId: Int, community: String, oidBytes: ByteArray, pduTag: Int, version: Int = 1): ByteArray {
        val varBindInner = ByteArrayOutputStream()
        varBindInner.write(0x06) // OBJECT IDENTIFIER tag
        writeLength(varBindInner, oidBytes.size)
        varBindInner.write(oidBytes)
        varBindInner.write(byteArrayOf(0x05, 0x00)) // NULL tag and length 0

        val varBindBytes = varBindInner.toByteArray()
        val varBindSeq = ByteArrayOutputStream()
        varBindSeq.write(0x30)
        writeLength(varBindSeq, varBindBytes.size)
        varBindSeq.write(varBindBytes)

        val varBindListBytes = varBindSeq.toByteArray()
        val varBindListSeq = ByteArrayOutputStream()
        varBindListSeq.write(0x30)
        writeLength(varBindListSeq, varBindListBytes.size)
        varBindListSeq.write(varBindListBytes)

        val pduInner = ByteArrayOutputStream()
        writeBerInteger(pduInner, reqId)
        writeBerInteger(pduInner, 0)
        writeBerInteger(pduInner, 0)
        pduInner.write(varBindListSeq.toByteArray())

        val pduBytes = pduInner.toByteArray()
        val pduSeq = ByteArrayOutputStream()
        pduSeq.write(pduTag) // 0xA0 (Get) or 0xA1 (GetNext)
        writeLength(pduSeq, pduBytes.size)
        pduSeq.write(pduBytes)

        val msgInner = ByteArrayOutputStream()
        writeBerInteger(msgInner, version)
        val commBytes = community.toByteArray(Charsets.US_ASCII)
        msgInner.write(0x04)
        writeLength(msgInner, commBytes.size)
        msgInner.write(commBytes)
        msgInner.write(pduSeq.toByteArray())

        val msgBytes = msgInner.toByteArray()
        val finalSeq = ByteArrayOutputStream()
        finalSeq.write(0x30)
        writeLength(finalSeq, msgBytes.size)
        finalSeq.write(msgBytes)

        return finalSeq.toByteArray()
    }

    data class ParsedResponse(
        val isSuccess: Boolean,
        val versionDecoded: String = "v2c",
        val responseOid: String? = null,
        val stringValue: String? = null,
        val longValue: Long? = null,
        val intValue: Int? = null,
        val summary: String,
        val errorDetail: String? = null
    )

    fun parseSnmpResponse(data: ByteArray, expectedReqId: Int, expectedCommunity: String): ParsedResponse {
        try {
            var i = 0

            // 1. Top Sequence (0x30)
            if (data[i] != 0x30.toByte()) {
                return ParsedResponse(false, summary = "Invalid ASN.1", errorDetail = "Expected SEQUENCE 0x30, got ${data[i]}")
            }
            i++
            val (_, nextI) = readLength(data, i)
            i = nextI

            // 2. Version (INTEGER 0x02)
            if (data[i] != 0x02.toByte()) {
                return ParsedResponse(false, summary = "Invalid Version Tag", errorDetail = "Expected INTEGER 0x02, got ${data[i]}")
            }
            i++
            val (vLen, vNext) = readLength(data, i)
            var respVersion = 0
            for (k in 0 until vLen) {
                respVersion = (respVersion shl 8) or (data[vNext + k].toInt() and 0xFF)
            }
            i = vNext + vLen

            val versionDecoded = when (respVersion) {
                0 -> "v1"
                1 -> "v2c"
                2 -> "v2u"
                3 -> "v3"
                else -> "v$respVersion"
            }

            // 3. Community (OCTET STRING 0x04)
            if (data[i] != 0x04.toByte()) {
                return ParsedResponse(false, versionDecoded = versionDecoded, summary = "Invalid Community Tag", errorDetail = "Expected OCTET STRING 0x04, got ${data[i]}")
            }
            i++
            val (cLen, cNext) = readLength(data, i)
            i = cNext + cLen

            // 4. Response PDU (0xA2)
            val pduTag = data[i].toInt() and 0xFF
            if (pduTag != 0xA2) {
                return ParsedResponse(false, versionDecoded = versionDecoded, summary = "Invalid PDU", errorDetail = "Expected GetResponse PDU 0xA2, got 0x${Integer.toHexString(pduTag)}")
            }
            i++
            val (_, pduNext) = readLength(data, i)
            i = pduNext

            // 5. Request ID (INTEGER 0x02)
            if (data[i] != 0x02.toByte()) {
                return ParsedResponse(false, versionDecoded = versionDecoded, summary = "Invalid ReqId Tag", errorDetail = "Expected INTEGER for reqId")
            }
            i++
            val (reqLen, reqNext) = readLength(data, i)
            var responseReqId = 0
            for (k in 0 until reqLen) {
                responseReqId = (responseReqId shl 8) or (data[reqNext + k].toInt() and 0xFF)
            }
            i = reqNext + reqLen

            if (responseReqId != expectedReqId) {
                return ParsedResponse(
                    false,
                    versionDecoded = versionDecoded,
                    summary = "Request ID Mismatch",
                    errorDetail = "Response reqId ($responseReqId) != Sent reqId ($expectedReqId)"
                )
            }

            // 6. Error Status (INTEGER 0x02)
            if (data[i] != 0x02.toByte()) {
                return ParsedResponse(false, versionDecoded = versionDecoded, summary = "Invalid ErrorStatus", errorDetail = "Expected INTEGER for errorStatus")
            }
            i++
            val (errLen, errNext) = readLength(data, i)
            var errorStatus = 0
            for (k in 0 until errLen) {
                errorStatus = (errorStatus shl 8) or (data[errNext + k].toInt() and 0xFF)
            }
            i = errNext + errLen

            // 7. Error Index (INTEGER 0x02)
            if (data[i] != 0x02.toByte()) {
                return ParsedResponse(false, versionDecoded = versionDecoded, summary = "Invalid ErrorIndex", errorDetail = "Expected INTEGER for errorIndex")
            }
            i++
            val (idxLen, idxNext) = readLength(data, i)
            i = idxNext + idxLen

            if (errorStatus != 0) {
                val errName = when (errorStatus) {
                    1 -> "tooBig"
                    2 -> "noSuchName"
                    3 -> "badValue"
                    4 -> "readOnly"
                    5 -> "genErr"
                    16 -> "authorizationError"
                    else -> "Error code $errorStatus"
                }
                return ParsedResponse(false, versionDecoded = versionDecoded, summary = "SNMP Error: $errName", errorDetail = errName)
            }

            // 8. VarBindList Sequence (0x30)
            if (data[i] != 0x30.toByte()) {
                return ParsedResponse(false, versionDecoded = versionDecoded, summary = "Invalid VarBindList", errorDetail = "Expected SEQUENCE 0x30 for VarBindList")
            }
            i++
            val (_, vbListNext) = readLength(data, i)
            i = vbListNext

            // 9. First VarBind Sequence (0x30)
            if (data[i] != 0x30.toByte()) {
                return ParsedResponse(false, versionDecoded = versionDecoded, summary = "Invalid VarBind", errorDetail = "Expected SEQUENCE 0x30 for VarBind")
            }
            i++
            val (_, vbNext) = readLength(data, i)
            i = vbNext

            // 10. OID tag (0x06)
            if (data[i] != 0x06.toByte()) {
                return ParsedResponse(false, versionDecoded = versionDecoded, summary = "Invalid OID Tag", errorDetail = "Expected OBJECT IDENTIFIER 0x06")
            }
            i++
            val (oidLen, oidNext) = readLength(data, i)
            val respOidString = decodeOidBytes(data, oidNext, oidLen)
            i = oidNext + oidLen

            // 11. Value Tag
            val valTag = data[i].toInt() and 0xFF
            i++
            val (vDataLen, vDataNext) = readLength(data, i)

            when (valTag) {
                0x04 -> { // OCTET STRING
                    val str = String(data, vDataNext, vDataLen, Charsets.UTF_8).trim()
                    val hexStr = data.sliceArray(vDataNext until (vDataNext + vDataLen)).joinToString(":") { "%02x".format(it) }
                    val displayStr = if (vDataLen == 6) hexStr else str
                    return ParsedResponse(
                        isSuccess = true,
                        versionDecoded = versionDecoded,
                        responseOid = respOidString,
                        stringValue = displayStr,
                        summary = "String: ${displayStr.take(40)}"
                    )
                }
                0x43 -> { // TimeTicks (1/100 sec)
                    var ticks = 0L
                    for (k in 0 until vDataLen) {
                        ticks = (ticks shl 8) or (data[vDataNext + k].toLong() and 0xFFL)
                    }
                    return ParsedResponse(
                        isSuccess = true,
                        versionDecoded = versionDecoded,
                        responseOid = respOidString,
                        longValue = ticks,
                        intValue = ticks.toInt(),
                        summary = "TimeTicks: $ticks (${formatTimeTicks(ticks)})"
                    )
                }
                0x02 -> { // INTEGER (signed; preserve negative RF values such as -50 dBm)
                    var num = 0
                    for (k in 0 until vDataLen) {
                        num = (num shl 8) or (data[vDataNext + k].toInt() and 0xFF)
                    }
                    if (vDataLen in 1..3 && (data[vDataNext].toInt() and 0x80) != 0) {
                        num = num or (-1 shl (vDataLen * 8))
                    }
                    return ParsedResponse(
                        isSuccess = true,
                        versionDecoded = versionDecoded,
                        responseOid = respOidString,
                        intValue = num,
                        longValue = num.toLong(),
                        summary = "Integer: $num"
                    )
                }
                0x41, 0x42, 0x46 -> { // Counter32 / Gauge32 / Counter64 (Unsigned)
                    var num = 0L
                    for (k in 0 until vDataLen) {
                        num = (num shl 8) or (data[vDataNext + k].toLong() and 0xFFL)
                    }
                    val safePositiveNum = if (num < 0) (num and Long.MAX_VALUE) else num
                    return ParsedResponse(
                        isSuccess = true,
                        versionDecoded = versionDecoded,
                        responseOid = respOidString,
                        longValue = safePositiveNum,
                        intValue = safePositiveNum.toInt(),
                        summary = "Counter/Gauge: $safePositiveNum"
                    )
                }
                0x40 -> { // IpAddress (4 bytes)
                    if (vDataLen == 4) {
                        val ipStr = "${data[vDataNext].toInt() and 0xFF}.${data[vDataNext + 1].toInt() and 0xFF}.${data[vDataNext + 2].toInt() and 0xFF}.${data[vDataNext + 3].toInt() and 0xFF}"
                        return ParsedResponse(isSuccess = true, versionDecoded = versionDecoded, responseOid = respOidString, stringValue = ipStr, summary = "IpAddress: $ipStr")
                    } else {
                        return ParsedResponse(isSuccess = true, versionDecoded = versionDecoded, responseOid = respOidString, stringValue = "IpAddress", summary = "IpAddress")
                    }
                }
                0x80 -> return ParsedResponse(false, versionDecoded = versionDecoded, responseOid = respOidString, summary = "noSuchObject", errorDetail = "noSuchObject")
                0x81 -> return ParsedResponse(false, versionDecoded = versionDecoded, responseOid = respOidString, summary = "noSuchInstance", errorDetail = "noSuchInstance")
                0x82 -> return ParsedResponse(false, versionDecoded = versionDecoded, responseOid = respOidString, summary = "endOfMibView", errorDetail = "endOfMibView")
                else -> {
                    val rawHex = data.sliceArray(vDataNext until (vDataNext + vDataLen)).joinToString("") { "%02x".format(it) }
                    return ParsedResponse(isSuccess = true, versionDecoded = versionDecoded, responseOid = respOidString, stringValue = rawHex, summary = "Raw Tag 0x${Integer.toHexString(valTag)}: $rawHex")
                }
            }
        } catch (e: Exception) {
            return ParsedResponse(false, summary = "Parse Failure", errorDetail = "Malformed ASN.1 payload: ${e.message}")
        }
    }

    fun encodeOidString(oidStr: String): ByteArray {
        val parts = oidStr.trim().removePrefix(".").split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size < 2) return byteArrayOf()
        val out = ByteArrayOutputStream()
        out.write((parts[0] * 40 + parts[1]))
        for (i in 2 until parts.size) {
            val valInt = parts[i]
            if (valInt < 128) {
                out.write(valInt)
            } else {
                val list = mutableListOf<Int>()
                var v = valInt
                list.add(v and 0x7F)
                v = v ushr 7
                while (v > 0) {
                    list.add(0, (v and 0x7F) or 0x80)
                    v = v ushr 7
                }
                for (b in list) {
                    out.write(b)
                }
            }
        }
        return out.toByteArray()
    }

    fun decodeOidBytes(data: ByteArray, offset: Int, length: Int): String {
        if (length == 0) return ""
        val sb = StringBuilder()
        var pos = offset
        val first = data[pos].toInt() and 0xFF
        sb.append(first / 40).append('.').append(first % 40)
        pos++
        var value = 0L
        val end = offset + length
        while (pos < end) {
            val b = data[pos].toInt() and 0xFF
            value = (value shl 7) or (b and 0x7F).toLong()
            if ((b and 0x80) == 0) {
                sb.append('.').append(value)
                value = 0L
            }
            pos++
        }
        return sb.toString()
    }

    private fun writeBerInteger(out: ByteArrayOutputStream, value: Int) {
        if (value == 0) {
            out.write(byteArrayOf(0x02, 0x01, 0x00))
            return
        }
        val bytes = mutableListOf<Byte>()
        var temp = value
        while (temp != 0 && temp != -1) {
            bytes.add(0, (temp and 0xFF).toByte())
            temp = temp shr 8
        }
        if (value > 0 && bytes.isNotEmpty() && (bytes[0].toInt() and 0x80) != 0) {
            bytes.add(0, 0x00)
        } else if (value < 0 && bytes.isNotEmpty() && (bytes[0].toInt() and 0x80) == 0) {
            bytes.add(0, 0xFF.toByte())
        }
        if (bytes.isEmpty()) {
            bytes.add((value and 0xFF).toByte())
        }
        out.write(0x02)
        out.write(bytes.size)
        for (b in bytes) {
            out.write(b.toInt())
        }
    }

    private fun writeLength(out: ByteArrayOutputStream, length: Int) {
        if (length < 128) {
            out.write(length)
        } else {
            val lenBytes = mutableListOf<Byte>()
            var temp = length
            while (temp > 0) {
                lenBytes.add(0, (temp and 0xFF).toByte())
                temp = temp shr 8
            }
            out.write(0x80 or lenBytes.size)
            for (b in lenBytes) {
                out.write(b.toInt())
            }
        }
    }

    private fun readLength(data: ByteArray, offset: Int): Pair<Int, Int> {
        val b = data[offset].toInt() and 0xFF
        if (b < 128) {
            return Pair(b, offset + 1)
        }
        val numOctets = b and 0x7F
        var length = 0
        var pos = offset + 1
        for (k in 0 until numOctets) {
            length = (length shl 8) or (data[pos].toInt() and 0xFF)
            pos++
        }
        return Pair(length, pos)
    }

    fun formatTimeTicks(ticks: Long): String {
        val totalSeconds = ticks / 100
        val days = totalSeconds / (24 * 3600)
        val hours = (totalSeconds % (24 * 3600)) / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (days > 0) {
            "${days}d ${hours}h ${minutes}m"
        } else if (hours > 0) {
            "${hours}h ${minutes}m ${seconds}s"
        } else {
            "${minutes}m ${seconds}s"
        }
    }
}
