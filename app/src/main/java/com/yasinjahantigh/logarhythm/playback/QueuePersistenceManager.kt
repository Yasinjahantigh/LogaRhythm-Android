package com.yasinjahantigh.logarhythm.playback

import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.data.room.MusicDao
import com.yasinjahantigh.logarhythm.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class RestoredQueue(
    val tracks: List<Track>,
    val currentIndex: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val originalTracks: List<Track>
)

@Singleton
class QueuePersistenceManager @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val musicDao: MusicDao
) {
    suspend fun saveQueue(
        tracks: List<Track>, currentIndex: Int, positionMs: Long,
        shuffle: Boolean, originalTracks: List<Track>
    ) = withContext(Dispatchers.IO) {
        preferencesManager.saveQueue(tracks.map { it.id }, currentIndex, positionMs, shuffle, originalTracks.map { it.id })
    }

    suspend fun restoreQueue(): RestoredQueue? = withContext(Dispatchers.IO) {
        val saved = preferencesManager.savedQueue.first()
        if (saved.trackIds.isEmpty()) return@withContext null

        val entities = musicDao.getTracksByIdsOnce(saved.trackIds)
        val entityMap = entities.associateBy { it.id }
        val tracks = saved.trackIds.mapNotNull { id -> entityMap[id]?.toDomain() }

        if (tracks.isEmpty()) return@withContext null
        val savedIndex = saved.currentIndex.coerceIn(0, saved.trackIds.lastIndex)
        val safeIndex = saved.trackIds.take(savedIndex).count { it in entityMap }.coerceAtMost(tracks.lastIndex)
        val safePos = if (saved.trackIds[savedIndex] in entityMap) saved.positionMs.coerceAtLeast(0L) else 0L
        val remaining = tracks.toMutableList()
        val original = saved.originalTrackIds.mapNotNull { id ->
            val index = remaining.indexOfFirst { it.id == id }
            if (index < 0) null else remaining.removeAt(index)
        } + remaining
        RestoredQueue(tracks, safeIndex, safePos, saved.shuffle, original)
    }
}
