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

class DefaultUbiquitiDeviceService : UbiquitiDeviceService {

    override fun listenForUbiquitiBeacons(): Flow<NetworkDevice> = flow {
        // Listens on UDP port 10001 for Ubiquiti Discovery Protocol broadcasts
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(null).apply {
                reuseAddress = true
                bind(InetSocketAddress(ubiquitiBroadcastPort))
                soTimeout = 3000
            }
            val buffer = ByteArray(2048)
            val packet = DatagramPacket(buffer, buffer.size)

            while (true) {
                try {
                    socket.receive(packet)
                    val senderIp = packet.address.hostAddress ?: continue
                    val device = NetworkDevice(
                        id = "ubnt-$senderIp",
                        name = "Ubiquiti ($senderIp)",
                        ipAddress = senderIp,
                        macAddress = "UDP 10001 Beacon",
                        manufacturer = Manufacturer.UBIQUITI,
                        model = "UniFi / airMAX",
                        deviceType = DeviceType.ACCESS_POINT,
                        status = DeviceStatus.ONLINE,
                        lastSeenTimestamp = System.currentTimeMillis()
                    )
                    emit(device)
                } catch (e: Exception) {
                    // Timeout or keep listening
                }
            }
        } catch (e: Exception) {
            // Cannot bind
        } finally {
            socket?.close()
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun queryDeviceDetails(ipAddress: String): Result<NetworkDevice> = withContext(Dispatchers.IO) {
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
                val desc = info.sysDescr ?: "Ubiquiti UniFi"
                val name = info.sysName ?: "Ubiquiti ($ipAddress)"
                return@withContext Result.success(
                    NetworkDevice(
                        id = "ubnt-$ipAddress",
                        name = name,
                        ipAddress = ipAddress,
                        macAddress = "Discovered via SNMP ($configuredCommunity)",
                        manufacturer = Manufacturer.UBIQUITI,
                        model = desc.take(30),
                        deviceType = DeviceType.ACCESS_POINT,
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

        // Test UniFi management ports: 8080 or 8443 or standard HTTPS 443
        var reachable = false
        val ports = listOf(8443, 8080, 443, 22)
        var openedPort = 0
        for (p in ports) {
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(ipAddress, p), 800)
                    reachable = true
                    openedPort = p
                }
                if (reachable) break
            } catch (e: Exception) {}
        }

        if (reachable) {
            return@withContext Result.success(
                NetworkDevice(
                    id = "ubnt-$ipAddress",
                    name = "Ubiquiti Device ($ipAddress)",
                    ipAddress = ipAddress,
                    macAddress = "Port $openedPort Active",
                    manufacturer = Manufacturer.UBIQUITI,
                    model = "EdgeOS / UniFi",
                    deviceType = DeviceType.ACCESS_POINT,
                    status = DeviceStatus.ONLINE,
                    lastSeenTimestamp = System.currentTimeMillis()
                )
            )
        }

        Result.failure(Exception("Ubiquiti device not reachable at $ipAddress"))
    }
}
