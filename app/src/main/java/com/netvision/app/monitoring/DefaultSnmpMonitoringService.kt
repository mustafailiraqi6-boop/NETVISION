package com.netvision.app.monitoring

import com.netvision.app.models.NetworkMetrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DefaultSnmpMonitoringService : SnmpMonitoringService {

    override suspend fun pollDeviceMetrics(target: SnmpMonitoringService.SnmpTarget): Result<NetworkMetrics> {
        val snmpResult = SnmpV2Client.queryDeviceInfo(
            targetIp = target.ipAddress,
            community = target.communityString,
            port = target.port
        )

        return snmpResult.fold(
            onSuccess = { info ->
                Result.success(
                    NetworkMetrics(
                        uptimeFormatted = info.uptimeFormatted ?: "Unknown",
                        // Only real values are set, placeholder flags are absent
                        signalDbm = 0,
                        txRateMbps = 0.0,
                        rxRateMbps = 0.0,
                        cpuUsagePercent = 0,
                        memoryUsagePercent = 0,
                        pingLatencyMs = 0
                    )
                )
            },
            onFailure = { err ->
                Result.failure(err)
            }
        )
    }

    override fun monitorLiveStream(
        target: SnmpMonitoringService.SnmpTarget,
        intervalMs: Long
    ): Flow<NetworkMetrics> = flow {
        while (true) {
            val result = pollDeviceMetrics(target)
            result.getOrNull()?.let { emit(it) }
            delay(intervalMs)
        }
    }

    override suspend fun testSnmpConnectivity(target: SnmpMonitoringService.SnmpTarget): Boolean {
        val result = SnmpV2Client.queryDeviceInfo(
            targetIp = target.ipAddress,
            community = target.communityString,
            port = target.port,
            timeoutMs = 1200
        )
        return result.isSuccess
    }
}
