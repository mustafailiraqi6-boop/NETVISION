package com.netvision.app.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NetworkRangeDao {
    @Query("SELECT * FROM network_ranges ORDER BY createdTimestamp ASC")
    fun getAllRanges(): Flow<List<NetworkRangeEntity>>

    @Query("SELECT * FROM network_ranges WHERE isEnabled = 1")
    fun getEnabledRanges(): Flow<List<NetworkRangeEntity>>

    @Query("SELECT * FROM network_ranges WHERE id = :id")
    suspend fun getRangeById(id: String): NetworkRangeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(range: NetworkRangeEntity)

    @Update
    suspend fun update(range: NetworkRangeEntity)

    @Delete
    suspend fun delete(range: NetworkRangeEntity)

    @Query("DELETE FROM network_ranges WHERE id = :id")
    suspend fun deleteById(id: String)
}
