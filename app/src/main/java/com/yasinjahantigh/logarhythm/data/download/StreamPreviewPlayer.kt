package com.yasinjahantigh.logarhythm.data.download

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.yasinjahantigh.logarhythm.data.download.musicman.MusicManClient
import com.yasinjahantigh.logarhythm.domain.model.DownloadResultKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import com.yasinjahantigh.logarhythm.playback.PlayerController
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class StreamPreviewState(
    val trackKey: String? = null,
    val isLoading: Boolean = false,
    val isPlaying: Boolean = false
)

/**
 * Lightweight preview player for search results before downloading.
 * Pauses main playback automatically so audio never overlaps.
 */
@Singleton
class StreamPreviewPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicManClient: MusicManClient,
    private val playerController: PlayerController,
    baseClient: OkHttpClient
) {
    private val httpClient = baseClient.newBuilder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var player: ExoPlayer? = null
    private var loadJob: Job? = null

    private val _state = MutableStateFlow(StreamPreviewState())
    val state: StateFlow<StreamPreviewState> = _state.asStateFlow()

    fun keyFor(result: DownloadSearchResult): String =
        "${result.kind.name}_${result.source.raw}_${result.id}"

    fun toggle(result: DownloadSearchResult) {
        if (result.kind != DownloadResultKind.TRACK) return
        val key = keyFor(result)
        val current = _state.value
        if (current.trackKey == key && (current.isLoading || current.isPlaying)) {
            stop()
            return
        }

        loadJob?.cancel()
        _state.value = StreamPreviewState(trackKey = key, isLoading = true, isPlaying = false)

        if (playerController.isPlaying.value) {
            playerController.pause()
        }

        loadJob = scope.launch {
            try {
                val streamUrl = resolvePreviewUrl(result)
                if (streamUrl.isNullOrBlank()) {
                    _state.value = StreamPreviewState()
                    return@launch
                }
                withContext(Dispatchers.Main) {
                    if (_state.value.trackKey != key) return@withContext
                    startExoPlayer(key, streamUrl)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (_state.value.trackKey == key) {
                    _state.value = StreamPreviewState()
                }
            }
        }
    }

    fun stop() {
        loadJob?.cancel()
        loadJob = null
        _state.value = StreamPreviewState()
        mainHandler.post {
            player?.apply {
                stop()
                clearMediaItems()
            }
        }
    }

    private suspend fun resolvePreviewUrl(result: DownloadSearchResult): String? {
        when (result.source) {
            DownloadSource.MUSICMAN -> {
                val audio = runCatching {
                    musicManClient.resolveAudioCandidates(result.id).firstOrNull()
                }.getOrNull()
                if (audio != null) return musicManClient.playableUrl(audio)
            }
            DownloadSource.DEEZER -> {
                fetchDeezerPreview(result.id)?.let { return it }
            }
            else -> Unit
        }
        return fetchItunesPreview(result.title, result.artist)
    }

    private fun fetchDeezerPreview(trackId: String): String? {
        val req = Request.Builder()
            .url("https://api.deezer.com/track/${trackId.trim()}")
            .header("Accept", "application/json")
            .get()
            .build()
        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                JSONObject(resp.body?.string().orEmpty())
                    .optString("preview")
                    .takeIf { it.startsWith("http") }
            }
        }.getOrNull()
    }

    private fun fetchItunesPreview(title: String, artist: String): String? {
        val q = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ").trim()
        if (q.isBlank()) return null
        val encoded = URLEncoder.encode(q, "UTF-8")
        val req = Request.Builder()
            .url("https://itunes.apple.com/search?term=$encoded&entity=song&limit=3")
            .header("Accept", "application/json")
            .get()
            .build()
        return runCatching {
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("results") ?: return@use null
                (0 until arr.length()).firstNotNullOfOrNull { idx ->
                    arr.optJSONObject(idx)?.optString("previewUrl")?.takeIf { it.startsWith("http") }
                }
            }
        }.getOrNull()
    }

    private fun startExoPlayer(key: String, url: String) {
        val exo = player ?: ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .build()
            .also { instance ->
                instance.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        val activeKey = _state.value.trackKey ?: return
                        if (playbackState == Player.STATE_READY) {
                            _state.value = StreamPreviewState(
                                trackKey = activeKey,
                                isLoading = false,
                                isPlaying = instance.isPlaying
                            )
                        } else if (playbackState == Player.STATE_ENDED) {
                            _state.value = StreamPreviewState()
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        val activeKey = _state.value.trackKey ?: return
                        if (!_state.value.isLoading || isPlaying) {
                            _state.value = StreamPreviewState(
                                trackKey = activeKey,
                                isLoading = false,
                                isPlaying = isPlaying
                            )
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        _state.value = StreamPreviewState()
                    }
                })
                player = instance
            }

        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        exo.play()
        _state.value = StreamPreviewState(trackKey = key, isLoading = false, isPlaying = true)
    }
}
