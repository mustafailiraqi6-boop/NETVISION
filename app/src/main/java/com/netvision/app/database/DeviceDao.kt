package com.netvision.app.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {
    @Query("SELECT * FROM devices ORDER BY lastSeenTimestamp DESC")
    fun getAllDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE id = :deviceId")
    suspend fun getDeviceById(deviceId: String): DeviceEntity?

    @Query("SELECT * FROM devices WHERE ipAddress = :ipAddress LIMIT 1")
    suspend fun getDeviceByIpAddress(ipAddress: String): DeviceEntity?

    @Query("SELECT * FROM devices WHERE manufacturer = :manufacturer")
    fun getDevicesByManufacturer(manufacturer: String): Flow<List<DeviceEntity>>

    @Query("SELECT COUNT(*) FROM devices")
    fun getDevicesCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM devices WHERE status = 'ONLINE'")
    fun getOnlineCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM devices WHERE status = 'OFFLINE'")
    fun getOfflineCount(): Flow<Int>

    @Query("SELECT * FROM devices WHERE folderId = :folderId ORDER BY lastSeenTimestamp DESC")
    fun getDevicesByFolder(folderId: String): Flow<List<DeviceEntity>>

    @Query("SELECT COUNT(*) FROM devices WHERE folderId = :folderId")
    suspend fun getDeviceCountInFolder(folderId: String): Int

    @Query("UPDATE devices SET folderId = :newFolderId WHERE folderId = :oldFolderId")
    suspend fun reassignFolderDevices(oldFolderId: String, newFolderId: String)

    @Query("UPDATE devices SET folderId = :folderId WHERE id = :deviceId")
    suspend fun updateDeviceFolder(deviceId: String, folderId: String)

    @Query("UPDATE devices SET name = :name, ipAddress = :ipAddress, manufacturer = :manufacturer, deviceType = :deviceType, folderId = :folderId WHERE id = :deviceId")
    suspend fun updateDeviceInfo(deviceId: String, name: String, ipAddress: String, manufacturer: String, deviceType: String, folderId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(device: DeviceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(devices: List<DeviceEntity>)

    @Update
    suspend fun update(device: DeviceEntity)

    @Delete
    suspend fun delete(device: DeviceEntity)

    @Query("DELETE FROM devices")
    suspend fun clearAll()
}
