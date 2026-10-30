package com.netvision.app.network

import com.netvision.app.models.NetworkDevice
import com.netvision.app.models.NetworkRange
import kotlinx.coroutines.flow.Flow

/**
 * Interface for discovering active network equipment across LAN/VLAN subnets.
 * Prepares the architectural contract for ARP scanning, ICMP ping sweep,
 * and multicast broadcast discovery.
 */
interface NetworkDiscoveryService {
    data class ScanProgress(
        val totalHosts: Int,
        val scannedHosts: Int,
        val percentage: Int,
        val currentIp: String,
        val isCompleted: Boolean,
        val newlyFoundDevice: NetworkDevice? = null
    )

    /**
     * Initiates a discovery scan across one or more CIDR ranges.
     * Emits incremental scan progress and discovered devices.
     */
    fun startScan(ranges: List<NetworkRange>): Flow<ScanProgress>

    /**
     * Halts an ongoing network scan immediately.
     */
    suspend fun stopScan()

    /**
     * Checks whether a scan is currently active.
     */
    fun isScanning(): Boolean
}
