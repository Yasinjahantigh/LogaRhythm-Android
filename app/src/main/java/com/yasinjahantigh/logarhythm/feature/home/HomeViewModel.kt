package com.yasinjahantigh.logarhythm.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yasinjahantigh.logarhythm.core.library.LibrarySync
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.data.radar.ArtistFollowRepository
import com.yasinjahantigh.logarhythm.data.radar.RadarRefreshSummary
import com.yasinjahantigh.logarhythm.data.radar.ReleaseRadarNotifications
import com.yasinjahantigh.logarhythm.data.radar.ReleaseRadarService
import com.yasinjahantigh.logarhythm.data.repository.ArtistPhotoRepository
import com.yasinjahantigh.logarhythm.data.room.ArtistReleaseEntity
import com.yasinjahantigh.logarhythm.data.room.FollowedArtistEntity
import com.yasinjahantigh.logarhythm.domain.library.ArtistSplitter
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import com.yasinjahantigh.logarhythm.domain.curation.TrackCuration
import com.yasinjahantigh.logarhythm.domain.discovery.Discovery
import com.yasinjahantigh.logarhythm.domain.mix.DailyMixGenerator
import com.yasinjahantigh.logarhythm.domain.model.DailyMix
import com.yasinjahantigh.logarhythm.domain.model.Playlist
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.repository.PlaylistRepository
import com.yasinjahantigh.logarhythm.domain.repository.StatsRepository
import com.yasinjahantigh.logarhythm.domain.repository.TrackRepository
import com.yasinjahantigh.logarhythm.domain.session.ListeningSessionPlanner
import com.yasinjahantigh.logarhythm.domain.taste.TasteProfile
import com.yasinjahantigh.logarhythm.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import java.util.Calendar
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    statsRepository: StatsRepository,
    private val trackRepository: TrackRepository,
    private val playlistRepository: PlaylistRepository,
    private val playerController: PlayerController,
    private val librarySync: LibrarySync,
    private val preferencesManager: PreferencesManager,
    private val artistFollowRepository: ArtistFollowRepository,
    private val releaseRadarNotifications: ReleaseRadarNotifications,
    private val artistPhotoRepository: ArtistPhotoRepository
) : ViewModel() {

    val followedArtists: StateFlow<List<FollowedArtistEntity>> = artistFollowRepository.observeFollowed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val radarReleases: StateFlow<List<ArtistReleaseEntity>> = artistFollowRepository.observeReleases()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val radarUnseenCount: StateFlow<Int> = artistFollowRepository.observeUnseenCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val releaseRadarEnabled: StateFlow<Boolean> = preferencesManager.releaseRadarEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _radarRefreshing = MutableStateFlow(false)
    val radarRefreshing: StateFlow<Boolean> = _radarRefreshing.asStateFlow()

    fun refreshReleaseRadar(onResult: (RadarRefreshSummary) -> Unit) {
        if (_radarRefreshing.value) return
        viewModelScope.launch {
            _radarRefreshing.value = true
            try {
                val summary = artistFollowRepository.refreshAll()
                if (summary.newReleases.isNotEmpty()) {
                    releaseRadarNotifications.notifyReleases(summary.newReleases)
                }
                onResult(summary)
            } finally {
                _radarRefreshing.value = false
            }
        }
    }

    fun markRadarSeen() {
        viewModelScope.launch { artistFollowRepository.markAllSeen() }
    }

    fun unfollowArtist(artistKey: String) {
        viewModelScope.launch { artistFollowRepository.unfollow(artistKey) }
    }

    fun setReleaseRadarEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setReleaseRadarEnabled(enabled) }
    }

    fun downloadRadarRelease(release: ArtistReleaseEntity, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val queued = runCatching { artistFollowRepository.downloadRelease(release) }.getOrDefault(false)
            onResult(queued)
        }
    }

    fun dismissRadarRelease(release: ArtistReleaseEntity) {
        viewModelScope.launch {
            artistFollowRepository.dismissRelease(release)
        }
    }

    fun releaseToSearchResult(release: ArtistReleaseEntity): DownloadSearchResult =
        artistFollowRepository.releaseToSearchResult(release)

    fun findLocalTrack(artistName: String, title: String): Track? {
        val tracks = allTracks.value
        if (tracks.isEmpty() || title.isBlank()) return null
        val targetKey = ReleaseRadarService.titleKey(artistName, title)
        tracks.firstOrNull { ReleaseRadarService.titleKey(it.artist, it.title) == targetKey }?.let { return it }
        val normTitle = ReleaseRadarService.normalizeTrackTitle(title)
        return tracks.firstOrNull {
            ReleaseRadarService.normalizeTrackTitle(it.title) == normTitle &&
                ReleaseRadarService.isArtistMatch(it.artist, artistName)
        }
    }

    /** Index the device's music now (after permission is granted or on a manual rescan). */
    fun rescanLibrary() = librarySync.requestRefresh(force = true)


    private val dailyMixGenerator = DailyMixGenerator()

    /** One shared library snapshot for Home discovery and smart-mix actions. */
    val allTracks: StateFlow<List<Track>> = trackRepository.getAllTracks()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recentlyAdded: StateFlow<List<Track>> = allTracks
        .map { tracks -> HomeTrackCollections.recentlyAdded(tracks) }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val playlists: StateFlow<List<Playlist>> = playlistRepository.getAllPlaylists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recentlyPlayed: StateFlow<List<Track>> = statsRepository.getRecentlyPlayedTracks(limit = 20)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val mostPlayed: StateFlow<List<Track>> = statsRepository.getMostPlayedTracks(limit = 20)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val totalListenTimeMs: StateFlow<Long> = statsRepository.getTotalListenTimeMs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0L
        )

    private val allPlayEvents = statsRepository.getAllPlayEvents()

    private val favoritesTrackIds = trackRepository.getFavoriteTrackIds()

    /** What the listener's own history says about their taste; empty until there is history. */
    private val tasteProfile: StateFlow<TasteProfile> = combine(
        allTracks,
        allPlayEvents,
        favoritesTrackIds
    ) { tracks, events, favIds ->
        TasteProfile.build(events, favIds, library = tracks)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TasteProfile.EMPTY
        )

    private val currentDay = flow {
        while (true) {
            emit(System.currentTimeMillis() / TasteProfile.DAY_MS)
            delay(60_000L)
        }
    }.distinctUntilChanged()

    val dailyMixes: StateFlow<List<DailyMix>> = combine(
        allTracks,
        allPlayEvents,
        favoritesTrackIds,
        tasteProfile,
        currentDay
    ) { tracks, events, favIds, profile, seedDate ->
        if (tracks.isEmpty()) return@combine emptyList()
        val exposure = preferencesManager.mixExposureHistory.first()
        val mixes = dailyMixGenerator.generateMixes(
            allTracks = tracks,
            playEvents = events,
            favoriteTrackIds = favIds,
            seedDate = seedDate,
            tasteProfile = profile,
            recentExposureDays = exposure
        )
        if (mixes.isNotEmpty()) {
            val allIds = mixes.flatMap { mix -> mix.tracks.map { it.id } }
            if (allIds.any { exposure[it] != seedDate }) {
                viewModelScope.launch {
                    preferencesManager.recordMixExposure(allIds, seedDate)
                }
            }
        }
        mixes
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    /** Local hour of day, refreshed so Home's daypart follows the clock while the app stays open. */
    private val currentHour = flow {
        while (true) {
            emit(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
            delay(60_000L)
        }
    }.distinctUntilChanged()

    val rightNow: StateFlow<Discovery.RightNow?> = combine(allTracks, tasteProfile, currentHour) { tracks, profile, hour ->
        if (tracks.isEmpty()) null else Discovery.rightNow(tracks, profile, hour)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val onRepeat: StateFlow<List<Track>> = combine(allTracks, allPlayEvents) { tracks, events ->
        Discovery.onRepeat(tracks, events)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val becauseYouPlayed: StateFlow<Discovery.BecauseYouPlayed?> = combine(allTracks, allPlayEvents, tasteProfile) { tracks, events, profile ->
        Discovery.becauseYouPlayed(tracks, events, profile)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val quickPicks: StateFlow<List<QuickPickItem>> = combine(
        allTracks,
        playlists,
        preferencesManager.favoritePlaylistIds,
        dailyMixes,
        mostPlayed,
        favoritesTrackIds,
        artistPhotoRepository.photos
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val tracks = values[0] as List<Track>
        @Suppress("UNCHECKED_CAST")
        val allPlaylists = values[1] as List<Playlist>
        @Suppress("UNCHECKED_CAST")
        val favPlaylistIds = values[2] as Set<Long>
        @Suppress("UNCHECKED_CAST")
        val mixes = values[3] as List<DailyMix>
        @Suppress("UNCHECKED_CAST")
        val topPlayed = values[4] as List<Track>
        @Suppress("UNCHECKED_CAST")
        val favIds = values[5] as Set<String>
        @Suppress("UNCHECKED_CAST")
        val photos = values[6] as Map<String, String>

        if (tracks.isEmpty()) return@combine emptyList()

        val items = mutableListOf<QuickPickItem>()
        val usedIds = HashSet<String>()

        // 1. Liked Songs (Favorites)
        val favTracks = tracks.filter { it.id in favIds }
        if (favTracks.isNotEmpty()) {
            val favArt = favTracks.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri
            items += QuickPickItem(
                id = "favorites",
                kind = QuickPickKind.FAVORITES,
                title = "",
                artworkUri = favArt,
                trackCount = favTracks.size,
                playlistId = Playlist.FAVORITES_ID,
                tracks = favTracks
            )
            usedIds.add("favorites")
        }

        // 2. Pinned favorite playlists
        allPlaylists
            .filter { it.id != Playlist.FAVORITES_ID && it.id in favPlaylistIds && it.trackCount > 0 }
            .take(3)
            .forEach { pl ->
                val id = "pl_${pl.id}"
                if (usedIds.add(id)) {
                    items += QuickPickItem(
                        id = id,
                        kind = QuickPickKind.PLAYLIST,
                        title = pl.name,
                        artworkUri = null,
                        trackCount = pl.trackCount,
                        playlistId = pl.id
                    )
                }
            }

        // 3. Top Daily Mix
        mixes.firstOrNull()?.takeIf { it.tracks.isNotEmpty() }?.let { mix ->
            if (usedIds.add(mix.id)) {
                items += QuickPickItem(
                    id = mix.id,
                    kind = QuickPickKind.DAILY_MIX,
                    title = mix.id,
                    artworkUri = mix.tracks.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri,
                    trackCount = mix.tracks.size,
                    tracks = mix.tracks
                )
            }
        }

        // 4. Top Artist
        val candidateSource = topPlayed.ifEmpty { tracks }
        val topArtistName = candidateSource
            .asSequence()
            .mapNotNull { t -> ArtistSplitter.split(t.artist).firstOrNull()?.trim() }
            .firstOrNull { name ->
                name.isNotBlank() &&
                    !name.equals("<unknown>", ignoreCase = true) &&
                    !name.equals("unknown", ignoreCase = true) &&
                    !name.equals("unknown artist", ignoreCase = true)
            }
        if (!topArtistName.isNullOrBlank()) {
            val artistKey = artistPhotoRepository.normalizeKey(topArtistName)
            val artistTracks = tracks.filter { candidate ->
                ArtistSplitter.split(candidate.artist).any {
                    artistPhotoRepository.normalizeKey(it) == artistKey
                } || artistPhotoRepository.normalizeKey(candidate.artist) == artistKey
            }
            if (artistTracks.isNotEmpty()) {
                val id = "artist_$artistKey"
                if (usedIds.add(id)) {
                    val photoUri = photos[artistKey]
                        ?: artistTracks.firstNotNullOfOrNull { it.artworkUri?.takeIf(String::isNotBlank) }
                    items += QuickPickItem(
                        id = id,
                        kind = QuickPickKind.ARTIST,
                        title = topArtistName,
                        artworkUri = photoUri,
                        trackCount = artistTracks.size,
                        tracks = artistTracks
                    )
                }
            }
        }

        // 5. Fill remaining slots up to 6 with other playlists and Daily Mixes
        if (items.size < 6) {
            for (pl in allPlaylists) {
                if (items.size >= 6) break
                if (pl.id == Playlist.FAVORITES_ID || pl.trackCount <= 0) continue
                val id = "pl_${pl.id}"
                if (usedIds.add(id)) {
                    items += QuickPickItem(
                        id = id,
                        kind = QuickPickKind.PLAYLIST,
                        title = pl.name,
                        artworkUri = null,
                        trackCount = pl.trackCount,
                        playlistId = pl.id
                    )
                }
            }
        }
        if (items.size < 6) {
            for (mix in mixes.drop(1)) {
                if (items.size >= 6) break
                if (mix.tracks.isEmpty()) continue
                if (usedIds.add(mix.id)) {
                    items += QuickPickItem(
                        id = mix.id,
                        kind = QuickPickKind.DAILY_MIX,
                        title = mix.id,
                        artworkUri = mix.tracks.firstOrNull { !it.artworkUri.isNullOrBlank() }?.artworkUri,
                        trackCount = mix.tracks.size,
                        tracks = mix.tracks
                    )
                }
            }
        }

        items.take(6)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun playQuickPick(item: QuickPickItem) {
        if (item.tracks.isNotEmpty()) {
            playerController.play(item.tracks, 0)
            return
        }
        val playlistId = item.playlistId ?: return
        viewModelScope.launch {
            val tracks = playlistRepository.getPlaylistTracks(playlistId).first()
            if (tracks.isNotEmpty()) {
                playerController.play(tracks, 0)
            }
        }
    }

    /** Plays the current "right now" queue exactly as the card shows it. */
    fun playRightNow() {
        val queue = rightNow.value?.tracks.orEmpty()
        if (queue.isNotEmpty()) playerController.play(queue, 0)
    }

    fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        playerController.play(tracks, startIndex)
    }

    fun shuffleAllTracks() {
        val tracks = allTracks.value
        if (tracks.isEmpty()) return
        playerController.setShuffleEnabled(true)
        playerController.play(tracks, tracks.indices.random())
    }

    /** Starts a varied queue that fits the listener's available time. */
    fun playListeningSession(minutes: Int, onEmpty: () -> Unit = {}) {
        if (minutes !in setOf(15, 30, 60)) return
        val tracks = allTracks.value
        val preferredIds = dailyMixes.value.firstOrNull()?.tracks?.map { it.id }?.toSet().orEmpty()
        val session = ListeningSessionPlanner.buildSession(
            tracks = tracks,
            targetDurationMs = minutes * 60_000L,
            preferredTrackIds = preferredIds,
            seed = System.currentTimeMillis()
        )
        if (session.isEmpty()) {
            onEmpty()
        } else {
            // A timed session should behave like a finite listening window, not loop forever.
            playerController.setShuffleEnabled(false)
            playerController.setRepeatMode(androidx.media3.common.Player.REPEAT_MODE_OFF)
            playerController.play(session, 0)
        }
    }

    fun playNext(track: Track) {
        playerController.playNext(track)
    }

    fun addToQueue(track: Track) {
        playerController.addToQueue(track)
    }

    fun addTrackToPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch {
            playlistRepository.addTrackToPlaylist(playlistId, trackId)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name)
        }
    }

    fun updateTrackMetadata(
        id: String,
        title: String,
        artist: String,
        album: String,
        genre: String,
        onDone: (Track?) -> Unit = {}
    ) {
        viewModelScope.launch {
            val updated = try {
                trackRepository.updateTrackMetadata(id, title, artist, album, genre)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (updated != null) playerController.updateTrackInPlayback(updated)
            onDone(updated)
        }
    }

    fun deleteTrack(track: Track, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val deleted = try {
                trackRepository.deleteTrack(track)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            onDone(deleted)
        }
    }

    fun playMoodPlaylist(mood: TrackCuration.Mood, onEmpty: () -> Unit = {}) {
        viewModelScope.launch {
            // A tap should create one playlist from the current library snapshot. Keeping a
            // collect alive here can unexpectedly restart playback after every library update.
            val tracks = allTracks.value.ifEmpty { trackRepository.getAllTracks().first() }
            val profile = tasteProfile.value
            val moodTracks = withContext(Dispatchers.Default) {
                TrackCuration.generateMoodPlaylist(mood, tracks, profile = profile)
            }
            if (moodTracks.isNotEmpty()) {
                playerController.play(moodTracks, 0)
            } else {
                onEmpty()
            }
        }
    }

    fun hideTrack(track: Track) {
        viewModelScope.launch {
            if (playerController.currentTrack.value?.id == track.id) {
                playerController.next()
            }
            trackRepository.hideTrack(track.id)
        }
    }
}

enum class QuickPickKind { FAVORITES, PLAYLIST, DAILY_MIX, ARTIST }

data class QuickPickItem(
    val id: String,
    val kind: QuickPickKind,
    val title: String,
    val artworkUri: String?,
    val trackCount: Int,
    val playlistId: Long? = null,
    val tracks: List<Track> = emptyList()
)
