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

data class SnmpDeviceInfo(
    val isSuccess: Boolean,
    val sysDescr: String? = null,
    val sysName: String? = null,
    val uptimeHundredths: Long? = null,
    val uptimeFormatted: String? = null,
    val model: String? = null,
    val ifCount: Int? = null,
    val connectedClientsCount: Int? = null,
    val errorMessage: String? = null,
    val communityUsed: String = "netvision",
    val latencyMs: Long = 0,
    val networkInterface: String = "VPN (Auto)",
    val snmpVersion: String = "v2c",
    val negotiationSummary: String? = null
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
    // 1.3.6.1.2.1.1.3.0 (sysUpTime)
    val OID_SYS_UPTIME = byteArrayOf(0x2b, 0x06, 0x01, 0x02, 0x01, 0x01, 0x03, 0x00)
    // 1.3.6.1.2.1.1.5.0 (sysName)
    val OID_SYS_NAME = byteArrayOf(0x2b, 0x06, 0x01, 0x02, 0x01, 0x01, 0x05, 0x00)
    // 1.3.6.1.2.1.2.1.0 (ifNumber)
    val OID_IF_NUMBER = byteArrayOf(0x2b, 0x06, 0x01, 0x02, 0x01, 0x02, 0x01, 0x00)

    // Wireless Station Count OIDs (for Ubiquiti AirOS / MikroTik Wireless Registration Table):
    // Ubiquiti ubntAirMaxStationCount (1.3.6.1.4.1.41112.1.4.5.1.15.1)
    val OID_UBNT_STA_COUNT = byteArrayOf(0x2b, 0x06, 0x01, 0x04, 0x01, 0x82.toByte(), 0xc1.toByte(), 0x28, 0x01, 0x04, 0x05, 0x01, 0x0f, 0x01)
    // IEEE 802.11 dot11AssociatedStationCount (1.2.840.10036.1.1.1.9.1)
    val OID_DOT11_STA_COUNT = byteArrayOf(0x2a, 0x86.toByte(), 0x48, 0x82.toByte(), 0x8e.toByte(), 0x24, 0x01, 0x01, 0x01, 0x09, 0x01)
    // MikroTik Wireless Registration Table entries count (1.3.6.1.4.1.14988.1.1.1.3.1.6)
    val OID_MIKROTIK_WLAN_COUNT = byteArrayOf(0x2b, 0x06, 0x01, 0x04, 0x01, 0xf5.toByte(), 0x0c, 0x01, 0x01, 0x01, 0x03, 0x01, 0x06)

    fun extractModel(descr: String?): String {
        if (descr.isNullOrBlank()) return "Unknown"
        val clean = descr.trim()
        if (clean.contains("RouterOS", ignoreCase = true)) {
            val rbMatch = Regex("""(RB\w+(-\w+)?|CRS\w+|CCR\w+|hEX\w*|hAP\w*|NetMetal\w*|BaseBox\w*|LHG\w*|cAP\w*|OmniTIK\w*|SXT\w*)""", RegexOption.IGNORE_CASE).find(clean)
            if (rbMatch != null) return "MikroTik ${rbMatch.value}"
            return clean.take(30)
        }
        val ubntKeywords = listOf("LiteBeam", "PowerBeam", "NanoStation", "Rocket", "AirGrid", "NanoBeam", "Bullet", "IsoStation", "GigaBeam", "UniFi", "EdgeRouter", "EdgeSwitch")
        for (kw in ubntKeywords) {
            if (clean.contains(kw, ignoreCase = true)) {
                val idx = clean.indexOf(kw, ignoreCase = true)
                val extracted = clean.substring(idx).split('\n', ',', ';').firstOrNull()?.trim()?.take(28) ?: kw
                return "Ubiquiti $extracted"
            }
        }
        if (clean.contains("AirOS", ignoreCase = true)) {
            return "Ubiquiti AirOS"
        }
        return clean.take(30)
    }

    /**
     * Queries device SNMP information with intelligent auto-negotiation and fallback:
     * - Tests preferred SNMP version first (or v2c if none saved).
     * - If attempt 1 fails/times out, seamlessly tests alternative version (v1 <-> v2c).
     * - Persists and returns the working version for future polling.
     */
    suspend fun queryDeviceInfo(
        targetIp: String,
        community: String = "netvision",
        port: Int = 161,
        preferredVersion: String? = null,
        timeoutMs: Int = 1000
    ): Result<SnmpDeviceInfo> = withContext(Dispatchers.IO) {
        val totalStart = System.currentTimeMillis()

        // Determine sequence of SNMP versions to try:
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

            // 1. Probe sysDescr with current version
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
                // Succeeded! Query remaining standard OIDs with this same verified version
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

                // Query Wireless Station / Registration Table count (NOT interface count)
                // Try Ubiquiti first, then Dot11, then MikroTik WLAN count
                var clientCount: Int? = null
                val staRes = querySingleOid(
                    ip = targetIp,
                    community = community,
                    port = port,
                    oidBytes = OID_UBNT_STA_COUNT,
                    oidName = "ubntStaCount",
                    version = currentVersion,
                    timeoutMs = 600,
                    attemptNumber = attemptNumber
                )
                if (staRes.isSuccess && staRes.intValue != null) {
                    clientCount = staRes.intValue
                } else {
                    val dot11Res = querySingleOid(
                        ip = targetIp,
                        community = community,
                        port = port,
                        oidBytes = OID_DOT11_STA_COUNT,
                        oidName = "dot11StaCount",
                        version = currentVersion,
                        timeoutMs = 600,
                        attemptNumber = attemptNumber
                    )
                    if (dot11Res.isSuccess && dot11Res.intValue != null) {
                        clientCount = dot11Res.intValue
                    } else {
                        val mtxWlanRes = querySingleOid(
                            ip = targetIp,
                            community = community,
                            port = port,
                            oidBytes = OID_MIKROTIK_WLAN_COUNT,
                            oidName = "mtxWlanCount",
                            version = currentVersion,
                            timeoutMs = 600,
                            attemptNumber = attemptNumber
                        )
                        if (mtxWlanRes.isSuccess && mtxWlanRes.intValue != null) {
                            clientCount = mtxWlanRes.intValue
                        }
                    }
                }

                val totalLatency = System.currentTimeMillis() - totalStart
                val descr = descrRes.stringValue
                val name = nameRes.stringValue
                val uptimeTicks = uptimeRes.longValue
                val uptimeFormatted = uptimeTicks?.let { formatTimeTicks(it) }
                val ifCount = ifRes.intValue
                val modelName = extractModel(descr)

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
                        communityUsed = community,
                        latencyMs = totalLatency,
                        networkInterface = lastInterface,
                        snmpVersion = currentVersion,
                        negotiationSummary = negSummary
                    )
                )
            } else {
                lastErrorMessage = descrRes.errorMessage
                // Fallback will trigger next iteration
            }
        }

        // All attempts exhausted
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

                // Bind to active Android VPN network interface if available
                val vpnInfo = resolveVpnNetwork()
                ifaceName = vpnInfo.second
                if (vpnInfo.first != null) {
                    try {
                        vpnInfo.first!!.bindSocket(socket)
                        Log.d(TAG, "Socket bound to Network ($ifaceName) for target $ip")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not bind socket to network: ${e.message}")
                        if (ifaceName.contains("VPN", ignoreCase = true) || ifaceName.startsWith("tun")) {
                            throw IOException("VPN is active ($ifaceName) but failed to bind socket to VPN tunnel: ${e.message}")
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

    /**
     * Resolves the active VPN network interface using Android ConnectivityManager
     */
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

    /**
     * Builds a standard SNMP GetRequest packet strictly adhering to ASN.1 BER (RFC 1157, RFC 1905).
     *
     * Format:
     * SEQUENCE {
     *   INTEGER version (0 = v1, 1 = v2c)
     *   OCTET STRING community
     *   GetRequest-PDU [0xA0] {
     *     INTEGER request-id
     *     INTEGER error-status (0)
     *     INTEGER error-index (0)
     *     VarBindList SEQUENCE {
     *       VarBind SEQUENCE {
     *         OBJECT IDENTIFIER oid
     *         NULL
     *       }
     *     }
     *   }
     * }
     */
    fun buildGetRequest(reqId: Int, community: String, oidBytes: ByteArray, version: Int = 1): ByteArray {
        // 1. VarBind: SEQUENCE { ObjectName (0x06), ObjectSyntax (NULL 0x05 0x00) }
        val varBindInner = ByteArrayOutputStream()
        varBindInner.write(0x06) // OBJECT IDENTIFIER tag
        writeLength(varBindInner, oidBytes.size)
        varBindInner.write(oidBytes)
        varBindInner.write(byteArrayOf(0x05, 0x00)) // NULL tag and length 0

        val varBindBytes = varBindInner.toByteArray()
        val varBindSeq = ByteArrayOutputStream()
        varBindSeq.write(0x30) // VarBind SEQUENCE tag
        writeLength(varBindSeq, varBindBytes.size)
        varBindSeq.write(varBindBytes)

        // 2. VarBindList: SEQUENCE OF VarBind
        val varBindListBytes = varBindSeq.toByteArray()
        val varBindListSeq = ByteArrayOutputStream()
        varBindListSeq.write(0x30) // VarBindList SEQUENCE tag
        writeLength(varBindListSeq, varBindListBytes.size)
        varBindListSeq.write(varBindListBytes)

        // 3. GetRequest PDU: tag 0xA0
        val pduInner = ByteArrayOutputStream()
        writeBerInteger(pduInner, reqId)
        writeBerInteger(pduInner, 0) // error-status = 0
        writeBerInteger(pduInner, 0) // error-index = 0
        pduInner.write(varBindListSeq.toByteArray())

        val pduBytes = pduInner.toByteArray()
        val pduSeq = ByteArrayOutputStream()
        pduSeq.write(0xA0) // GetRequest-PDU tag
        writeLength(pduSeq, pduBytes.size)
        pduSeq.write(pduBytes)

        // 4. Message: SEQUENCE { version (INTEGER 0 or 1), community (OCTET STRING), PDU }
        val msgInner = ByteArrayOutputStream()
        writeBerInteger(msgInner, version) // 0 = SNMPv1, 1 = SNMPv2c
        val commBytes = community.toByteArray(Charsets.US_ASCII)
        msgInner.write(0x04) // OCTET STRING
        writeLength(msgInner, commBytes.size)
        msgInner.write(commBytes)
        msgInner.write(pduSeq.toByteArray())

        val msgBytes = msgInner.toByteArray()
        val finalSeq = ByteArrayOutputStream()
        finalSeq.write(0x30) // Top Message SEQUENCE
        writeLength(finalSeq, msgBytes.size)
        finalSeq.write(msgBytes)

        return finalSeq.toByteArray()
    }

    data class ParsedResponse(
        val isSuccess: Boolean,
        val versionDecoded: String = "v2c",
        val stringValue: String? = null,
        val longValue: Long? = null,
        val intValue: Int? = null,
        val summary: String,
        val errorDetail: String? = null
    )

    /**
     * Strict ASN.1 BER parser for SNMP GetResponse (0xA2) across SNMP v1 and v2c
     */
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

            // Request ID verification
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
                    2 -> "noSuchName (OID not supported on device)"
                    3 -> "badValue"
                    4 -> "readOnly"
                    5 -> "genErr"
                    16 -> "authorizationError (Check Community name)"
                    else -> "SNMP Error code $errorStatus"
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
            i = oidNext + oidLen

            // 11. Value Tag
            val valTag = data[i].toInt() and 0xFF
            i++
            val (vDataLen, vDataNext) = readLength(data, i)

            when (valTag) {
                0x04 -> { // OCTET STRING
                    val str = String(data, vDataNext, vDataLen, Charsets.UTF_8).trim()
                    return ParsedResponse(true, versionDecoded = versionDecoded, stringValue = str, summary = "String: ${str.take(40)}")
                }
                0x43 -> { // TimeTicks (1/100 sec)
                    var ticks = 0L
                    for (k in 0 until vDataLen) {
                        ticks = (ticks shl 8) or (data[vDataNext + k].toLong() and 0xFFL)
                    }
                    return ParsedResponse(true, versionDecoded = versionDecoded, longValue = ticks, summary = "TimeTicks: $ticks (${formatTimeTicks(ticks)})")
                }
                0x02 -> { // INTEGER
                    var num = 0
                    for (k in 0 until vDataLen) {
                        num = (num shl 8) or (data[vDataNext + k].toInt() and 0xFF)
                    }
                    return ParsedResponse(true, versionDecoded = versionDecoded, intValue = num, summary = "Integer: $num")
                }
                0x41, 0x42 -> { // Counter32 / Gauge32
                    var num = 0L
                    for (k in 0 until vDataLen) {
                        num = (num shl 8) or (data[vDataNext + k].toLong() and 0xFFL)
                    }
                    return ParsedResponse(true, versionDecoded = versionDecoded, longValue = num, summary = "Counter/Gauge: $num")
                }
                0x40 -> { // IpAddress (4 bytes)
                    if (vDataLen == 4) {
                        val ip = "${data[vDataNext].toInt() and 0xFF}.${data[vDataNext + 1].toInt() and 0xFF}.${data[vDataNext + 2].toInt() and 0xFF}.${data[vDataNext + 3].toInt() and 0xFF}"
                        return ParsedResponse(true, versionDecoded = versionDecoded, stringValue = ip, summary = "IpAddress: $ip")
                    } else {
                        return ParsedResponse(true, versionDecoded = versionDecoded, stringValue = "IpAddress", summary = "IpAddress")
                    }
                }
                0x80 -> return ParsedResponse(false, versionDecoded = versionDecoded, summary = "noSuchObject", errorDetail = "noSuchObject (OID not found in MIB)")
                0x81 -> return ParsedResponse(false, versionDecoded = versionDecoded, summary = "noSuchInstance", errorDetail = "noSuchInstance (Instance does not exist)")
                0x82 -> return ParsedResponse(false, versionDecoded = versionDecoded, summary = "endOfMibView", errorDetail = "endOfMibView")
                else -> {
                    val rawHex = data.sliceArray(vDataNext until (vDataNext + vDataLen)).joinToString("") { "%02x".format(it) }
                    return ParsedResponse(true, versionDecoded = versionDecoded, stringValue = rawHex, summary = "Raw Tag 0x${Integer.toHexString(valTag)}: $rawHex")
                }
            }
        } catch (e: Exception) {
            return ParsedResponse(false, summary = "Parse Failure", errorDetail = "Malformed ASN.1 payload: ${e.message}")
        }
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
