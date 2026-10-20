package com.yasinjahantigh.logarhythm.playback

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.container.MdtaMetadataEntry
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.data.room.MusicDao
import com.yasinjahantigh.logarhythm.data.room.ResumePositionEntity
import com.yasinjahantigh.logarhythm.domain.playback.ReplayGain
import com.yasinjahantigh.logarhythm.domain.playback.ReplayGainMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Playback add-ons around the single ExoPlayer: fades between songs, ReplayGain (attenuation on
 * `player.volume`, boost through `LoudnessEnhancer`), skip silence, sleep-timer fade-out, resume
 * positions and headset recovery. All work happens on the main thread.
 */
@OptIn(UnstableApi::class)
class PlaybackEnhancer(
    context: Context,
    private val player: ExoPlayer,
    private val preferences: PreferencesManager,
    private val dao: MusicDao,
    private val sleepTimer: SleepTimerManager
) {
    private companion object {
        const val LONG_AUDIO_MS = 20 * 60_000L
        const val SAVE_INTERVAL_MS = 10_000L
        const val SLEEP_FADE_MS = 15_000L
        const val HEADSET_WINDOW_MS = 10 * 60_000L
        // AudioDeviceInfo.TYPE_BLE_HEADSET is API 31; compare by value so older SDKs compile.
        const val TYPE_BLE_HEADSET = 26
        val HEADSET_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            TYPE_BLE_HEADSET
        )
        val GAIN_KEYS = listOf(
            "replaygain_track_gain", "replaygain_album_gain", "r128_track_gain", "r128_album_gain"
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var fadeMs = 0L
    private var replayMode = ReplayGainMode.OFF
    private var preampDb = 0f
    private var resumeOnHeadset = true

    private var trackGainDb: Float? = null
    private var albumGainDb: Float? = null
    private var replayVolume = 1f
    private var pendingBoostMb = 0
    private var lastVolume = -1f
    private var lastItemId: String? = null
    private var lastSavedAt = 0L
    private var pausedByNoisyAt = 0L

    // DSP boost stage for quiet tracks (player.volume can only attenuate). Bound to the current
    // audio session; recreated when the session changes, released with the player.
    private var loudness: LoudnessEnhancer? = null
    private var loudnessSessionId = 0

    private val tick = object : Runnable {
        override fun run() {
            applyVolume()
            maybeSavePosition(force = false)
            if (player.isPlaying) handler.postDelayed(this, nextDelayMs())
        }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                // The previous long file played to its end: forget where it was.
                lastItemId?.let { id -> scope.launch(Dispatchers.IO) { dao.clearResumePosition(id) } }
            }
            lastItemId = mediaItem?.mediaId
            trackGainDb = null
            albumGainDb = null
            replayVolume = 1f
            pendingBoostMb = 0
            applyBoost()
            lastSavedAt = 0L
            applyVolume()
            if (mediaItem != null && reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                restorePosition(mediaItem.mediaId)
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            readReplayGain(tracks)
            applyVolume()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            handler.removeCallbacks(tick)
            if (isPlaying) {
                handler.post(tick)
            } else {
                maybeSavePosition(force = true)
                applyVolume()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            pausedByNoisyAt = if (!playWhenReady &&
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
            ) SystemClock.elapsedRealtime() else 0L
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) = applyVolume()
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            if (!resumeOnHeadset || pausedByNoisyAt == 0L) return
            if (SystemClock.elapsedRealtime() - pausedByNoisyAt > HEADSET_WINDOW_MS) {
                pausedByNoisyAt = 0L
                return
            }
            if (addedDevices.any { it.isSink && it.type in HEADSET_TYPES }) {
                pausedByNoisyAt = 0L
                player.play()
            }
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
            bindLoudness(audioSessionId)
        }
    }

    fun start() {
        player.addListener(listener)
        player.addAnalyticsListener(analyticsListener)
        bindLoudness(player.audioSessionId)
        audioManager.registerAudioDeviceCallback(deviceCallback, handler)
        scope.launch { preferences.fadeBetweenSongsSec.collect { fadeMs = it * 1000L; applyVolume() } }
        scope.launch { preferences.skipSilence.collect { player.skipSilenceEnabled = it } }
        scope.launch {
            preferences.replayGainMode.collect {
                replayMode = ReplayGainMode.from(it)
                recomputeReplayVolume()
                applyVolume()
            }
        }
        scope.launch {
            preferences.replayGainPreampDb.collect {
                preampDb = it
                recomputeReplayVolume()
                applyVolume()
            }
        }
        scope.launch { preferences.resumeOnHeadset.collect { resumeOnHeadset = it } }
        lastItemId = player.currentMediaItem?.mediaId
    }

    fun release() {
        maybeSavePosition(force = true)
        handler.removeCallbacksAndMessages(null)
        player.removeListener(listener)
        runCatching { player.removeAnalyticsListener(analyticsListener) }
        runCatching { loudness?.release() }
        loudness = null
        loudnessSessionId = 0
        runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
        scope.cancel()
    }

    // ---- volume: ReplayGain x fade x sleep-timer fade ------------------------------------------

    private fun nextDelayMs(): Long {
        val duration = player.duration
        if (fadeMs <= 0L || duration == C.TIME_UNSET) {
            return if (sleepTimer.remainingMs.value?.let { it < SLEEP_FADE_MS * 2 } == true) 100L else 1_000L
        }
        val speed = player.playbackParameters.speed.coerceAtLeast(0.25f)
        val untilFade = ((duration - player.currentPosition - fadeMs) / speed).toLong()
        return untilFade.coerceIn(80L, 1_000L)
    }

    private fun applyVolume() {
        var factor = 1f
        val duration = player.duration
        val position = player.currentPosition
        if (fadeMs > 0L) {
            if (position in 0 until fadeMs) factor = minOf(factor, position / fadeMs.toFloat())
            if (duration != C.TIME_UNSET && duration > 0L) {
                val speed = player.playbackParameters.speed.coerceAtLeast(0.25f)
                val remaining = ((duration - position) / speed).toLong()
                if (remaining in 0 until fadeMs) factor = minOf(factor, remaining / fadeMs.toFloat())
            }
        }
        sleepTimer.remainingMs.value?.let { left ->
            if (left < SLEEP_FADE_MS) factor = minOf(factor, left.coerceAtLeast(0L) / SLEEP_FADE_MS.toFloat())
        }
        val perceptual = factor.coerceIn(0f, 1f).let { it * it }
        val target = (replayVolume * perceptual).coerceIn(0f, 1f)
        if (kotlin.math.abs(target - lastVolume) > 0.004f) {
            lastVolume = target
            player.volume = target
        }
    }

    private fun recomputeReplayVolume() {
        val (volume, boostMb) = ReplayGain.splitForOutput(
            ReplayGain.pick(replayMode, trackGainDb, albumGainDb), preampDb
        )
        replayVolume = volume
        pendingBoostMb = boostMb
        applyBoost()
    }

    /** (Re)binds the DSP boost to the player's audio session. Silent no-op where unsupported. */
    private fun bindLoudness(audioSessionId: Int) {
        if (audioSessionId == 0 || audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        if (loudness != null && loudnessSessionId == audioSessionId) return
        runCatching { loudness?.release() }
        loudness = null
        loudnessSessionId = audioSessionId
        loudness = runCatching {
            LoudnessEnhancer(audioSessionId).apply { enabled = true }
        }.getOrElse {
            Log.w("PlaybackEnhancer", "LoudnessEnhancer not supported: ${it.message}")
            null
        }
        applyBoost()
    }

    private fun applyBoost() {
        val enhancer = loudness ?: return
        runCatching { enhancer.setTargetGain(pendingBoostMb) }
    }

    /**
     * ReplayGain lives in ID3 TXXX frames, Vorbis comments and MP4 freeform atoms, which ExoPlayer
     * surfaces as stream metadata. Typed key/value fields are read first; unknown entry types fall
     * back to a `toString()` scan. Also understands R128_*_GAIN (1/256 dB units).
     */
    private fun readReplayGain(tracks: Tracks) {
        var track: Float? = null
        var album: Float? = null
        for (group in tracks.groups) {
            for (i in 0 until group.length) {
                val metadata = group.getTrackFormat(i).metadata ?: continue
                for (e in 0 until metadata.length()) {
                    val keyed = keyValueOf(metadata.get(e)) ?: continue
                    val (key, value) = keyed
                    when (key) {
                        "replaygain_track_gain" -> track = track ?: ReplayGain.parseDb(value)
                        "replaygain_album_gain" -> album = album ?: ReplayGain.parseDb(value)
                        "r128_track_gain" -> track = track ?: ReplayGain.parseR128(value)
                        "r128_album_gain" -> album = album ?: ReplayGain.parseR128(value)
                    }
                }
            }
        }
        trackGainDb = track
        albumGainDb = album
        recomputeReplayVolume()
    }

    private fun keyValueOf(entry: Metadata.Entry): Pair<String, String>? = try {
        val (rawKey, rawValue) = when (entry) {
            is TextInformationFrame -> (entry.description?.ifEmpty { entry.id } ?: entry.id) to (entry.value ?: "")
            is VorbisComment -> entry.key to entry.value
            is MdtaMetadataEntry -> entry.key to String(entry.value, Charsets.UTF_8)
            else -> {
                val text = entry.toString()
                val lower = text.lowercase()
                val key = GAIN_KEYS.firstOrNull { lower.contains(it) } ?: return null
                key to text.substring(lower.indexOf(key) + key.length)
            }
        }
        if (rawKey.isBlank()) null else rawKey.lowercase().trim() to rawValue
    } catch (_: Exception) {
        null
    }

    // ---- long-audio position memory -------------------------------------------------------------

    private fun restorePosition(mediaId: String) {
        scope.launch {
            val saved = withContext(Dispatchers.IO) { dao.getResumePosition(mediaId) } ?: return@launch
            val item = player.currentMediaItem ?: return@launch
            if (item.mediaId != mediaId || player.currentPosition > 2_000L) return@launch
            val duration = player.duration
            val limit = if (duration == C.TIME_UNSET) Long.MAX_VALUE else duration - 15_000L
            if (saved.positionMs in 5_000L until limit) player.seekTo(saved.positionMs)
        }
    }

    private fun maybeSavePosition(force: Boolean) {
        val id = player.currentMediaItem?.mediaId ?: return
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration < LONG_AUDIO_MS) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastSavedAt < SAVE_INTERVAL_MS) return
        lastSavedAt = now
        val position = player.currentPosition
        scope.launch(Dispatchers.IO) {
            if (position < 5_000L || position > duration - 10_000L) {
                dao.clearResumePosition(id)
            } else {
                dao.upsertResumePosition(ResumePositionEntity(id, position, System.currentTimeMillis()))
            }
        }
    }
}
