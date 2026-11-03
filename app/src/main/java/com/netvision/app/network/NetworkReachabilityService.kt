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

    /** Returns true only when Android currently exposes an active VPN transport. */
    fun isVpnConnected(): Boolean = connectivityManager.allNetworks.any { network ->
        connectivityManager.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    }

    /**
     * Device reachability is valid only through an active Android VPN network.
     * Every TCP probe is explicitly bound to that VPN; cellular/Wi-Fi fallback
     * is intentionally forbidden to avoid false ONLINE results off-VPN.
     */
    suspend fun checkHostReachability(
        targetIp: String,
        portsToProbe: List<Int> = commonPorts,
        timeoutMs: Int = 2000
    ): ReachabilityResult = withContext(Dispatchers.IO) {
        if (!isVpnConnected()) {
            return@withContext ReachabilityResult(targetIp, false, 0, 0, "VPN is not connected")
        }

        val startedAt = System.currentTimeMillis()
        var process: Process? = null
        try {
            // Use the same ICMP echo mechanism as the Ping screen. TCP port
            // availability must not decide whether a network device is online.
            process = ProcessBuilder("ping", "-c", "1", "-W",
                (timeoutMs / 1000).coerceAtLeast(1).toString(), targetIp)
                .redirectErrorStream(true).start()
            val completed = process.waitFor((timeoutMs + 1500L), java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!completed) {
                process.destroyForcibly()
                return@withContext ReachabilityResult(targetIp, false, 0, System.currentTimeMillis() - startedAt, "ICMP timeout")
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val time = Regex("time[=<]([0-9.]+)\\s*ms", RegexOption.IGNORE_CASE)
                .find(output)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            val success = process.exitValue() == 0 || output.contains("bytes from", true) || time != null
            ReachabilityResult(
                targetIp = targetIp,
                isReachable = success,
                portTested = 0,
                latencyMs = time?.toLong() ?: (System.currentTimeMillis() - startedAt),
                failureReason = if (success) null else "No ICMP echo reply"
            )
        } catch (e: Exception) {
            ReachabilityResult(targetIp, false, 0, System.currentTimeMillis() - startedAt, e.localizedMessage ?: "ICMP check failed")
        } finally {
            process?.destroy()
        }
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
