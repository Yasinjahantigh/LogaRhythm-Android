package com.yasinjahantigh.logarhythm.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadCatalogDao {

    @Query("SELECT * FROM download_catalog_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getByKey(cacheKey: String): DownloadCatalogEntity?

    @Query("SELECT * FROM download_catalog_cache WHERE kind IN ('album', 'artist') ORDER BY updatedAt DESC LIMIT 30")
    fun observeSavedCollections(): Flow<List<DownloadCatalogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadCatalogEntity)

    @Query("DELETE FROM download_catalog_cache WHERE cacheKey = :cacheKey")
    suspend fun deleteByKey(cacheKey: String)
}
