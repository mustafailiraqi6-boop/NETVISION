package com.netvision.app.monitoring

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Reads IF-MIB counters using the same VPN-bound SNMP client used by the app.
 * Unsupported values remain null; zero is never fabricated for missing OIDs.
 */
data class EthernetPortSnapshot(
    val index: Int,
    val name: String,
    val adminStatus: Int?,
    val operStatus: Int?,
    val speedBps: Long?,
    val rxOctets: Long?,
    val txOctets: Long?,
    val rxMbps: Double? = null,
    val txMbps: Double? = null,
    val linkDowns: Long? = null
)

data class EthernetMonitoringSnapshot(
    val ports: List<EthernetPortSnapshot>,
    val sampledAt: Long = System.currentTimeMillis(),
    val supported: Boolean = true,
    val error: String? = null
) {
    val connectedCount: Int get() = ports.count { it.operStatus == 1 }
    val totalCount: Int get() = ports.size
}

object EthernetPortMonitor {
    private const val IF_NUMBER = "1.3.6.1.2.1.2.1.0"
    private const val IF_NAME = "1.3.6.1.2.1.31.1.1.1.1"
    private const val IF_SPEED = "1.3.6.1.2.1.2.2.1.5"
    private const val IF_ADMIN = "1.3.6.1.2.1.2.2.1.7"
    private const val IF_OPER = "1.3.6.1.2.1.2.2.1.8"
    private const val IF_HC_IN = "1.3.6.1.2.1.31.1.1.1.6"
    private const val IF_HC_OUT = "1.3.6.1.2.1.31.1.1.1.10"
    private const val MIKROTIK_LINK_DOWNS = "1.3.6.1.4.1.14988.1.1.14.1.1.90"

    suspend fun read(
        ip: String,
        community: String,
        port: Int,
        version: String?,
        timeoutMs: Int = 900
    ): EthernetMonitoringSnapshot = withContext(Dispatchers.IO) {
        val versionToUse = if (version.equals("v1", true)) "v1" else "v2c"
        val countResult = get(ip, community, port, IF_NUMBER, "ifNumber", versionToUse, timeoutMs)
        val count = countResult.intValue ?: countResult.longValue?.toInt()
            ?: return@withContext EthernetMonitoringSnapshot(emptyList(), supported = false, error = "IF-MIB غير مدعوم")
        if (count <= 0) return@withContext EthernetMonitoringSnapshot(emptyList(), supported = false, error = "لا توجد واجهات")

        // Read interface names sequentially. Large parallel SNMP bursts can cause
        // MikroTik devices to drop replies, producing an incomplete port list.
        val rows = mutableListOf<EthernetPortSnapshot>()
        for (index in 1..count.coerceAtMost(256)) {
            val name = get(ip, community, port, "$IF_NAME.$index", "ifName.$index", versionToUse, timeoutMs).stringValue?.trim()
                ?: get(ip, community, port, "1.3.6.1.2.1.2.2.1.2.$index", "ifDescr.$index", versionToUse, timeoutMs).stringValue?.trim()
                ?: continue
            val lower = name.lowercase()
            if (!(lower.startsWith("ether") || lower.startsWith("sfp") || lower.startsWith("combo"))) continue

            // Keep the six per-port telemetry reads together, but avoid launching
            // requests for every interface at the same time.
            val values = coroutineScope {
                listOf(
                    async { get(ip, community, port, "$IF_ADMIN.$index", "ifAdminStatus.$index", versionToUse, timeoutMs).longValue?.toInt() },
                    async { get(ip, community, port, "$IF_OPER.$index", "ifOperStatus.$index", versionToUse, timeoutMs).longValue?.toInt() },
                    async { get(ip, community, port, "$IF_SPEED.$index", "ifSpeed.$index", versionToUse, timeoutMs).longValue },
                    async { get(ip, community, port, "$IF_HC_IN.$index", "ifHCInOctets.$index", versionToUse, timeoutMs).longValue },
                    async { get(ip, community, port, "$IF_HC_OUT.$index", "ifHCOutOctets.$index", versionToUse, timeoutMs).longValue },
                    async { get(ip, community, port, "$MIKROTIK_LINK_DOWNS.$index", "linkDowns.$index", versionToUse, timeoutMs).longValue }
                ).awaitAll()
            }
            rows += EthernetPortSnapshot(
                index, name,
                values[0] as Int?, values[1] as Int?, values[2] as Long?,
                values[3] as Long?, values[4] as Long?,
                linkDowns = values[5] as Long?
            )
        }
        EthernetMonitoringSnapshot(rows.sortedBy { it.index })
    }

    private fun get(ip: String, community: String, port: Int, oid: String, label: String, version: String, timeout: Int): SnmpV2Client.SingleOidResult =
        SnmpV2Client.querySingleOid(ip, community, port, SnmpV2Client.encodeOidString(oid), label, version, timeout)
}
