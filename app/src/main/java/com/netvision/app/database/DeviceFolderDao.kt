package com.netvision.app.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceFolderDao {
    @Query("SELECT * FROM device_folders ORDER BY createdAt ASC")
    fun getAllFolders(): Flow<List<DeviceFolderEntity>>

    @Query("SELECT * FROM device_folders WHERE id = :folderId")
    suspend fun getFolderById(folderId: String): DeviceFolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(folder: DeviceFolderEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaultFolder(folder: DeviceFolderEntity)

    @Update
    suspend fun update(folder: DeviceFolderEntity)

    @Delete
    suspend fun delete(folder: DeviceFolderEntity)

    @Query("DELETE FROM device_folders WHERE id = :folderId")
    suspend fun deleteById(folderId: String)
}
