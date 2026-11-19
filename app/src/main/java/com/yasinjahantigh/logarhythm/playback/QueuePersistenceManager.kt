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

        val uniqueIds = saved.trackIds.distinct()
        val entities = if (uniqueIds.size <= SQLITE_IN_CHUNK_SIZE) {
            musicDao.getTracksByIdsOnce(uniqueIds)
        } else {
            uniqueIds.chunked(SQLITE_IN_CHUNK_SIZE).flatMap { chunk ->
                musicDao.getTracksByIdsOnce(chunk)
            }
        }
        val trackById = HashMap<String, Track>(entities.size * 2)
        for (i in entities.indices) {
            val domain = entities[i].toDomain()
            trackById[domain.id] = domain
        }
        val tracks = ArrayList<Track>(saved.trackIds.size)
        for (i in saved.trackIds.indices) {
            trackById[saved.trackIds[i]]?.let { tracks.add(it) }
        }

        if (tracks.isEmpty()) return@withContext null
        val savedIndex = saved.currentIndex.coerceIn(0, saved.trackIds.lastIndex)
        var safeIndex = 0
        for (i in 0 until savedIndex) {
            if (saved.trackIds[i] in trackById) safeIndex++
        }
        safeIndex = safeIndex.coerceAtMost(tracks.lastIndex)
        val safePos = if (saved.trackIds[savedIndex] in trackById) saved.positionMs.coerceAtLeast(0L) else 0L

        val original = if (!saved.shuffle && saved.originalTrackIds == saved.trackIds) {
            tracks
        } else {
            // O(N) reconstruction preserving exact duplicate occurrences and leftover ordering.
            val buckets = HashMap<String, ArrayDeque<Int>>(tracks.size * 2)
            for (i in tracks.indices) {
                buckets.getOrPut(tracks[i].id) { ArrayDeque(1) }.addLast(i)
            }
            val consumed = BooleanArray(tracks.size)
            val result = ArrayList<Track>(tracks.size)
            for (i in saved.originalTrackIds.indices) {
                val deque = buckets[saved.originalTrackIds[i]]
                if (deque != null && deque.isNotEmpty()) {
                    val idx = deque.removeFirst()
                    consumed[idx] = true
                    result.add(tracks[idx])
                }
            }
            for (i in tracks.indices) {
                if (!consumed[i]) result.add(tracks[i])
            }
            result
        }
        RestoredQueue(tracks, safeIndex, safePos, saved.shuffle, original)
    }

    private companion object {
        const val SQLITE_IN_CHUNK_SIZE = 500
    }
}
