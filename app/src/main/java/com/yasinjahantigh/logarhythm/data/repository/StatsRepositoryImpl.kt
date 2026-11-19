package com.yasinjahantigh.logarhythm.data.repository

import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.data.room.MusicDao
import com.yasinjahantigh.logarhythm.data.room.PlayEventEntity
import com.yasinjahantigh.logarhythm.domain.library.ArtistSplitConfig
import com.yasinjahantigh.logarhythm.domain.library.ArtistSplitter
import com.yasinjahantigh.logarhythm.domain.model.AlbumStat
import com.yasinjahantigh.logarhythm.domain.model.ArtistStat
import com.yasinjahantigh.logarhythm.domain.model.PlayEvent
import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.StatsSortBy
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.model.TrackStat
import com.yasinjahantigh.logarhythm.domain.repository.StatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepositoryImpl @Inject constructor(
    private val musicDao: MusicDao,
    private val preferencesManager: PreferencesManager
) : StatsRepository {

    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        repoScope.launch(Dispatchers.IO) {
            repairMultiLoopPlayEvents()
        }
    }

    private val allPlayEventsShared: Flow<List<PlayEvent>> = musicDao.getAllPlayEvents()
        .distinctUntilChanged()
        .map { entities ->
            entities.map { entity ->
                PlayEvent(
                    id = entity.id,
                    trackId = entity.trackId,
                    timestamp = entity.timestamp,
                    listenedMs = entity.listenedMs,
                    skipped = entity.skipped
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .shareIn(repoScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 0L, replayExpirationMillis = 0L), replay = 1)

    override suspend fun recordPlayEvent(event: PlayEvent) {
        musicDao.insertPlayEvent(
            PlayEventEntity(
                trackId = event.trackId,
                timestamp = event.timestamp,
                listenedMs = event.listenedMs,
                skipped = event.skipped
            )
        )
    }

    override suspend fun updatePlayEventListened(trackId: String, timestamp: Long, listenedMs: Long) {
        musicDao.updatePlayEventListened(trackId, timestamp, listenedMs)
    }

    override fun getPlayEventsForTrack(trackId: String): Flow<List<PlayEvent>> {
        return musicDao.getPlayEventsForTrack(trackId).map { entities ->
            entities.map { entity ->
                PlayEvent(
                    id = entity.id,
                    trackId = entity.trackId,
                    timestamp = entity.timestamp,
                    listenedMs = entity.listenedMs,
                    skipped = entity.skipped
                )
            }
        }
    }

    override fun getAllPlayEvents(): Flow<List<PlayEvent>> = allPlayEventsShared

    override fun getTotalListenTimeMs(sinceTimestamp: Long, untilTimestamp: Long): Flow<Long> {
        return when {
            sinceTimestamp <= 0L && untilTimestamp == Long.MAX_VALUE -> musicDao.getTotalListenTimeMs()
            untilTimestamp == Long.MAX_VALUE -> musicDao.getTotalListenTimeMs(sinceTimestamp)
            else -> musicDao.getTotalListenTimeMsInRange(sinceTimestamp, untilTimestamp)
        }
    }

    override fun getCompletedPlayCount(sinceTimestamp: Long, untilTimestamp: Long): Flow<Int> =
        musicDao.getCompletedPlayCountInRange(sinceTimestamp, untilTimestamp)

    override fun getPeakHour(sinceTimestamp: Long, untilTimestamp: Long): Flow<Int?> =
        musicDao.getPlayTimestampsInRange(sinceTimestamp, untilTimestamp).map { timestamps ->
            if (timestamps.isEmpty()) return@map null
            val counts = IntArray(24)
            val cal = java.util.Calendar.getInstance()
            for (ts in timestamps) {
                cal.timeInMillis = ts
                counts[cal.get(java.util.Calendar.HOUR_OF_DAY)]++
            }
            var bestHour = 0
            for (h in 1 until 24) {
                if (counts[h] > counts[bestHour]) bestHour = h
            }
            bestHour
        }

    override fun getTopTracks(
        sinceTimestamp: Long,
        limit: Int,
        untilTimestamp: Long,
        sortBy: StatsSortBy
    ): Flow<List<TrackStat>> {
        val source = when (sortBy) {
            StatsSortBy.PLAY_COUNT -> if (untilTimestamp == Long.MAX_VALUE) {
                musicDao.getTopTracks(sinceTimestamp, limit)
            } else {
                musicDao.getTopTracksInRange(sinceTimestamp, untilTimestamp, limit)
            }
            StatsSortBy.LISTEN_TIME -> if (untilTimestamp == Long.MAX_VALUE) {
                musicDao.getTopTracksByTime(sinceTimestamp, limit)
            } else {
                musicDao.getTopTracksInRangeByTime(sinceTimestamp, untilTimestamp, limit)
            }
        }
        return source.map { list ->
            list.map { query ->
                TrackStat(
                    track = Track(
                        id = query.id,
                        title = query.title,
                        artist = query.artist,
                        album = query.album,
                        durationMs = query.durationMs,
                        artworkUri = query.artworkUri,
                        sourceType = if (query.sourceType == SourceType.LOCAL.prefix) {
                            SourceType.LOCAL
                        } else {
                            SourceType.WEB
                        },
                        uri = query.uri,
                        dateModified = query.dateModified,
                        dateAdded = query.dateAdded,
                        folder = query.folder,
                        genre = query.genre
                    ),
                    playCount = query.playCount,
                    totalListenTimeMs = query.totalListenedMs
                )
            }
        }
    }

    override fun getTopArtists(
        sinceTimestamp: Long,
        limit: Int,
        untilTimestamp: Long,
        sortBy: StatsSortBy
    ): Flow<List<ArtistStat>> {
        val configFlow = combine(
            preferencesManager.splitArtists,
            preferencesManager.artistSeparators,
            preferencesManager.artistNeverSplit
        ) { enabled, raw, neverSplit ->
            if (enabled) {
                ArtistSplitConfig(
                    enabled = true,
                    separators = ArtistSplitter.parseSeparators(raw),
                    neverSplit = neverSplit
                )
            } else {
                ArtistSplitConfig.OFF
            }
        }
        val source = when (sortBy) {
            StatsSortBy.PLAY_COUNT -> if (untilTimestamp == Long.MAX_VALUE) {
                musicDao.getTopArtists(sinceTimestamp, limit * 3)
            } else {
                musicDao.getTopArtistsInRange(sinceTimestamp, untilTimestamp, limit * 3)
            }
            StatsSortBy.LISTEN_TIME -> if (untilTimestamp == Long.MAX_VALUE) {
                musicDao.getTopArtistsByTime(sinceTimestamp, limit * 3)
            } else {
                musicDao.getTopArtistsInRangeByTime(sinceTimestamp, untilTimestamp, limit * 3)
            }
        }
        // Read extra raw rows so collaborations can merge into both artists after splitting.
        return combine(source, configFlow) { list, config ->
            if (!config.enabled) {
                return@combine list.take(limit).map { query ->
                    ArtistStat(
                        artist = query.artist,
                        playCount = query.playCount,
                        totalListenTimeMs = query.totalListenedMs,
                        trackCount = query.trackCount
                    )
                }
            }
            data class Acc(
                val name: String,
                var playCount: Int = 0,
                var listenedMs: Long = 0,
                var trackCount: Int = 0
            )
            val byName = LinkedHashMap<String, Acc>()
            for (query in list) {
                for (name in config.namesOf(query.artist)) {
                    val acc = byName.getOrPut(name.lowercase()) { Acc(name) }
                    acc.playCount += query.playCount
                    acc.listenedMs += query.totalListenedMs
                    acc.trackCount += query.trackCount
                }
            }
            val comparator = when (sortBy) {
                StatsSortBy.PLAY_COUNT -> compareByDescending<Acc> { it.playCount }.thenByDescending { it.listenedMs }
                StatsSortBy.LISTEN_TIME -> compareByDescending<Acc> { it.listenedMs }.thenByDescending { it.playCount }
            }
            byName.values
                .sortedWith(comparator)
                .take(limit)
                .map {
                    ArtistStat(
                        artist = it.name,
                        playCount = it.playCount,
                        totalListenTimeMs = it.listenedMs,
                        trackCount = it.trackCount
                    )
                }
        }
    }

    override fun getTopAlbums(
        sinceTimestamp: Long,
        limit: Int,
        untilTimestamp: Long,
        sortBy: StatsSortBy
    ): Flow<List<AlbumStat>> {
        val source = when (sortBy) {
            StatsSortBy.PLAY_COUNT -> if (untilTimestamp == Long.MAX_VALUE) {
                musicDao.getTopAlbums(sinceTimestamp, limit)
            } else {
                musicDao.getTopAlbumsInRange(sinceTimestamp, untilTimestamp, limit)
            }
            StatsSortBy.LISTEN_TIME -> if (untilTimestamp == Long.MAX_VALUE) {
                musicDao.getTopAlbumsByTime(sinceTimestamp, limit)
            } else {
                musicDao.getTopAlbumsInRangeByTime(sinceTimestamp, untilTimestamp, limit)
            }
        }
        return source.map { list ->
            list.map { query ->
                AlbumStat(
                    album = query.album,
                    artist = query.artist,
                    artworkUri = query.artworkUri,
                    playCount = query.playCount,
                    totalListenTimeMs = query.totalListenedMs,
                    trackCount = query.trackCount
                )
            }
        }
    }

    override fun getRecentlyPlayedTracks(limit: Int): Flow<List<Track>> {
        return musicDao.getRecentlyPlayedTracks(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getMostPlayedTracks(limit: Int): Flow<List<Track>> {
        return musicDao.getMostPlayedTracks(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    private suspend fun repairMultiLoopPlayEvents() {
        try {
            val tracks = musicDao.getAllTracksOnce().associateBy { it.id }
            val events = musicDao.getAllPlayEventsOnce()
            val newEventsToInsert = mutableListOf<PlayEventEntity>()

            for (event in events) {
                if (event.skipped) continue
                val track = tracks[event.trackId] ?: continue
                val dur = track.durationMs
                if (dur < 20_000L) continue

                // If listenedMs is significantly greater than duration (e.g. >= 1.8 * duration),
                // it accumulated multiple repeat plays into a single event due to the previous repeat bug.
                val playCount = (event.listenedMs / dur).toInt()
                if (playCount > 1) {
                    musicDao.updatePlayEventListenedById(event.id, dur)
                    for (i in 1 until playCount) {
                        val remainingMs = if (i == playCount - 1) {
                            (event.listenedMs - (i * dur)).coerceIn(
                                com.yasinjahantigh.logarhythm.domain.stats.ListenPolicy.playThresholdMs(dur),
                                dur
                            )
                        } else {
                            dur
                        }
                        newEventsToInsert.add(
                            PlayEventEntity(
                                trackId = event.trackId,
                                timestamp = event.timestamp + (i * dur),
                                listenedMs = remainingMs,
                                skipped = false
                            )
                        )
                    }
                }
            }

            if (newEventsToInsert.isNotEmpty()) {
                musicDao.insertPlayEvents(newEventsToInsert)
            }
        } catch (_: Exception) {
            // Defensive: do not fail repo initialization if repair encounters an issue
        }
    }
}
