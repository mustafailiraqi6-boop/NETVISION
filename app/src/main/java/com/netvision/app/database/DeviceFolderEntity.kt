package com.netvision.app.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.netvision.app.models.DeviceFolder

@Entity(tableName = "device_folders")
data class DeviceFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomainModel(deviceCount: Int = 0): DeviceFolder {
        return DeviceFolder(
            id = id,
            name = name,
            deviceCount = deviceCount,
            createdAt = createdAt
        )
    }

    companion object {
        const val DEFAULT_FOLDER_ID = "uncategorized"
        const val DEFAULT_FOLDER_NAME = "غير مصنف"

        fun defaultFolder(): DeviceFolderEntity {
            return DeviceFolderEntity(
                id = DEFAULT_FOLDER_ID,
                name = DEFAULT_FOLDER_NAME,
                createdAt = 0L
            )
        }

        fun fromDomainModel(folder: DeviceFolder): DeviceFolderEntity {
            return DeviceFolderEntity(
                id = folder.id,
                name = folder.name,
                createdAt = folder.createdAt
            )
        }
    }
}
