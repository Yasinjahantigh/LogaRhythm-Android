package com.yasinjahantigh.logarhythm.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceEqDao {

    @Query("SELECT * FROM device_eq_profiles ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DeviceEqProfileEntity>>

    @Query("SELECT * FROM device_eq_profiles WHERE deviceKey = :deviceKey LIMIT 1")
    suspend fun getByKey(deviceKey: String): DeviceEqProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DeviceEqProfileEntity)
}
