package com.yasinjahantigh.logarhythm.data.repository

import com.yasinjahantigh.logarhythm.data.room.MusicDao
import com.yasinjahantigh.logarhythm.data.room.PlayEventEntity
import com.yasinjahantigh.logarhythm.domain.model.AlbumStat
import com.yasinjahantigh.logarhythm.domain.model.ArtistStat
import com.yasinjahantigh.logarhythm.domain.model.PlayEvent
import com.yasinjahantigh.logarhythm.domain.model.SourceType
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.model.TrackStat
import com.yasinjahantigh.logarhythm.domain.repository.StatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatsRepositoryImpl @Inject constructor(
    private val musicDao: MusicDao
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
        return musicDao.getTopArtists(sinceTimestamp, limit).map { list ->
            list.map { query ->
                ArtistStat(
                    artist = query.artist,
                    playCount = query.playCount,
                    totalListenTimeMs = query.totalListenedMs,
                    trackCount = query.trackCount
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
