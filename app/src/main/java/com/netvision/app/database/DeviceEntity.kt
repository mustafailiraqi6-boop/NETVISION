package com.netvision.app.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.netvision.app.models.DeviceStatus
import com.netvision.app.models.DeviceType
import com.netvision.app.models.Manufacturer
import com.netvision.app.models.NetworkDevice
import com.netvision.app.models.NetworkMetrics

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val ipAddress: String,
    val macAddress: String,
    val manufacturer: String,
    val model: String,
    val deviceType: String,
    val status: String,
    val firmwareVersion: String,
    val lastSeenTimestamp: Long,
    val signalDbm: Int,
    val txRateMbps: Double,
    val rxRateMbps: Double,
    val uptimeFormatted: String,
    val cpuUsagePercent: Int,
    val memoryUsagePercent: Int,
    val pingLatencyMs: Int,
    val snmpVersion: String? = null,
    val folderId: String = "uncategorized"
) {
    fun toDomainModel(): NetworkDevice {
        return NetworkDevice(
            id = id,
            name = name,
            ipAddress = ipAddress,
            macAddress = macAddress,
            manufacturer = try { Manufacturer.valueOf(manufacturer) } catch (e: Exception) { Manufacturer.GENERIC },
            model = model,
            deviceType = try { DeviceType.valueOf(deviceType) } catch (e: Exception) { DeviceType.ROUTER },
            status = try { DeviceStatus.valueOf(status) } catch (e: Exception) { DeviceStatus.ONLINE },
            firmwareVersion = firmwareVersion,
            lastSeenTimestamp = lastSeenTimestamp,
            metrics = NetworkMetrics(
                signalDbm = signalDbm,
                txRateMbps = txRateMbps,
                rxRateMbps = rxRateMbps,
                uptimeFormatted = uptimeFormatted,
                cpuUsagePercent = cpuUsagePercent,
                memoryUsagePercent = memoryUsagePercent,
                pingLatencyMs = pingLatencyMs
            ),
            snmpVersion = snmpVersion,
            folderId = if (folderId.isBlank()) "uncategorized" else folderId
        )
    }

    companion object {
        fun fromDomainModel(device: NetworkDevice): DeviceEntity {
            return DeviceEntity(
                id = device.id,
                name = device.name,
                ipAddress = device.ipAddress,
                macAddress = device.macAddress,
                manufacturer = device.manufacturer.name,
                model = device.model,
                deviceType = device.deviceType.name,
                status = device.status.name,
                firmwareVersion = device.firmwareVersion,
                lastSeenTimestamp = device.lastSeenTimestamp,
                signalDbm = device.metrics.signalDbm,
                txRateMbps = device.metrics.txRateMbps,
                rxRateMbps = device.metrics.rxRateMbps,
                uptimeFormatted = device.metrics.uptimeFormatted,
                cpuUsagePercent = device.metrics.cpuUsagePercent,
                memoryUsagePercent = device.metrics.memoryUsagePercent,
                pingLatencyMs = device.metrics.pingLatencyMs,
                snmpVersion = device.snmpVersion,
                folderId = if (device.folderId.isBlank()) "uncategorized" else device.folderId
            )
        }
    }
}
