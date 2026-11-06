package com.netvision.app.network

import com.netvision.app.models.NetworkDevice
import kotlinx.coroutines.flow.Flow

/**
 * Service for discovery and telemetry of MikroTik RouterOS and SwOS devices.
 * Implements MikroTik Neighbor Discovery Protocol (MNDP) over UDP port 5678,
 * RouterOS API, and Winbox discovery packet parsing.
 */
interface MikroTikDeviceService {
    val mndpPort: Int get() = 5678

    /**
     * Listens for MNDP (MikroTik Neighbor Discovery Protocol) UDP broadcasts.
     */
    fun listenForMndpAnnouncements(): Flow<NetworkDevice>

    /**
     * Communicates with RouterOS devices via RouterOS REST API (v7.1+) or RouterOS API (port 8728).
     */
    suspend fun fetchSystemResources(ipAddress: String, credentials: Pair<String, String>?,
        apiPort: Int = 8728): Result<NetworkDevice>
}
