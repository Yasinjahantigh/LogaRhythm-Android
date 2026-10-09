package com.example.musicplayer.playback

import com.example.musicplayer.data.datastore.PreferencesManager
import com.example.musicplayer.data.room.MusicDao
import com.example.musicplayer.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class RestoredQueue(
    val tracks: List<Track>,
    val currentIndex: Int,
    val positionMs: Long
)

@Singleton
class QueuePersistenceManager @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val musicDao: MusicDao
) {
    suspend fun saveQueue(tracks: List<Track>, currentIndex: Int, positionMs: Long) =
        withContext(Dispatchers.IO) {
            val ids = tracks.map { it.id }
            preferencesManager.saveQueue(ids, currentIndex, positionMs)
        }

    suspend fun restoreQueue(): RestoredQueue? = withContext(Dispatchers.IO) {
        val saved = preferencesManager.savedQueue.first()
        if (saved.trackIds.isEmpty()) return@withContext null

        val entities = musicDao.getTracksByIdsOnce(saved.trackIds)
        val entityMap = entities.associateBy { it.id }
        val tracks = saved.trackIds.mapNotNull { id -> entityMap[id]?.toDomain() }

        if (tracks.isEmpty()) return@withContext null
        val safeIndex = saved.currentIndex.coerceIn(0, tracks.lastIndex)
        val safePos = saved.positionMs.coerceAtLeast(0L)
        RestoredQueue(tracks, safeIndex, safePos)
    }
}
