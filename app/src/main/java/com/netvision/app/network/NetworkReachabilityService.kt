package com.netvision.app.network

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

class NetworkReachabilityService {

    private val commonPorts = listOf(80, 443, 22, 161, 8728) // Web, SSH, SNMP, RouterOS API

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
