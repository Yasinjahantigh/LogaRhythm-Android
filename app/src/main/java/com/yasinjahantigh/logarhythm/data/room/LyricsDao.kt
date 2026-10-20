package com.yasinjahantigh.logarhythm.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LyricsDao {
    @Query("SELECT * FROM track_lyrics WHERE trackId = :trackId LIMIT 1")
    fun getLyricsForTrack(trackId: String): Flow<LyricsEntity?>

    @Query("SELECT * FROM track_lyrics WHERE trackId = :trackId LIMIT 1")
    suspend fun getLyricsForTrackOnce(trackId: String): LyricsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLyrics(lyrics: LyricsEntity)

    @Query("DELETE FROM track_lyrics WHERE trackId = :trackId")
    suspend fun deleteLyricsForTrack(trackId: String)

    @Query("SELECT trackId FROM track_lyrics WHERE source = 'deleted'")
    fun getHiddenTrackIds(): Flow<List<String>>

    @Query("SELECT * FROM track_lyrics")
    suspend fun getAllLyricsOnce(): List<LyricsEntity>

    @Query("SELECT offsetMs FROM lyrics_offsets WHERE trackId = :trackId")
    fun getOffset(trackId: String): Flow<Long?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setOffset(entity: LyricsOffsetEntity)

    @Query("DELETE FROM lyrics_offsets WHERE trackId = :trackId")
    suspend fun clearOffset(trackId: String)

    @Query("SELECT * FROM lyrics_offsets")
    suspend fun getAllOffsetsOnce(): List<LyricsOffsetEntity>
}
