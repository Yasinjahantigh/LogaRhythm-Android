package com.yasinjahantigh.logarhythm.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistFollowDao {

    @Query("SELECT * FROM followed_artists ORDER BY followedAt DESC")
    fun observeFollowed(): Flow<List<FollowedArtistEntity>>

    @Query("SELECT * FROM followed_artists ORDER BY followedAt DESC")
    suspend fun getFollowedOnce(): List<FollowedArtistEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM followed_artists WHERE artistKey = :artistKey)")
    fun observeIsFollowed(artistKey: String): Flow<Boolean>

    @Query("SELECT * FROM followed_artists WHERE artistKey = :artistKey LIMIT 1")
    suspend fun getByKey(artistKey: String): FollowedArtistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArtist(entity: FollowedArtistEntity)

    @Query("DELETE FROM followed_artists WHERE artistKey = :artistKey")
    suspend fun deleteArtist(artistKey: String)

    @Query("SELECT * FROM artist_releases ORDER BY isSeen ASC, discoveredAt DESC LIMIT 50")
    fun observeReleases(): Flow<List<ArtistReleaseEntity>>

    @Query("SELECT COUNT(*) FROM artist_releases WHERE isSeen = 0")
    fun observeUnseenCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertReleases(items: List<ArtistReleaseEntity>)

    @Query("UPDATE artist_releases SET isSeen = 1 WHERE isSeen = 0")
    suspend fun markAllSeen()

    @Query("DELETE FROM artist_releases WHERE artistKey = :artistKey")
    suspend fun deleteReleasesForArtist(artistKey: String)
}
