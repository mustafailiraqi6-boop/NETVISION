package com.netvision.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

data class ReachabilityResult(
    val targetIp: String,
    val isReachable: Boolean,
    val portTested: Int,
    val latencyMs: Long,
    val failureReason: String? = null
)

class NetworkReachabilityService {

    // Common ports tested for network equipment reachability (HTTP Port 80 checked FIRST for WebFig / Web UI)
    private val commonPorts = listOf(80, 443, 8291, 8728, 22, 8080, 8443)

    /**
     * Checks if a target IP is reachable by probing TCP ports (starting with port 80 for WebFig)
     * and falling back to ICMP ping.
     * Executed strictly off the main thread on Dispatchers.IO.
     */
    suspend fun checkHostReachability(
        targetIp: String,
        portsToProbe: List<Int> = commonPorts,
        timeoutMs: Int = 500
    ): ReachabilityResult = withContext(Dispatchers.IO) {
        // 1. Probe TCP ports sequentially (port 80 first for WebFig)
        for (port in portsToProbe) {
            val startTime = System.currentTimeMillis()
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(targetIp, port), timeoutMs)
                    val latency = System.currentTimeMillis() - startTime
                    return@withContext ReachabilityResult(
                        targetIp = targetIp,
                        isReachable = true,
                        portTested = port,
                        latencyMs = latency
                    )
                }
            } catch (e: Exception) {
                // Port connection failed or timed out, probe next port
            }
        }

        // 2. Fallback to ICMP ping if all TCP ports failed
        val startTime = System.currentTimeMillis()
        try {
            val addr = InetAddress.getByName(targetIp)
            if (addr.isReachable(timeoutMs)) {
                val latency = System.currentTimeMillis() - startTime
                return@withContext ReachabilityResult(
                    targetIp = targetIp,
                    isReachable = true,
                    portTested = 0,
                    latencyMs = latency
                )
            }
        } catch (e: Exception) {
            // ICMP failed
        }

        // 3. Both TCP ports and ICMP failed
        ReachabilityResult(
            targetIp = targetIp,
            isReachable = false,
            portTested = 0,
            latencyMs = 0,
            failureReason = "Host unreachable on TCP ports $portsToProbe and ICMP"
        )
    }

    suspend fun testReachability(
        targetIp: String,
        port: Int = 80,
        timeoutMs: Int = 1200
    ): ReachabilityResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(targetIp, port), timeoutMs)
                val latency = System.currentTimeMillis() - startTime
                ReachabilityResult(
                    targetIp = targetIp,
                    isReachable = true,
                    portTested = port,
                    latencyMs = latency
                )
            }
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - startTime
            ReachabilityResult(
                targetIp = targetIp,
                isReachable = false,
                portTested = port,
                latencyMs = latency,
                failureReason = e.localizedMessage ?: "Connection Timeout or Route Unreachable"
            )
        }
    }

    suspend fun probeDevicePorts(targetIp: String, timeoutMs: Int = 800): List<Int> = withContext(Dispatchers.IO) {
        val openPorts = mutableListOf<Int>()
        for (port in commonPorts) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(targetIp, port), timeoutMs)
                    openPorts.add(port)
                }
            } catch (e: Exception) {}
        }
        openPorts
    }
}
