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
    val ifCount: Int? = null,
    val errorMessage: String? = null,
    val communityUsed: String = "netvision",
    val latencyMs: Long = 0,
    val networkInterface: String = "VPN (Auto)"
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
    val exceptionDetail: String? = null
)

object SnmpV2Client {

    private const val TAG = "NetVision_SNMP"
    private val requestIdCounter = AtomicInteger(1000)

    // In-memory circular diagnostic log buffer (retains last 30 transactions)
    private val diagnosticHistory = ConcurrentLinkedQueue<SnmpDiagnosticRecord>()

    fun getDiagnosticHistory(): List<SnmpDiagnosticRecord> = diagnosticHistory.toList()

    fun maskCommunity(community: String): String {
        if (community.length <= 2) return "**"
        return "${community.first()}${"*".repeat(community.length - 2)}${community.last()}"
    }

    // Standard OID byte sequences in ASN.1 BER:
    // 1.3.6.1.2.1.1.1.0 (sysDescr)
    val OID_SYS_DESCR = byteArrayOf(0x2b, 0x06, 0x01, 0x02, 0x01, 0x01, 0x01, 0x00)
    // 1.3.6.1.2.1.1.3.0 (sysUpTime)
    val OID_SYS_UPTIME = byteArrayOf(0x2b, 0x06, 0x01, 0x02, 0x01, 0x01, 0x03, 0x00)
    // 1.3.6.1.2.1.1.5.0 (sysName)
    val OID_SYS_NAME = byteArrayOf(0x2b, 0x06, 0x01, 0x02, 0x01, 0x01, 0x05, 0x00)
    // 1.3.6.1.2.1.2.1.0 (ifNumber)
    val OID_IF_NUMBER = byteArrayOf(0x2b, 0x06, 0x01, 0x02, 0x01, 0x02, 0x01, 0x00)

    suspend fun queryDeviceInfo(
        targetIp: String,
        community: String = "netvision",
        port: Int = 161,
        timeoutMs: Int = 1500
    ): Result<SnmpDeviceInfo> = withContext(Dispatchers.IO) {
        val totalStart = System.currentTimeMillis()

        // 1. Query sysDescr
        val descrRes = querySingleOid(targetIp, community, port, OID_SYS_DESCR, "sysDescr", timeoutMs)

        // 2. Query sysName
        val nameRes = querySingleOid(targetIp, community, port, OID_SYS_NAME, "sysName", timeoutMs)

        // 3. Query sysUpTime
        val uptimeRes = querySingleOid(targetIp, community, port, OID_SYS_UPTIME, "sysUpTime", timeoutMs)

        // 4. Query ifNumber
        val ifRes = querySingleOid(targetIp, community, port, OID_IF_NUMBER, "ifNumber", timeoutMs)

        val totalLatency = System.currentTimeMillis() - totalStart
        val ifaceName = descrRes.networkInterface

        val anySuccess = descrRes.isSuccess || nameRes.isSuccess || uptimeRes.isSuccess || ifRes.isSuccess

        if (!anySuccess) {
            val primaryError = descrRes.errorMessage
                ?: nameRes.errorMessage
                ?: "Timeout - No SNMP response from $targetIp:$port (Community: '${maskCommunity(community)}')"
            return@withContext Result.success(
                SnmpDeviceInfo(
                    isSuccess = false,
                    errorMessage = primaryError,
                    communityUsed = community,
                    latencyMs = totalLatency,
                    networkInterface = ifaceName
                )
            )
        }

        val descr = descrRes.stringValue
        val name = nameRes.stringValue
        val uptimeTicks = uptimeRes.longValue
        val uptimeFormatted = uptimeTicks?.let { formatTimeTicks(it) }
        val ifCount = ifRes.intValue

        Result.success(
            SnmpDeviceInfo(
                isSuccess = true,
                sysDescr = descr,
                sysName = name,
                uptimeHundredths = uptimeTicks,
                uptimeFormatted = uptimeFormatted,
                ifCount = ifCount,
                communityUsed = community,
                latencyMs = totalLatency,
                networkInterface = ifaceName
            )
        )
    }

    private data class SingleOidResult(
        val isSuccess: Boolean,
        val stringValue: String? = null,
        val longValue: Long? = null,
        val intValue: Int? = null,
        val errorMessage: String? = null,
        val networkInterface: String = "Default",
        val latencyMs: Long = 0
    )

    private fun querySingleOid(
        ip: String,
        community: String,
        port: Int,
        oidBytes: ByteArray,
        oidName: String,
        timeoutMs: Int
    ): SingleOidResult {
        val reqId = requestIdCounter.incrementAndGet()
        val packetBytes = buildGetRequest(reqId, community, oidBytes)
        val maskedComm = maskCommunity(community)

        var timeSent = 0L
        var timeReceived = 0L
        var bytesReceived = 0
        var ifaceName = "Default"
        var exceptionMsg: String? = null

        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs

                // Bind to active Android VPN network if present
                val vpnInfo = resolveVpnNetwork()
                ifaceName = vpnInfo.second
                if (vpnInfo.first != null) {
                    try {
                        vpnInfo.first!!.bindSocket(socket)
                        Log.d(TAG, "Socket bound to VPN Network ($ifaceName) for target $ip")
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not bind socket to VPN network: ${e.message}")
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
                    version = "v2c",
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
                    exceptionDetail = parsed.errorDetail
                )
                logDiagnostic(record)

                if (parsed.isSuccess) {
                    return SingleOidResult(
                        isSuccess = true,
                        stringValue = parsed.stringValue,
                        longValue = parsed.longValue,
                        intValue = parsed.intValue,
                        networkInterface = ifaceName,
                        latencyMs = latency
                    )
                } else {
                    return SingleOidResult(
                        isSuccess = false,
                        errorMessage = parsed.errorDetail ?: "SNMP response error",
                        networkInterface = ifaceName,
                        latencyMs = latency
                    )
                }
            }
        } catch (e: SocketTimeoutException) {
            val latency = if (timeSent > 0) System.currentTimeMillis() - timeSent else timeoutMs.toLong()
            val msg = "Timeout - No response on UDP $port via $ifaceName (Community '$maskedComm')"
            logDiagnostic(
                SnmpDiagnosticRecord(
                    targetIp = ip,
                    port = port,
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
                    exceptionDetail = e.javaClass.simpleName + ": " + (e.message ?: "SocketTimeout")
                )
            )
            return SingleOidResult(isSuccess = false, errorMessage = msg, networkInterface = ifaceName, latencyMs = latency)
        } catch (e: Exception) {
            val latency = if (timeSent > 0) System.currentTimeMillis() - timeSent else 0L
            val msg = "${e.javaClass.simpleName}: ${e.message}"
            logDiagnostic(
                SnmpDiagnosticRecord(
                    targetIp = ip,
                    port = port,
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
                    exceptionDetail = msg
                )
            )
            return SingleOidResult(isSuccess = false, errorMessage = msg, networkInterface = ifaceName, latencyMs = latency)
        }
    }

    private fun logDiagnostic(entry: SnmpDiagnosticRecord) {
        diagnosticHistory.add(entry)
        while (diagnosticHistory.size > 30) {
            diagnosticHistory.poll()
        }
        Log.i(TAG, "SNMP [${entry.oidName}] to ${entry.targetIp}:${entry.port} via ${entry.networkInterface} | Success: ${entry.isSuccess} | Latency: ${entry.latencyMs}ms | ${entry.resultSummary}")
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
                    val name = linkProps?.interfaceName ?: "VPN (tun)"
                    return Pair(network, name)
                }
            }

            // Fallback to active network
            val activeNet = cm.activeNetwork
            val activeProps = cm.getLinkProperties(activeNet)
            val name = activeProps?.interfaceName ?: "Default"
            return Pair(activeNet, name)
        } catch (e: Exception) {
            return Pair(null, "Default")
        }
    }

    /**
     * Builds a standard SNMPv2c GetRequest packet strictly adhering to ASN.1 BER (RFC 1157, RFC 1905).
     *
     * Format:
     * SEQUENCE {
     *   INTEGER version (1 = v2c)
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
    fun buildGetRequest(reqId: Int, community: String, oidBytes: ByteArray): ByteArray {
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

        // 4. Message: SEQUENCE { version (INTEGER 1), community (OCTET STRING), PDU }
        val msgInner = ByteArrayOutputStream()
        writeBerInteger(msgInner, 1) // version 1 = SNMPv2c
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

    private data class ParsedResponse(
        val isSuccess: Boolean,
        val stringValue: String? = null,
        val longValue: Long? = null,
        val intValue: Int? = null,
        val summary: String,
        val errorDetail: String? = null
    )

    /**
     * Strict ASN.1 BER parser for SNMP GetResponse (0xA2)
     */
    private fun parseSnmpResponse(data: ByteArray, expectedReqId: Int, expectedCommunity: String): ParsedResponse {
        try {
            var i = 0

            // 1. Top Sequence (0x30)
            if (data[i] != 0x30.toByte()) {
                return ParsedResponse(false, summary = "Invalid ASN.1", errorDetail = "Expected SEQUENCE 0x30, got ${data[i]}")
            }
            i++
            val (msgLen, nextI) = readLength(data, i)
            i = nextI

            // 2. Version (INTEGER 0x02)
            if (data[i] != 0x02.toByte()) {
                return ParsedResponse(false, summary = "Invalid Version Tag", errorDetail = "Expected INTEGER 0x02, got ${data[i]}")
            }
            i++
            val (vLen, vNext) = readLength(data, i)
            i = vNext + vLen

            // 3. Community (OCTET STRING 0x04)
            if (data[i] != 0x04.toByte()) {
                return ParsedResponse(false, summary = "Invalid Community Tag", errorDetail = "Expected OCTET STRING 0x04, got ${data[i]}")
            }
            i++
            val (cLen, cNext) = readLength(data, i)
            val commStr = String(data, cNext, cLen, Charsets.US_ASCII)
            i = cNext + cLen

            // 4. Response PDU (0xA2)
            val pduTag = data[i].toInt() and 0xFF
            if (pduTag != 0xA2) {
                return ParsedResponse(false, summary = "Invalid PDU", errorDetail = "Expected GetResponse PDU 0xA2, got 0x${Integer.toHexString(pduTag)}")
            }
            i++
            val (pduLen, pduNext) = readLength(data, i)
            i = pduNext

            // 5. Request ID (INTEGER 0x02)
            if (data[i] != 0x02.toByte()) {
                return ParsedResponse(false, summary = "Invalid ReqId Tag", errorDetail = "Expected INTEGER for reqId")
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
                    summary = "Request ID Mismatch",
                    errorDetail = "Response reqId ($responseReqId) != Sent reqId ($expectedReqId)"
                )
            }

            // 6. Error Status (INTEGER 0x02)
            if (data[i] != 0x02.toByte()) {
                return ParsedResponse(false, summary = "Invalid ErrorStatus", errorDetail = "Expected INTEGER for errorStatus")
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
                return ParsedResponse(false, summary = "Invalid ErrorIndex", errorDetail = "Expected INTEGER for errorIndex")
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
                return ParsedResponse(false, summary = "SNMP Error: $errName", errorDetail = errName)
            }

            // 8. VarBindList Sequence (0x30)
            if (data[i] != 0x30.toByte()) {
                return ParsedResponse(false, summary = "Invalid VarBindList", errorDetail = "Expected SEQUENCE 0x30 for VarBindList")
            }
            i++
            val (vbListLen, vbListNext) = readLength(data, i)
            i = vbListNext

            // 9. First VarBind Sequence (0x30)
            if (data[i] != 0x30.toByte()) {
                return ParsedResponse(false, summary = "Invalid VarBind", errorDetail = "Expected SEQUENCE 0x30 for VarBind")
            }
            i++
            val (vbLen, vbNext) = readLength(data, i)
            i = vbNext

            // 10. OID tag (0x06)
            if (data[i] != 0x06.toByte()) {
                return ParsedResponse(false, summary = "Invalid OID Tag", errorDetail = "Expected OBJECT IDENTIFIER 0x06")
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
                    return ParsedResponse(true, stringValue = str, summary = "String: ${str.take(40)}")
                }
                0x43 -> { // TimeTicks (1/100 sec)
                    var ticks = 0L
                    for (k in 0 until vDataLen) {
                        ticks = (ticks shl 8) or (data[vDataNext + k].toLong() and 0xFFL)
                    }
                    return ParsedResponse(true, longValue = ticks, summary = "TimeTicks: $ticks (${formatTimeTicks(ticks)})")
                }
                0x02 -> { // INTEGER
                    var num = 0
                    for (k in 0 until vDataLen) {
                        num = (num shl 8) or (data[vDataNext + k].toInt() and 0xFF)
                    }
                    return ParsedResponse(true, intValue = num, summary = "Integer: $num")
                }
                0x41, 0x42 -> { // Counter32 / Gauge32
                    var num = 0L
                    for (k in 0 until vDataLen) {
                        num = (num shl 8) or (data[vDataNext + k].toLong() and 0xFFL)
                    }
                    return ParsedResponse(true, longValue = num, summary = "Counter/Gauge: $num")
                }
                0x80 -> return ParsedResponse(false, summary = "noSuchObject", errorDetail = "noSuchObject (OID not found in MIB)")
                0x81 -> return ParsedResponse(false, summary = "noSuchInstance", errorDetail = "noSuchInstance (Instance does not exist)")
                0x82 -> return ParsedResponse(false, summary = "endOfMibView", errorDetail = "endOfMibView")
                else -> {
                    val rawHex = data.sliceArray(vDataNext until (vDataNext + vDataLen)).joinToString("") { "%02x".format(it) }
                    return ParsedResponse(true, stringValue = rawHex, summary = "Raw Tag 0x${Integer.toHexString(valTag)}: $rawHex")
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

    private fun formatTimeTicks(ticks: Long): String {
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
