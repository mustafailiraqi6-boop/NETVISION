package com.netvision.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

data class ReachabilityResult(
    val targetIp: String,
    val isReachable: Boolean,
    val portTested: Int,
    val latencyMs: Long,
    val failureReason: String? = null
)

class NetworkReachabilityService(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val commonPorts = listOf(80, 443, 8291, 8728, 22, 8080, 8443)

    /**
     * Device reachability is valid only through an active Android VPN network.
     * Every TCP probe is explicitly bound to that VPN; cellular/Wi-Fi fallback
     * is intentionally forbidden to avoid false ONLINE results off-VPN.
     */
    suspend fun checkHostReachability(
        targetIp: String,
        portsToProbe: List<Int> = commonPorts,
        timeoutMs: Int = 500
    ): ReachabilityResult = withContext(Dispatchers.IO) {
        val vpnNetwork: Network? = connectivityManager.allNetworks.firstOrNull { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }

        if (vpnNetwork == null) {
            return@withContext ReachabilityResult(
                targetIp = targetIp,
                isReachable = false,
                portTested = 0,
                latencyMs = 0,
                failureReason = "VPN is not connected"
            )
        }

        for (port in portsToProbe) {
            val startTime = System.currentTimeMillis()
            try {
                Socket().use { socket ->
                    vpnNetwork.bindSocket(socket)
                    socket.connect(InetSocketAddress(targetIp, port), timeoutMs)
                    val latency = System.currentTimeMillis() - startTime
                    return@withContext ReachabilityResult(
                        targetIp = targetIp,
                        isReachable = true,
                        portTested = port,
                        latencyMs = latency
                    )
                }
            } catch (_: Exception) {
                // Try the next TCP port, always on the same VPN network.
            }
        }

        ReachabilityResult(
            targetIp = targetIp,
            isReachable = false,
            portTested = 0,
            latencyMs = 0,
            failureReason = "No TCP response through VPN on ports $portsToProbe"
        )
    }

    suspend fun testReachability(
        targetIp: String,
        port: Int = 80,
        timeoutMs: Int = 1200
    ): ReachabilityResult = withContext(Dispatchers.IO) {
        val vpnNetwork = connectivityManager.allNetworks.firstOrNull { network ->
            connectivityManager.getNetworkCapabilities(network)
                ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        } ?: return@withContext ReachabilityResult(
            targetIp, false, port, 0, "VPN is not connected"
        )

        val startTime = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                vpnNetwork.bindSocket(socket)
                socket.connect(InetSocketAddress(targetIp, port), timeoutMs)
                ReachabilityResult(
                    targetIp = targetIp,
                    isReachable = true,
                    portTested = port,
                    latencyMs = System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Exception) {
            ReachabilityResult(
                targetIp = targetIp,
                isReachable = false,
                portTested = port,
                latencyMs = System.currentTimeMillis() - startTime,
                failureReason = e.localizedMessage ?: "VPN connection failed"
            )
        }
    }

    suspend fun probeDevicePorts(targetIp: String, timeoutMs: Int = 800): List<Int> =
        withContext(Dispatchers.IO) {
            val vpnNetwork = connectivityManager.allNetworks.firstOrNull { network ->
                connectivityManager.getNetworkCapabilities(network)
                    ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            } ?: return@withContext emptyList()

            val openPorts = mutableListOf<Int>()
            for (port in commonPorts) {
                try {
                    Socket().use { socket ->
                        vpnNetwork.bindSocket(socket)
                        socket.connect(InetSocketAddress(targetIp, port), timeoutMs)
                        openPorts.add(port)
                    }
                } catch (_: Exception) { }
            }
            openPorts
        }
}
