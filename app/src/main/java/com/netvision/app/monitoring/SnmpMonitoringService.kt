package com.netvision.app.monitoring

import com.netvision.app.models.NetworkMetrics
import kotlinx.coroutines.flow.Flow

/**
 * Service contract for Simple Network Management Protocol (SNMP) polling.
 * Supports SNMP v2c / v3 standard OIDs:
 * - sysUpTime (1.3.6.1.2.1.1.3.0)
 * - hrProcessorLoad (1.3.6.1.2.1.25.3.3.1.2)
 * - ifInOctets / ifOutOctets for interface bandwidth
 * - Vendor specific MIBs (UBNT-MIB, MIKROTIK-MIB)
 */
interface SnmpMonitoringService {
    enum class SnmpVersion {
        V2C,
        V3
    }

    data class SnmpTarget(
        val ipAddress: String,
        val port: Int = 161,
        val communityString: String = "public",
        val version: SnmpVersion = SnmpVersion.V2C
    )

    /**
     * Polls metrics for a single device over SNMP.
     */
    suspend fun pollDeviceMetrics(target: SnmpTarget): Result<NetworkMetrics>

    /**
     * Creates a periodic live telemetry stream for continuous monitoring.
     */
    fun monitorLiveStream(target: SnmpTarget, intervalMs: Long = 5000): Flow<NetworkMetrics>

    /**
     * Tests SNMP community string connectivity.
     */
    suspend fun testSnmpConnectivity(target: SnmpTarget): Boolean
}
