package com.netvision.app.monitoring

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicInteger

data class SnmpDeviceInfo(
    val isSuccess: Boolean,
    val sysDescr: String? = null,
    val sysName: String? = null,
    val uptimeHundredths: Long? = null,
    val uptimeFormatted: String? = null,
    val errorMessage: String? = null,
    val communityUsed: String = "netvision"
)

object SnmpV2Client {

    private val requestIdCounter = AtomicInteger(1000)

    // Pre-encoded OIDs in ASN.1 BER:
    // 1.3.6.1.2.1.1.1.0 (sysDescr) -> 43 06 01 02 01 01 01 00
    private val OID_SYS_DESCR = byteArrayOf(0x2b, 0x06, 0x01, 0x02, 0x01, 0x01, 0x01, 0x00)
    // 1.3.6.1.2.1.1.3.0 (sysUpTime) -> 43 06 01 02 01 01 03 00
    private val OID_SYS_UPTIME = byteArrayOf(0x2b, 0x06, 0x01, 0x02, 0x01, 0x01, 0x03, 0x00)
    // 1.3.6.1.2.1.1.5.0 (sysName) -> 43 06 01 02 01 01 05 00
    private val OID_SYS_NAME = byteArrayOf(0x2b, 0x06, 0x01, 0x02, 0x01, 0x01, 0x05, 0x00)

    suspend fun queryDeviceInfo(
        targetIp: String,
        community: String = "netvision",
        port: Int = 161,
        timeoutMs: Int = 1200
    ): Result<SnmpDeviceInfo> = withContext(Dispatchers.IO) {
        try {
            val descr = queryOidString(targetIp, community, port, OID_SYS_DESCR, timeoutMs)
            val name = queryOidString(targetIp, community, port, OID_SYS_NAME, timeoutMs)
            val uptime = queryOidTimeTicks(targetIp, community, port, OID_SYS_UPTIME, timeoutMs)

            if (descr == null && name == null && uptime == null) {
                val failureInfo = SnmpDeviceInfo(
                    isSuccess = false,
                    errorMessage = "Timeout - No SNMP response on UDP $port (Community: '$community')",
                    communityUsed = community
                )
                return@withContext Result.success(failureInfo)
            }

            val formattedUptime = uptime?.let { formatTimeTicks(it) }

            val successInfo = SnmpDeviceInfo(
                isSuccess = true,
                sysDescr = descr,
                sysName = name,
                uptimeHundredths = uptime,
                uptimeFormatted = formattedUptime,
                communityUsed = community
            )

            Result.success(successInfo)
        } catch (e: SocketTimeoutException) {
            Result.success(
                SnmpDeviceInfo(
                    isSuccess = false,
                    errorMessage = "Timeout - No response on UDP $port (Community '$community')",
                    communityUsed = community
                )
            )
        } catch (e: Exception) {
            Result.success(
                SnmpDeviceInfo(
                    isSuccess = false,
                    errorMessage = e.localizedMessage ?: "SNMP query failed on UDP $port (Community '$community')",
                    communityUsed = community
                )
            )
        }
    }

    private fun queryOidString(
        ip: String,
        community: String,
        port: Int,
        oidBytes: ByteArray,
        timeoutMs: Int
    ): String? {
        val response = sendSnmpGet(ip, community, port, oidBytes, timeoutMs) ?: return null
        return extractStringValue(response, community)
    }

    private fun queryOidTimeTicks(
        ip: String,
        community: String,
        port: Int,
        oidBytes: ByteArray,
        timeoutMs: Int
    ): Long? {
        val response = sendSnmpGet(ip, community, port, oidBytes, timeoutMs) ?: return null
        return extractTimeTicksValue(response)
    }

    private fun sendSnmpGet(
        ip: String,
        community: String,
        port: Int,
        oidBytes: ByteArray,
        timeoutMs: Int
    ): ByteArray? {
        val reqId = requestIdCounter.incrementAndGet()
        val packetBytes = buildGetRequest(reqId, community, oidBytes)

        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs
            val address = InetAddress.getByName(ip)
            val packet = DatagramPacket(packetBytes, packetBytes.size, address, port)
            socket.send(packet)

            val buffer = ByteArray(1500)
            val receivePacket = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(receivePacket)
                return buffer.copyOf(receivePacket.length)
            } catch (e: Exception) {
                return null
            }
        }
    }

    private fun buildGetRequest(reqId: Int, community: String, oidBytes: ByteArray): ByteArray {
        val pdu = ByteArrayOutputStream()
        // Request ID (Integer)
        writeBerInteger(pdu, reqId)
        // Error status (0)
        pdu.write(byteArrayOf(0x02, 0x01, 0x00))
        // Error index (0)
        pdu.write(byteArrayOf(0x02, 0x01, 0x00))

        // VarBind: SEQUENCE { OID, NULL }
        val varBind = ByteArrayOutputStream()
        varBind.write(0x06) // OID tag
        writeLength(varBind, oidBytes.size)
        varBind.write(oidBytes)
        varBind.write(byteArrayOf(0x05, 0x00)) // NULL tag + length 0

        val varBindList = ByteArrayOutputStream()
        varBindList.write(0x30)
        writeLength(varBindList, varBind.size())
        varBindList.write(varBind.toByteArray())

        pdu.write(varBindList.toByteArray())

        val pduBytes = pdu.toByteArray()
        val getRequestPdu = ByteArrayOutputStream()
        getRequestPdu.write(0xA0) // GetRequest PDU tag
        writeLength(getRequestPdu, pduBytes.size)
        getRequestPdu.write(pduBytes)

        // Top level SEQUENCE: { version (v2c = 1), community, GetRequest-PDU }
        val topSeq = ByteArrayOutputStream()
        // Version 1 (SNMPv2c)
        topSeq.write(byteArrayOf(0x02, 0x01, 0x01))
        // Community string
        val commBytes = community.toByteArray(Charsets.US_ASCII)
        topSeq.write(0x04)
        writeLength(topSeq, commBytes.size)
        topSeq.write(commBytes)
        // PDU
        topSeq.write(getRequestPdu.toByteArray())

        val finalBytes = ByteArrayOutputStream()
        finalBytes.write(0x30)
        writeLength(finalBytes, topSeq.size())
        finalBytes.write(topSeq.toByteArray())

        return finalBytes.toByteArray()
    }

    private fun writeBerInteger(out: ByteArrayOutputStream, value: Int) {
        out.write(0x02)
        out.write(4)
        out.write((value shr 24) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write(value and 0xFF)
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

    private fun extractStringValue(data: ByteArray, community: String): String? {
        try {
            var i = 0
            while (i < data.size - 2) {
                // Find OCTET STRING tag (0x04)
                if (data[i] == 0x04.toByte()) {
                    var len = data[i + 1].toInt() and 0xFF
                    var offset = i + 2
                    if (len and 0x80 != 0) {
                        val numOctets = len and 0x7F
                        len = 0
                        for (k in 0 until numOctets) {
                            len = (len shl 8) or (data[offset + k].toInt() and 0xFF)
                        }
                        offset += numOctets
                    }
                    if (offset + len <= data.size && len > 0) {
                        val str = String(data, offset, len, Charsets.UTF_8).trim()
                        if (str.isNotEmpty() && str.all { it >= ' ' || it == '\n' || it == '\r' || it == '\t' }) {
                            // Filter out exact community string echo
                            if (str != community) {
                                return str
                            }
                        }
                    }
                }
                i++
            }
        } catch (e: Exception) {}
        return null
    }

    private fun extractTimeTicksValue(data: ByteArray): Long? {
        try {
            var i = 0
            while (i < data.size - 2) {
                // TimeTicks tag is 0x43 (Application 3)
                if (data[i] == 0x43.toByte()) {
                    val len = data[i + 1].toInt() and 0xFF
                    val offset = i + 2
                    if (offset + len <= data.size && len in 1..8) {
                        var ticks = 0L
                        for (k in 0 until len) {
                            ticks = (ticks shl 8) or (data[offset + k].toLong() and 0xFFL)
                        }
                        return ticks
                    }
                }
                i++
            }
        } catch (e: Exception) {}
        return null
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
