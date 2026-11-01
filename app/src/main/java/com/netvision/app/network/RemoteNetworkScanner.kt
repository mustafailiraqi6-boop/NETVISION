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
import java.io.BufferedReader
import java.io.FileReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

data class RemoteScanStatus(
    val subnetCidr: String,
    val totalHosts: Int,
    val scannedHosts: Int,
    val percentage: Int,
    val currentIp: String,
    val isRouteAccessible: Boolean,
    val newlyFoundDevice: NetworkDevice? = null,
    val isFinished: Boolean = false
)

class RemoteNetworkScanner(
    private val reachabilityService: NetworkReachabilityService = NetworkReachabilityService(NetVisionApplication.instance)
) {

    // Common ports tested for network equipment (HTTP Port 80 checked FIRST for WebFig / Web UI)
    private val probePorts = listOf(80, 443, 8291, 8728, 22, 8080, 8443)

    fun scanRemoteSubnet(subnetCidr: String): Flow<RemoteScanStatus> = flow {
        val hostIps = CidrUtils.getUsableHostIps(subnetCidr, maxHosts = 512)
        val totalHosts = hostIps.size

        if (totalHosts == 0) {
            emit(
                RemoteScanStatus(
                    subnetCidr = subnetCidr,
                    totalHosts = 0,
                    scannedHosts = 0,
                    percentage = 100,
                    currentIp = "",
                    isRouteAccessible = false,
                    newlyFoundDevice = null,
                    isFinished = true
                )
            )
            return@flow
        }

        // Test route accessibility on the first IP
        val firstIp = hostIps.first()
        val gatewayCheck = reachabilityService.testReachability(firstIp, port = 80, timeoutMs = 600)
        val isAccessible = gatewayCheck.isReachable || reachabilityService.testReachability(firstIp, port = 443, timeoutMs = 600).isReachable

        var scanned = 0

        for (targetIp in hostIps) {
            scanned++

            val foundDevice = probeHost(targetIp)

            val percent = ((scanned.toFloat() / totalHosts) * 100).toInt().coerceIn(0, 100)
            val isLast = scanned == totalHosts

            emit(
                RemoteScanStatus(
                    subnetCidr = subnetCidr,
                    totalHosts = totalHosts,
                    scannedHosts = scanned,
                    percentage = percent,
                    currentIp = targetIp,
                    isRouteAccessible = isAccessible || (foundDevice != null),
                    newlyFoundDevice = foundDevice,
                    isFinished = isLast
                )
            )
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Probes an individual IP address using TCP socket connections, ICMP ping, and SNMP.
     * Guaranteed to return a NetworkDevice ONLY if the device is genuinely reachable.
     */
    suspend fun probeHost(targetIp: String): NetworkDevice? = withContext(Dispatchers.IO) {
        val configuredCommunity = try {
            NetVisionApplication.instance.settingsRepository.getSettings().snmpCommunity
        } catch (e: Exception) {
            "netvision"
        }

        var isOnline = false
        var activePort = 0
        var latencyMs: Long = 0

        // 1. Probe TCP ports with short timeout (port 80 checked FIRST for MikroTik WebFig and web UI)
        for (port in probePorts) {
            val startTime = System.currentTimeMillis()
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(targetIp, port), 350)
                    isOnline = true
                    activePort = port
                    latencyMs = System.currentTimeMillis() - startTime
                }
            } catch (e: Exception) {}

            if (isOnline) break
        }

        // 2. If TCP didn't respond, try ICMP echo ping
        if (!isOnline) {
            val startTime = System.currentTimeMillis()
            try {
                val addr = InetAddress.getByName(targetIp)
                if (addr.isReachable(400)) {
                    isOnline = true
                    activePort = 0
                    latencyMs = System.currentTimeMillis() - startTime
                }
            } catch (e: Exception) {}
        }

        // 3. If still not responding, try SNMP v2c
        var snmpSysDescr: String? = null
        var snmpSysName: String? = null
        var snmpUptime: String? = null

        val snmpRes = SnmpV2Client.queryDeviceInfo(targetIp, community = configuredCommunity, timeoutMs = 500)
        if (snmpRes.isSuccess) {
            val info = snmpRes.getOrNull()
            if (info != null && info.isSuccess) {
                isOnline = true
                snmpSysDescr = info.sysDescr
                snmpSysName = info.sysName
                snmpUptime = info.uptimeFormatted
            }
        }

        if (!isOnline) {
            return@withContext null
        }

        // 4. Resolve Hostname
        var hostname = snmpSysName
        if (hostname.isNullOrBlank()) {
            try {
                val host = InetAddress.getByName(targetIp).canonicalHostName
                if (host != targetIp && host.isNotBlank()) {
                    hostname = host
                }
            } catch (e: Exception) {}
        }

        // 5. Check ARP cache for MAC address
        val arpMac = getMacFromArpCache(targetIp)

        // 6. Classify Vendor & Device Type
        val (vendor, devType, modelName) = classifyDevice(activePort, snmpSysDescr, hostname, arpMac)

        val finalName = when {
            !hostname.isNullOrBlank() -> hostname
            vendor == Manufacturer.MIKROTIK -> "MikroTik ($targetIp)"
            vendor == Manufacturer.UBIQUITI -> "Ubiquiti ($targetIp)"
            else -> "Host ($targetIp)"
        }

        val macDisplay = arpMac ?: if (activePort > 0) "OpenVPN Port $activePort" else "VPN L3 Host"

        NetworkDevice(
            id = "net-$targetIp",
            name = finalName,
            ipAddress = targetIp,
            macAddress = macDisplay,
            manufacturer = vendor,
            model = modelName,
            deviceType = devType,
            status = DeviceStatus.ONLINE,
            firmwareVersion = snmpSysDescr?.take(40) ?: "Standard",
            lastSeenTimestamp = System.currentTimeMillis(),
            metrics = NetworkMetrics(
                pingLatencyMs = latencyMs.toInt().coerceAtLeast(1),
                uptimeFormatted = snmpUptime ?: "N/A",
                cpuUsagePercent = 0,
                memoryUsagePercent = 0,
                signalDbm = 0,
                txRateMbps = 0.0,
                rxRateMbps = 0.0
            )
        )
    }

    private fun classifyDevice(
        openPort: Int,
        snmpDescr: String?,
        hostname: String?,
        mac: String?
    ): Triple<Manufacturer, DeviceType, String> {
        val descr = (snmpDescr ?: "").lowercase()
        val host = (hostname ?: "").lowercase()
        val macLower = (mac ?: "").lowercase()

        // 1. Explicit Ubiquiti indicators (sysDescr, hostname, MAC OUI, Ubiquiti ports)
        if (descr.contains("ubiquiti") || descr.contains("ubnt") || descr.contains("unifi") || descr.contains("airos") || descr.contains("airmax") || descr.contains("litebeam") || descr.contains("powerbeam") || descr.contains("nanostation") || descr.contains("rocket") ||
            host.contains("ubnt") || host.contains("unifi") || host.contains("airos") || host.contains("airmax") ||
            macLower.startsWith("74:83:c2") || macLower.startsWith("ac:8b:a9") || macLower.startsWith("68:d7:9a") || macLower.startsWith("24:a4:3c") || macLower.startsWith("00:27:22") || macLower.startsWith("04:18:d6") || macLower.startsWith("18:e8:29") || macLower.startsWith("80:2a:a8") || macLower.startsWith("b4:fb:e4") || macLower.startsWith("d8:b6:b7") || macLower.startsWith("e8:de:27") || macLower.startsWith("f0:9f:c2") ||
            openPort == 8080 || openPort == 8443
        ) {
            val devType = if (descr.contains("switch") || host.contains("sw")) DeviceType.SWITCH else DeviceType.ACCESS_POINT
            val model = if (snmpDescr != null && snmpDescr.length > 5) snmpDescr.take(25) else "AirOS / Ubiquiti"
            return Triple(Manufacturer.UBIQUITI, devType, model)
        }

        // 2. Explicit MikroTik indicators (RouterOS, Winbox 8291, API 8728, MAC OUI)
        if (descr.contains("mikrotik") || descr.contains("routeros") ||
            host.contains("mikrotik") || host.contains("rb") ||
            macLower.startsWith("48:8f:5a") || macLower.startsWith("cc:2d:e0") || macLower.startsWith("08:55:31") || macLower.startsWith("b8:69:f4") || macLower.startsWith("d4:ca:6d") || macLower.startsWith("e4:8d:8c") || macLower.startsWith("2c:c8:1b") || macLower.startsWith("64:d1:54") ||
            openPort == 8291 || openPort == 8728
        ) {
            val model = if (snmpDescr != null && snmpDescr.length > 5) snmpDescr.take(25) else "RouterOS"
            return Triple(Manufacturer.MIKROTIK, DeviceType.ROUTER, model)
        }

        // 3. Cisco
        if (descr.contains("cisco") || host.contains("cisco") || macLower.startsWith("00:1e:13")) {
            return Triple(Manufacturer.CISCO, DeviceType.SWITCH, "Catalyst / IOS")
        }

        val type = when (openPort) {
            80, 443 -> DeviceType.GATEWAY
            22 -> DeviceType.ROUTER
            else -> DeviceType.ROUTER
        }

        return Triple(Manufacturer.GENERIC, type, if (openPort > 0) "TCP Port $openPort" else "Host")
    }

    private fun getMacFromArpCache(ip: String): String? {
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                var line = reader.readLine() // skip header
                while (reader.readLine().also { line = it } != null) {
                    val tokens = line!!.split("\\s+".toRegex())
                    if (tokens.size >= 4 && tokens[0] == ip) {
                        val mac = tokens[3]
                        if (mac != "00:00:00:00:00:00" && mac.length == 17) {
                            return mac.uppercase()
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        return null
    }
}
