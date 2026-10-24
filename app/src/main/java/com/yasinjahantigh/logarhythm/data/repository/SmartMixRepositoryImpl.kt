package com.yasinjahantigh.logarhythm.data.repository

import com.yasinjahantigh.logarhythm.data.room.LyricsDao
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.model.TrackPlayStat
import com.yasinjahantigh.logarhythm.domain.repository.SmartMixRepository
import com.yasinjahantigh.logarhythm.domain.repository.TrackRepository
import com.yasinjahantigh.logarhythm.domain.smart.SmartMix
import com.yasinjahantigh.logarhythm.domain.smart.SmartMixBuilder
import com.yasinjahantigh.logarhythm.domain.smart.SmartMixType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SmartMixRepositoryImpl @Inject constructor(
    private val trackRepository: TrackRepository,
    private val lyricsDao: LyricsDao
) : SmartMixRepository {

    private data class Snapshot(
        val tracks: List<Track>,
        val playStats: Map<String, TrackPlayStat>,
        val favoriteIds: Set<String>,
        val downloadedUris: Set<String>,
        val lyricsTrackIds: Set<String>
    )

    private fun snapshot(): Flow<Snapshot> = combine(
        trackRepository.getAllTracks(),
        trackRepository.getPlayStats(),
        trackRepository.getFavoriteTrackIds(),
        trackRepository.getDownloadedTrackUris(),
        lyricsDao.getTrackIdsWithLyrics()
    ) { tracks, stats, favorites, downloads, lyricsIds ->
        Snapshot(tracks, stats, favorites, downloads, lyricsIds.toSet())
    }.flowOn(Dispatchers.Default)

    private fun build(snapshot: Snapshot, type: SmartMixType): List<Track> =
        SmartMixBuilder.build(
            type = type,
            tracks = snapshot.tracks,
            playStats = snapshot.playStats,
            favoriteIds = snapshot.favoriteIds,
            downloadedUris = snapshot.downloadedUris,
            lyricsTrackIds = snapshot.lyricsTrackIds
        )

    override fun getSmartMixes(): Flow<List<SmartMix>> = snapshot().map { snapshot ->
        SmartMixType.entries.map { type ->
            val tracks = build(snapshot, type)
            SmartMix(
                type = type,
                trackCount = tracks.size,
                artworkUris = SmartMixBuilder.artworkOf(tracks)
            )
        }
    }

    override fun getSmartMix(type: SmartMixType): Flow<SmartMix?> = snapshot().map { snapshot ->
        val tracks = build(snapshot, type)
        SmartMix(
            type = type,
            trackCount = tracks.size,
            artworkUris = SmartMixBuilder.artworkOf(tracks)
        )
    }

    override fun getSmartMixTracks(type: SmartMixType): Flow<List<Track>> =
        snapshot().map { snapshot -> build(snapshot, type) }
}
