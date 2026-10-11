package com.example.musicplayer.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.core.library.LibrarySync
import com.example.musicplayer.domain.ai.MusicAiEngine
import com.example.musicplayer.domain.discovery.Discovery
import com.example.musicplayer.domain.mix.DailyMixGenerator
import com.example.musicplayer.domain.model.DailyMix
import com.example.musicplayer.domain.model.Playlist
import com.example.musicplayer.domain.model.Track
import com.example.musicplayer.domain.repository.PlaylistRepository
import com.example.musicplayer.domain.repository.StatsRepository
import com.example.musicplayer.domain.repository.TrackRepository
import com.example.musicplayer.domain.session.ListeningSessionPlanner
import com.example.musicplayer.domain.taste.TasteProfile
import com.example.musicplayer.playback.PlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
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
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    statsRepository: StatsRepository,
    private val trackRepository: TrackRepository,
    private val playlistRepository: PlaylistRepository,
    private val playerController: PlayerController,
    private val librarySync: LibrarySync
) : ViewModel() {

    /** Index the device's music now (after permission is granted or on a manual rescan). */
    fun rescanLibrary() = librarySync.requestRefresh()


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

    private val favoritesTrackIds = playlistRepository.getPlaylistTracks(1L)
        .map { tracks -> tracks.map { it.id }.toSet() }

    val dailyMixes: StateFlow<List<DailyMix>> = combine(
        allTracks,
        statsRepository.getAllPlayEvents(),
        favoritesTrackIds
    ) { tracks, events, favIds ->
        dailyMixGenerator.generateMixes(
            allTracks = tracks,
            playEvents = events,
            favoriteTrackIds = favIds
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val allPlayEvents = statsRepository.getAllPlayEvents()

    /** What the listener's own history says about their taste; empty until there is history. */
    private val tasteProfile: StateFlow<TasteProfile> = combine(allPlayEvents, favoritesTrackIds) { events, favIds ->
        TasteProfile.build(events, favIds)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TasteProfile.EMPTY
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

    fun playAiMoodPlaylist(mood: MusicAiEngine.AiMood, onEmpty: () -> Unit = {}) {
        viewModelScope.launch {
            // A tap should create one playlist from the current library snapshot. Keeping a
            // collect alive here can unexpectedly restart playback after every library update.
            val tracks = trackRepository.getAllTracks().first()
            val moodTracks = MusicAiEngine.generateMoodPlaylist(mood, tracks)
            if (moodTracks.isNotEmpty()) {
                playerController.play(moodTracks, 0)
            } else {
                onEmpty()
            }
        }
    }
}
