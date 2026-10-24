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
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.model.TrackStat
import com.yasinjahantigh.logarhythm.domain.repository.StatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepositoryImpl @Inject constructor(
    private val musicDao: MusicDao,
    private val preferencesManager: PreferencesManager
) : StatsRepository {

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

    override fun getAllPlayEvents(): Flow<List<PlayEvent>> {
        return musicDao.getAllPlayEvents().map { entities ->
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

    override fun getTotalListenTimeMs(sinceTimestamp: Long): Flow<Long> {
        return if (sinceTimestamp <= 0L) {
            musicDao.getTotalListenTimeMs()
        } else {
            musicDao.getTotalListenTimeMs(sinceTimestamp)
        }
    }

    override fun getTopTracks(sinceTimestamp: Long, limit: Int): Flow<List<TrackStat>> {
        return musicDao.getTopTracks(sinceTimestamp, limit).map { list ->
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

    override fun getTopArtists(sinceTimestamp: Long, limit: Int): Flow<List<ArtistStat>> {
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
        // Read extra raw rows so collaborations can merge into both artists after splitting.
        return combine(musicDao.getTopArtists(sinceTimestamp, limit * 3), configFlow) { list, config ->
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
            byName.values
                .sortedWith(
                    compareByDescending<Acc> { it.playCount }
                        .thenByDescending { it.listenedMs }
                )
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

    override fun getTopAlbums(sinceTimestamp: Long, limit: Int): Flow<List<AlbumStat>> {
        return musicDao.getTopAlbums(sinceTimestamp, limit).map { list ->
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
}
