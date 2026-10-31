package com.netvision.app.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.netvision.app.models.NetworkRange

@Entity(tableName = "network_ranges")
data class NetworkRangeEntity(
    @PrimaryKey val id: String,
    val cidr: String,
    val label: String,
    val isEnabled: Boolean = true,
    val createdTimestamp: Long = System.currentTimeMillis()
) {
    fun toDomainModel(): NetworkRange {
        return NetworkRange(
            id = id,
            cidr = cidr,
            label = label,
            isEnabled = isEnabled
        )
    }

    companion object {
        fun fromDomainModel(range: NetworkRange): NetworkRangeEntity {
            return NetworkRangeEntity(
                id = range.id,
                cidr = range.cidr,
                label = range.label,
                isEnabled = range.isEnabled
            )
        }
    }
}
