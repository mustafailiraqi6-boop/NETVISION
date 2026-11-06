package com.netvision.app.network

import com.netvision.app.NetVisionApplication
import com.netvision.app.models.DeviceStatus
import com.netvision.app.models.DeviceType
import com.netvision.app.models.Manufacturer
import com.netvision.app.models.NetworkDevice
import com.netvision.app.models.NetworkMetrics
import com.netvision.app.monitoring.SnmpV2Client
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket

class DefaultMikroTikDeviceService : MikroTikDeviceService {

    override fun listenForMndpAnnouncements(): Flow<NetworkDevice> = flow {
        // Listens for MikroTik MNDP UDP broadcasts on port 5678
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(mndpPort))
                soTimeout = 3000
            }
            val buffer = ByteArray(2048)
            val packet = DatagramPacket(buffer, buffer.size)

            while (true) {
                try {
                    socket.receive(packet)
                    val senderIp = packet.address.hostAddress ?: continue
                    val device = NetworkDevice(
                        id = "mndp-$senderIp",
                        name = "MikroTik ($senderIp)",
                        ipAddress = senderIp,
                        macAddress = "MNDP Discovery",
                        manufacturer = Manufacturer.MIKROTIK,
                        model = "RouterBOARD",
                        deviceType = DeviceType.ROUTER,
                        status = DeviceStatus.ONLINE,
                        lastSeenTimestamp = System.currentTimeMillis()
                    )
                    emit(device)
                } catch (e: Exception) {
                    // Timeout or socket closed, keep loop alive
                }
            }
        } catch (e: Exception) {
            // Cannot bind or no permissions
        } finally {
            socket?.close()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun fetchSystemResources(
        ipAddress: String,
        credentials: Pair<String, String>?
    ): Result<NetworkDevice> = withContext(Dispatchers.IO) {
        val configuredCommunity = try {
            NetVisionApplication.instance.settingsRepository.getSettings().snmpCommunity
        } catch (e: Exception) {
            "netvision"
        }

        // Try SNMP first
        val snmpRes = SnmpV2Client.queryDeviceInfo(ipAddress, community = configuredCommunity)
        if (snmpRes.isSuccess) {
            val info = snmpRes.getOrNull()
            if (info != null && info.isSuccess) {
                val desc = info.sysDescr ?: "MikroTik RouterOS"
                val name = info.sysName ?: "MikroTik ($ipAddress)"
                return@withContext Result.success(
                    NetworkDevice(
                        id = "mtk-$ipAddress",
                        name = name,
                        ipAddress = ipAddress,
                        macAddress = "Discovered via SNMP ($configuredCommunity)",
                        manufacturer = Manufacturer.MIKROTIK,
                        model = desc.take(30),
                        deviceType = DeviceType.ROUTER,
                        status = DeviceStatus.ONLINE,
                        firmwareVersion = desc,
                        lastSeenTimestamp = System.currentTimeMillis(),
                        metrics = NetworkMetrics(
                            uptimeFormatted = info.uptimeFormatted ?: "N/A"
                        )
                    )
                )
            }
        }

        // Use authenticated RouterOS API when credentials were supplied.
        if (credentials != null) {
            val apiResult = RouterOsApiClient().fetchSystemResources(
                host = ipAddress,
                port = 8728,
                username = credentials.first,
                password = credentials.second
            )
            if (apiResult.isSuccess) {
                val resources = apiResult.getOrThrow()
                val totalMemory = resources["total-memory"]?.toLongOrNull() ?: 0L
                val freeMemory = resources["free-memory"]?.toLongOrNull() ?: 0L
                val usedMemoryPercent = if (totalMemory > 0L) {
                    (((totalMemory - freeMemory).coerceAtLeast(0L) * 100) / totalMemory).toInt()
                } else 0
                val version = resources["version"] ?: "RouterOS"
                val board = resources["board-name"] ?: "MikroTik"
                return@withContext Result.success(
                    NetworkDevice(
                        id = "mtk-$ipAddress",
                        name = resources["identity"] ?: "MikroTik ($ipAddress)",
                        ipAddress = ipAddress,
                        macAddress = "",
                        manufacturer = Manufacturer.MIKROTIK,
                        model = board,
                        deviceType = DeviceType.ROUTER,
                        status = DeviceStatus.ONLINE,
                        firmwareVersion = version,
                        lastSeenTimestamp = System.currentTimeMillis(),
                        metrics = NetworkMetrics(
                            uptimeFormatted = resources["uptime"] ?: "N/A",
                            cpuUsagePercent = resources["cpu-load"]?.toIntOrNull() ?: 0,
                            memoryUsagePercent = usedMemoryPercent
                        )
                    )
                )
            }
        }

        // Test Winbox port (8291) only as a reachability fallback.
        var winboxReachable = false
        try {
            Socket().use { s ->
                s.connect(InetSocketAddress(ipAddress, 8291), 1000)
                winboxReachable = true
            }
        } catch (e: Exception) {}

        if (winboxReachable) {
            return@withContext Result.success(
                NetworkDevice(
                    id = "mtk-$ipAddress",
                    name = "MikroTik Device ($ipAddress)",
                    ipAddress = ipAddress,
                    macAddress = "Winbox Port 8291",
                    manufacturer = Manufacturer.MIKROTIK,
                    model = "RouterOS",
                    deviceType = DeviceType.ROUTER,
                    status = DeviceStatus.ONLINE,
                    lastSeenTimestamp = System.currentTimeMillis()
                )
            )
        }

        Result.failure(Exception("MikroTik not reachable at $ipAddress"))
    }
}
