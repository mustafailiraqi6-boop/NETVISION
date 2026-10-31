package com.netvision.app.network

import com.netvision.app.models.DeviceStatus
import com.netvision.app.models.DeviceType
import com.netvision.app.models.Manufacturer
import com.netvision.app.models.NetworkDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.InetSocketAddress
import java.net.Socket

data class RemoteScanStatus(
    val subnetCidr: String,
    val totalHosts: Int,
    val scannedHosts: Int,
    val percentage: Int,
    val currentIp: String,
    val isRouteAccessible: Boolean,
    val newlyFoundDevice: NetworkDevice? = null
)

class RemoteNetworkScanner(
    private val reachabilityService: NetworkReachabilityService = NetworkReachabilityService()
) {

    fun scanRemoteSubnet(subnetCidr: String, portsToProbe: List<Int> = listOf(80, 443, 22, 161, 8728)): Flow<RemoteScanStatus> = flow {
        val baseIp = subnetCidr.substringBefore("/")
        val prefix = baseIp.substringBeforeLast(".")

        // First test gateway / network reachability on .1
        val gatewayIp = "$prefix.1"
        val gatewayCheck = reachabilityService.testReachability(gatewayIp, port = 80, timeoutMs = 1000)

        val isAccessible = gatewayCheck.isReachable || reachabilityService.testReachability(gatewayIp, port = 443, timeoutMs = 1000).isReachable

        val totalHosts = 254
        var scanned = 0

        for (host in 1..254) {
            val targetIp = "$prefix.$host"
            scanned++

            var foundDevice: NetworkDevice? = null

            // Check TCP socket connectivity across requested ports
            for (port in portsToProbe) {
                val res = reachabilityService.testReachability(targetIp, port = port, timeoutMs = 400)
                if (res.isReachable) {
                    foundDevice = NetworkDevice(
                        id = "remote-$targetIp",
                        name = "Remote Host ($targetIp)",
                        ipAddress = targetIp,
                        macAddress = "VPN Route (${port})",
                        manufacturer = if (port == 8728) Manufacturer.MIKROTIK else Manufacturer.GENERIC,
                        model = "Remote VPN Node",
                        deviceType = DeviceType.ROUTER,
                        status = DeviceStatus.ONLINE,
                        lastSeenTimestamp = System.currentTimeMillis()
                    )
                    break
                }
            }

            emit(
                RemoteScanStatus(
                    subnetCidr = subnetCidr,
                    totalHosts = totalHosts,
                    scannedHosts = scanned,
                    percentage = (scanned * 100) / totalHosts,
                    currentIp = targetIp,
                    isRouteAccessible = isAccessible,
                    newlyFoundDevice = foundDevice
                )
            )
        }
    }.flowOn(Dispatchers.IO)
}
