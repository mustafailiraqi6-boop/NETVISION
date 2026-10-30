package com.netvision.app.network

import com.netvision.app.models.NetworkDevice
import kotlinx.coroutines.flow.Flow

/**
 * Service for discovery and interaction with Ubiquiti (UBNT) devices:
 * - UniFi OS Gateways, Cloud Keys, Access Points, and UniFi Switches
 * - EdgeMAX (EdgeRouter, EdgeSwitch)
 * - airMAX / LTU wireless radios
 * Listens on UDP port 10001 for Ubiquiti Discovery Protocol broadcasts.
 */
interface UbiquitiDeviceService {
    val ubiquitiBroadcastPort: Int get() = 10001

    /**
     * Broadcasts UBNT discovery packet and listens for device responses.
     */
    fun listenForUbiquitiBeacons(): Flow<NetworkDevice>

    /**
     * Queries detailed UniFi / EdgeOS stats via REST API or SSH.
     */
    suspend fun queryDeviceDetails(ipAddress: String): Result<NetworkDevice>
}
