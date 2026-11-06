package com.netvision.app.repository

import com.netvision.app.database.DeviceDao
import com.netvision.app.database.DeviceEntity
import com.netvision.app.database.DeviceFolderDao
import com.netvision.app.database.DeviceFolderEntity
import com.netvision.app.database.NetworkRangeDao
import com.netvision.app.database.NetworkRangeEntity
import com.netvision.app.models.ActivitySeverity
import com.netvision.app.models.DeviceFolder
import com.netvision.app.models.DeviceStatus
import com.netvision.app.models.DeviceType
import com.netvision.app.models.Manufacturer
import com.netvision.app.models.NetworkDevice
import com.netvision.app.models.NetworkRange
import com.netvision.app.models.RecentDeviceActivity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

class DeviceRepository(
    private val deviceDao: DeviceDao? = null,
    private val networkRangeDao: NetworkRangeDao? = null,
    private val deviceFolderDao: DeviceFolderDao? = null
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
        // An IP can exist only once in the permanent monitoring list.
        if (deviceDao?.getDeviceByIpAddress(device.ipAddress.trim()) != null) return
        val folderId = if (device.folderId.isBlank()) DeviceFolderEntity.DEFAULT_FOLDER_ID else device.folderId
        deviceDao?.insertOrUpdate(DeviceEntity.fromDomainModel(device.copy(folderId = folderId)))
        logActivity(
            deviceName = device.name,
            ipAddress = device.ipAddress,
            messageEn = "Device added to permanent monitoring list (${device.ipAddress}).",
            messageAr = "تمت إضافة الجهاز إلى قائمة المراقبة الدائمة (${device.ipAddress}).",
            severity = ActivitySeverity.SUCCESS
        )
    }

    suspend fun updateDeviceManufacturer(deviceId: String, manufacturer: Manufacturer) {
        val entity = deviceDao?.getDeviceById(deviceId) ?: return
        if (entity.manufacturer != manufacturer.name) {
            deviceDao.insertOrUpdate(entity.copy(manufacturer = manufacturer.name))
        }
    }

    /**
     * Refresh an automatically generated device label from SNMP without
     * overwriting a name entered manually by the user.
     */
    suspend fun updateDeviceSnmpName(deviceId: String, snmpName: String) {
        val cleanName = snmpName.trim().take(64)
        if (cleanName.isBlank()) return
        val entity = deviceDao?.getDeviceById(deviceId) ?: return
        val currentName = entity.name.trim()
        val isGeneratedName = currentName.startsWith("Host (", ignoreCase = true) ||
            currentName.startsWith("Device (", ignoreCase = true) ||
            currentName.startsWith("MikroTik (", ignoreCase = true) ||
            currentName.startsWith("Ubiquiti (", ignoreCase = true) ||
            currentName.startsWith("Mimosa (", ignoreCase = true) ||
            currentName == entity.ipAddress
        if (isGeneratedName && !cleanName.equals(entity.ipAddress, ignoreCase = true)) {
            deviceDao.insertOrUpdate(entity.copy(name = cleanName))
        }
    }

    suspend fun updateDevice(device: NetworkDevice) {
        val folderId = if (device.folderId.isBlank()) DeviceFolderEntity.DEFAULT_FOLDER_ID else device.folderId
        deviceDao?.insertOrUpdate(DeviceEntity.fromDomainModel(device.copy(folderId = folderId)))
    }

    suspend fun updateDeviceFull(
        deviceId: String,
        name: String,
        ipAddress: String,
        manufacturer: Manufacturer,
        deviceType: DeviceType,
        folderId: String,
        apiEnabled: Boolean = false,
        apiPort: Int = 8728,
        apiUsername: String = "",
        apiPassword: String = ""
    ) {
        val entity = deviceDao?.getDeviceById(deviceId) ?: return
        val targetFolder = if (folderId.isBlank()) DeviceFolderEntity.DEFAULT_FOLDER_ID else folderId
        val updated = entity.copy(
            name = name.trim(),
            ipAddress = ipAddress.trim(),
            manufacturer = manufacturer.name,
            deviceType = deviceType.name,
            folderId = targetFolder,
            apiEnabled = apiEnabled && manufacturer == Manufacturer.MIKROTIK,
            apiPort = apiPort.coerceIn(1, 65535),
            apiUsername = apiUsername.trim(),
            apiPasswordEncrypted = com.netvision.app.security.ApiCredentialCipher.encrypt(apiPassword) ?: entity.apiPasswordEncrypted
        )
        deviceDao.insertOrUpdate(updated)
        logActivity(
            deviceName = name,
            ipAddress = ipAddress,
            messageEn = "Device configuration updated (${ipAddress}).",
            messageAr = "تم تحديث بيانات الجهاز (${ipAddress}).",
            severity = ActivitySeverity.INFO
        )
    }

    suspend fun addManualDevice(
        name: String,
        ipAddress: String,
        manufacturer: Manufacturer,
        deviceType: DeviceType,
        folderId: String,
        apiEnabled: Boolean = false,
        apiPort: Int = 8728,
        apiUsername: String = "",
        apiPassword: String = ""
    ): NetworkDevice {
        val targetFolder = if (folderId.isBlank()) DeviceFolderEntity.DEFAULT_FOLDER_ID else folderId
        val newDevice = NetworkDevice(
            id = "dev-${System.currentTimeMillis()}-${(100..999).random()}",
            name = name.trim(),
            ipAddress = ipAddress.trim(),
            macAddress = "00:00:00:00:00:00",
            manufacturer = manufacturer,
            model = if (manufacturer == Manufacturer.MIKROTIK) "RouterOS" else if (manufacturer == Manufacturer.UBIQUITI) "AirOS" else "Standard Device",
            deviceType = deviceType,
            status = DeviceStatus.OFFLINE,
            firmwareVersion = "v1.0.0",
            folderId = targetFolder,
            apiEnabled = apiEnabled && manufacturer == Manufacturer.MIKROTIK,
            apiPort = apiPort.coerceIn(1, 65535),
            apiUsername = apiUsername.trim(),
            apiPassword = apiPassword
        )
        saveDevice(newDevice)
        return newDevice
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
            lastPingTimestamp = System.currentTimeMillis(),
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

    suspend fun updateDeviceTelemetry(
        deviceId: String,
        uptime: String?,
        snmpVersion: String?,
        cpuUsagePercent: Int? = null,
        memoryUsagePercent: Int? = null
    ) {
        val entity = deviceDao?.getDeviceById(deviceId) ?: return
        deviceDao.insertOrUpdate(entity.copy(
            uptimeFormatted = uptime ?: entity.uptimeFormatted,
            snmpVersion = snmpVersion ?: entity.snmpVersion,
            cpuUsagePercent = cpuUsagePercent ?: entity.cpuUsagePercent,
            memoryUsagePercent = memoryUsagePercent ?: entity.memoryUsagePercent
        ))
    }

    // -------------------------------------------------------------
    // Device Folders Management (Room Database)
    // -------------------------------------------------------------

    fun getAllFolders(): Flow<List<DeviceFolder>> {
        val foldersFlow = deviceFolderDao?.getAllFolders() ?: flowOf(emptyList())
        val devicesFlow = deviceDao?.getAllDevices() ?: flowOf(emptyList())

        return combine(foldersFlow, devicesFlow) { folderEntities, deviceEntities ->
            // Count devices per folder
            val counts = mutableMapOf<String, Int>()
            for (dev in deviceEntities) {
                val fId = if (dev.folderId.isBlank()) DeviceFolderEntity.DEFAULT_FOLDER_ID else dev.folderId
                counts[fId] = (counts[fId] ?: 0) + 1
            }

            val list = mutableListOf<DeviceFolder>()

            // Always ensure the default "uncategorized" folder is present first
            val uncategorizedEntity = folderEntities.find { it.id == DeviceFolderEntity.DEFAULT_FOLDER_ID }
            val uncatCount = counts[DeviceFolderEntity.DEFAULT_FOLDER_ID] ?: 0
            list.add(
                DeviceFolder(
                    id = DeviceFolderEntity.DEFAULT_FOLDER_ID,
                    name = uncategorizedEntity?.name ?: DeviceFolderEntity.DEFAULT_FOLDER_NAME,
                    deviceCount = uncatCount,
                    createdAt = 0L
                )
            )

            // Add custom user folders
            folderEntities
                .filter { it.id != DeviceFolderEntity.DEFAULT_FOLDER_ID }
                .forEach { entity ->
                    list.add(entity.toDomainModel(deviceCount = counts[entity.id] ?: 0))
                }

            list
        }
    }

    suspend fun createFolder(name: String): String {
        val folderId = "fld-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}"
        val entity = DeviceFolderEntity(
            id = folderId,
            name = name.trim(),
            createdAt = System.currentTimeMillis()
        )
        deviceFolderDao?.insertOrUpdate(entity)
        logActivity(
            deviceName = name,
            ipAddress = "Folder System",
            messageEn = "New device folder created: '${name.trim()}'.",
            messageAr = "تم إنشاء حافظة أجهزة جديدة: '${name.trim()}'.",
            severity = ActivitySeverity.SUCCESS
        )
        return folderId
    }

    suspend fun updateFolderName(folderId: String, newName: String) {
        val existing = deviceFolderDao?.getFolderById(folderId) ?: return
        deviceFolderDao.update(existing.copy(name = newName.trim()))
        logActivity(
            deviceName = newName,
            ipAddress = "Folder System",
            messageEn = "Folder name updated to '${newName.trim()}'.",
            messageAr = "تم تعديل اسم الحافظة إلى '${newName.trim()}'.",
            severity = ActivitySeverity.INFO
        )
    }

    suspend fun deleteFolder(folderId: String) {
        if (folderId == DeviceFolderEntity.DEFAULT_FOLDER_ID) {
            // Cannot delete default uncategorized folder
            return
        }
        val folder = deviceFolderDao?.getFolderById(folderId)
        val folderName = folder?.name ?: folderId

        // Critical Safety: Move all devices inside this folder to "uncategorized" so no devices are deleted!
        deviceDao?.reassignFolderDevices(
            oldFolderId = folderId,
            newFolderId = DeviceFolderEntity.DEFAULT_FOLDER_ID
        )

        // Delete the folder entity from database
        deviceFolderDao?.deleteById(folderId)

        logActivity(
            deviceName = folderName,
            ipAddress = "Folder System",
            messageEn = "Folder '${folderName}' deleted. All contained devices were safely moved to 'Uncategorized'.",
            messageAr = "تم حذف الحافظة '${folderName}' ونقل جميع أجهزتها بأمان إلى حافظة 'غير مصنف'.",
            severity = ActivitySeverity.WARNING
        )
    }

    suspend fun moveDeviceToFolder(deviceId: String, newFolderId: String) {
        val targetFolder = if (newFolderId.isBlank()) DeviceFolderEntity.DEFAULT_FOLDER_ID else newFolderId
        deviceDao?.updateDeviceFolder(deviceId, targetFolder)
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
