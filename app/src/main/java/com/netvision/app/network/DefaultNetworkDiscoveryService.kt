package com.netvision.app.network

import com.netvision.app.models.NetworkDevice
import com.netvision.app.models.NetworkRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.concurrent.atomic.AtomicBoolean

class DefaultNetworkDiscoveryService(
    private val scanner: RemoteNetworkScanner = RemoteNetworkScanner()
) : NetworkDiscoveryService {

    private val isScanActive = AtomicBoolean(false)

    override fun startScan(ranges: List<NetworkRange>): Flow<NetworkDiscoveryService.ScanProgress> = flow {
        isScanActive.set(true)
        val enabledRanges = ranges.filter { it.isEnabled }

        if (enabledRanges.isEmpty()) {
            emit(
                NetworkDiscoveryService.ScanProgress(
                    totalHosts = 0,
                    scannedHosts = 0,
                    percentage = 100,
                    currentIp = "",
                    isCompleted = true,
                    newlyFoundDevice = null
                )
            )
            isScanActive.set(false)
            return@flow
        }

        var cumulativeScanned = 0
        val totalHosts = enabledRanges.sumOf { CidrUtils.getUsableHostIps(it.cidr).size }

        for (range in enabledRanges) {
            if (!isScanActive.get()) break

            scanner.scanRemoteSubnet(range.cidr).collect { status ->
                if (!isScanActive.get()) return@collect
                cumulativeScanned++
                val percent = if (totalHosts > 0) ((cumulativeScanned.toFloat() / totalHosts) * 100).toInt().coerceIn(0, 100) else 100

                emit(
                    NetworkDiscoveryService.ScanProgress(
                        totalHosts = totalHosts,
                        scannedHosts = cumulativeScanned,
                        percentage = percent,
                        currentIp = status.currentIp,
                        isCompleted = status.isFinished && cumulativeScanned >= totalHosts,
                        newlyFoundDevice = status.newlyFoundDevice
                    )
                )
            }
        }
        isScanActive.set(false)
    }.flowOn(Dispatchers.IO)

    override suspend fun stopScan() {
        isScanActive.set(false)
    }

    override fun isScanning(): Boolean = isScanActive.get()
}
