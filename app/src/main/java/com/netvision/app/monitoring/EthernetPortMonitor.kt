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
    val speedMbps: Long? = null,
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
    // read() only adds interfaces whose IF-MIB ifType is ethernetCsmacd(6).
    // This includes physical Ethernet and SFP/SFP+ ports regardless of link state.
    val totalCount: Int get() = ports.size
}

object EthernetPortMonitor {
    // ifDescr is exposed by both RouterOS and the tested AirOS SNMP v1 device.
    private const val IF_NAME = "1.3.6.1.2.1.2.2.1.2"
    private const val IF_TYPE = "1.3.6.1.2.1.2.2.1.3"
    private const val ETHERNET_CSMACD = 6L
    private const val IF_SPEED = "1.3.6.1.2.1.2.2.1.5"
    private const val IF_HIGH_SPEED = "1.3.6.1.2.1.31.1.1.1.15"
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
        // Walk ifName to discover real ifIndex values; indexes are not assumed
        // to be contiguous and are reused for every subsequent IF-MIB lookup.
        val rows = mutableListOf<EthernetPortSnapshot>()
        var cursor = IF_NAME
        val seenIndexes = mutableSetOf<Int>()
        // SNMP is UDP: a single lost response must not truncate the physical-port
        // inventory. Retry the same cursor before declaring the walk complete.
        val walkTimeoutMs = maxOf(timeoutMs, 1500)
        for (step in 0 until 128) {
            val next = retrySnmpRead(attempts = 3) {
                SnmpV2Client.querySingleOid(
                    ip, community, port, SnmpV2Client.encodeOidString(cursor),
                    "ifName.walk", versionToUse, walkTimeoutMs,
                    useGetNext = true, preserveOctetString = true
                )
            }
            if (!next.isSuccess) break
            val responseOid = next.responseOid ?: break
            if (!responseOid.startsWith("$IF_NAME.")) break
            val index = responseOid.removePrefix("$IF_NAME.").toIntOrNull() ?: break
            if (!seenIndexes.add(index)) break
            cursor = responseOid
            val name = next.stringValue?.trim().orEmpty()
            if (name.isBlank()) continue

            // RouterOS exposes physical Ethernet and SFP/SFP+ ports as
            // ethernetCsmacd(6). Virtual interfaces (bridge, VLAN, PPP, etc.)
            // have different ifType values and are deliberately excluded.
            val typeResult = retrySnmpRead(attempts = 3) {
                get(ip, community, port, "$IF_TYPE.$index", "ifType.$index", versionToUse, walkTimeoutMs)
            }
            val interfaceType = typeResult.takeIf { it.isSuccess }?.longValue
            if (interfaceType != ETHERNET_CSMACD) continue

            // Keep the per-port telemetry reads together, but avoid launching
            // requests for every interface at the same time.
            val values = coroutineScope {
                listOf(
                    async { get(ip, community, port, "$IF_ADMIN.$index", "ifAdminStatus.$index", versionToUse, timeoutMs).longValue?.toInt() },
                    async { get(ip, community, port, "$IF_OPER.$index", "ifOperStatus.$index", versionToUse, timeoutMs).longValue?.toInt() },
                    async { get(ip, community, port, "$IF_SPEED.$index", "ifSpeed.$index", versionToUse, timeoutMs).longValue },
                    async { get(ip, community, port, "$IF_HIGH_SPEED.$index", "ifHighSpeed.$index", versionToUse, timeoutMs).longValue },
                    async { get(ip, community, port, "$IF_HC_IN.$index", "ifHCInOctets.$index", versionToUse, timeoutMs).longValue },
                    async { get(ip, community, port, "$IF_HC_OUT.$index", "ifHCOutOctets.$index", versionToUse, timeoutMs).longValue },
                    async { get(ip, community, port, "$MIKROTIK_LINK_DOWNS.$index", "linkDowns.$index", versionToUse, timeoutMs).longValue }
                ).awaitAll()
            }
            rows += EthernetPortSnapshot(
                index, name,
                values[0] as Int?, values[1] as Int?, values[2] as Long?,
                speedMbps = ((values[2] as Long?)?.takeIf { it > 0L && it < 4_294_967_295L }?.div(1_000_000L)
                    ?: (values[3] as Long?)?.takeIf { it > 0L }),
                rxOctets = values[4] as Long?,
                txOctets = values[5] as Long?,
                linkDowns = values[6] as Long?
            )
        }
        EthernetMonitoringSnapshot(rows.sortedBy { it.index })
    }

    private inline fun retrySnmpRead(
        attempts: Int,
        request: () -> SnmpV2Client.SingleOidResult
    ): SnmpV2Client.SingleOidResult {
        var lastResult: SnmpV2Client.SingleOidResult? = null
        repeat(attempts) {
            val result = request()
            lastResult = result
            if (result.isSuccess) return result
        }
        return lastResult ?: SnmpV2Client.SingleOidResult(
            isSuccess = false,
            errorMessage = "SNMP request failed after $attempts attempts"
        )
    }

    private fun get(ip: String, community: String, port: Int, oid: String, label: String, version: String, timeout: Int): SnmpV2Client.SingleOidResult =
        SnmpV2Client.querySingleOid(ip, community, port, SnmpV2Client.encodeOidString(oid), label, version, timeout)
}
