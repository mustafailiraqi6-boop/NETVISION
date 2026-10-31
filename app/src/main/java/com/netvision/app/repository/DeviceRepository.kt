package com.netvision.app.repository

import com.netvision.app.database.DeviceDao
import com.netvision.app.database.DeviceEntity
import com.netvision.app.database.NetworkRangeDao
import com.netvision.app.database.NetworkRangeEntity
import com.netvision.app.models.ActivitySeverity
import com.netvision.app.models.DeviceStatus
import com.netvision.app.models.NetworkDevice
import com.netvision.app.models.NetworkRange
import com.netvision.app.models.RecentDeviceActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class DeviceRepository(
    private val deviceDao: DeviceDao? = null,
    private val networkRangeDao: NetworkRangeDao? = null
) {

    // Dynamic real activity log populated only by genuine network events
    private val _recentActivities = MutableStateFlow<List<RecentDeviceActivity>>(emptyList())
    val recentActivities: Flow<List<RecentDeviceActivity>> = _recentActivities.asStateFlow()

    // -------------------------------------------------------------
    // Device Management (Room Database)
    // -------------------------------------------------------------

    fun getAllDevices(): Flow<List<NetworkDevice>> {
        return deviceDao?.getAllDevices()?.map { list ->
            list.map { it.toDomainModel() }
        } ?: flowOf(emptyList())
    }

    suspend fun getDeviceById(id: String): NetworkDevice? {
        return deviceDao?.getDeviceById(id)?.toDomainModel()
    }

    suspend fun saveDevice(device: NetworkDevice) {
        deviceDao?.insertOrUpdate(DeviceEntity.fromDomainModel(device))
        logActivity(
            deviceName = device.name,
            ipAddress = device.ipAddress,
            messageEn = "Device added to permanent monitoring list (${device.ipAddress}).",
            messageAr = "تمت إضافة الجهاز إلى قائمة المراقبة الدائمة (${device.ipAddress}).",
            severity = ActivitySeverity.SUCCESS
        )
    }

    suspend fun updateDevice(device: NetworkDevice) {
        deviceDao?.insertOrUpdate(DeviceEntity.fromDomainModel(device))
    }

    suspend fun deleteDevice(device: NetworkDevice) {
        deviceDao?.delete(DeviceEntity.fromDomainModel(device))
        logActivity(
            deviceName = device.name,
            ipAddress = device.ipAddress,
            messageEn = "Device removed from monitoring (${device.ipAddress}).",
            messageAr = "تم حذف الجهاز من المراقبة (${device.ipAddress}).",
            severity = ActivitySeverity.INFO
        )
    }

    suspend fun updateDeviceStatus(
        deviceId: String,
        isOnline: Boolean,
        latencyMs: Int,
        uptime: String? = null,
        snmpVersion: String? = null
    ) {
        val entity = deviceDao?.getDeviceById(deviceId) ?: return
        val currentStatus = try { DeviceStatus.valueOf(entity.status) } catch (e: Exception) { DeviceStatus.OFFLINE }
        val newStatus = if (isOnline) DeviceStatus.ONLINE else DeviceStatus.OFFLINE

        val updatedEntity = entity.copy(
            status = newStatus.name,
            lastSeenTimestamp = if (isOnline) System.currentTimeMillis() else entity.lastSeenTimestamp,
            pingLatencyMs = if (isOnline) latencyMs else 0,
            uptimeFormatted = uptime ?: entity.uptimeFormatted,
            snmpVersion = snmpVersion ?: entity.snmpVersion
        )
        deviceDao.insertOrUpdate(updatedEntity)

        // Log state changes
        if (currentStatus != newStatus) {
            if (newStatus == DeviceStatus.OFFLINE) {
                logActivity(
                    deviceName = entity.name,
                    ipAddress = entity.ipAddress,
                    messageEn = "Device connection lost (ICMP / TCP timeout).",
                    messageAr = "انقطع اتصال الجهاز (انتهاء مهلة ICMP / TCP).",
                    severity = ActivitySeverity.ALERT
                )
            } else {
                logActivity(
                    deviceName = entity.name,
                    ipAddress = entity.ipAddress,
                    messageEn = "Device back online (Latency ${latencyMs}ms).",
                    messageAr = "عاد الجهاز للاتصال (زمن الاستجابة ${latencyMs}ms).",
                    severity = ActivitySeverity.SUCCESS
                )
            }
        }
    }

    // -------------------------------------------------------------
    // CIDR Network Ranges Management (Room Database)
    // -------------------------------------------------------------

    fun getAllRanges(): Flow<List<NetworkRange>> {
        return networkRangeDao?.getAllRanges()?.map { list ->
            list.map { it.toDomainModel() }
        } ?: flowOf(emptyList())
    }

    suspend fun saveRange(range: NetworkRange) {
        networkRangeDao?.insertOrUpdate(NetworkRangeEntity.fromDomainModel(range))
    }

    suspend fun deleteRange(rangeId: String) {
        networkRangeDao?.deleteById(rangeId)
    }

    suspend fun toggleRangeEnabled(rangeId: String, isEnabled: Boolean) {
        val existing = networkRangeDao?.getRangeById(rangeId) ?: return
        networkRangeDao.update(existing.copy(isEnabled = isEnabled))
    }

    // -------------------------------------------------------------
    // Real Activity Logging
    // -------------------------------------------------------------

    fun logActivity(
        deviceName: String,
        ipAddress: String,
        messageEn: String,
        messageAr: String,
        severity: ActivitySeverity
    ) {
        val newAct = RecentDeviceActivity(
            id = "act-${System.currentTimeMillis()}-${(1..1000).random()}",
            deviceName = deviceName,
            ipAddress = ipAddress,
            messageEn = messageEn,
            messageAr = messageAr,
            timeAgoEn = "Just now",
            timeAgoAr = "الآن",
            severity = severity
        )
        val current = _recentActivities.value.toMutableList()
        current.add(0, newAct)
        if (current.size > 20) {
            current.removeAt(current.size - 1)
        }
        _recentActivities.value = current
    }
}
