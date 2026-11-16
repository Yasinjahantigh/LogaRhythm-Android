package com.yasinjahantigh.logarhythm.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedQueueDao {

    @Query("SELECT * FROM saved_queues ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SavedQueueEntity>>

    @Query("SELECT * FROM saved_queues WHERE queueId = :id LIMIT 1")
    suspend fun getById(id: String): SavedQueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SavedQueueEntity)

    @Query("DELETE FROM saved_queues WHERE queueId = :id")
    suspend fun delete(id: String)
}
