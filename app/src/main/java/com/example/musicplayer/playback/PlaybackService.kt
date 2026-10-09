package com.example.musicplayer.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.musicplayer.MainActivity
import com.example.musicplayer.widget.MusicWidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground MediaSessionService running ExoPlayer.
 * Manages audio focus, audio becoming noisy events, system notifications,
 * equalizer audio session attachment, background sleep timer, and widget sync.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.example.musicplayer.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.example.musicplayer.action.NEXT"
        const val ACTION_PREVIOUS = "com.example.musicplayer.action.PREVIOUS"
    }

    @Inject
    lateinit var equalizerManager: EqualizerManager

    @Inject
    lateinit var sleepTimerManager: SleepTimerManager

    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        // Sync player events to Glance Home Screen Widget
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val item = player.currentMediaItem
                MusicWidgetUpdater.update(
                    this@PlaybackService,
                    item?.mediaMetadata?.title?.toString(),
                    item?.mediaMetadata?.artist?.toString(),
                    item?.mediaMetadata?.artworkUri?.toString(),
                    isPlaying
                )
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                MusicWidgetUpdater.update(
                    this@PlaybackService,
                    mediaItem?.mediaMetadata?.title?.toString(),
                    mediaItem?.mediaMetadata?.artist?.toString(),
                    mediaItem?.mediaMetadata?.artworkUri?.toString(),
                    player.isPlaying
                )
            }
        })

        // Attach Sleep Timer to player
        sleepTimerManager.attachPlayer(player)

        // Attach Equalizer to player audio session and observe changes
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
                equalizerManager.attachAudioSession(audioSessionId)
            }
        })
        val initialSessionId = player.audioSessionId
        if (initialSessionId != C.AUDIO_SESSION_ID_UNSET && initialSessionId != 0) {
            equalizerManager.attachAudioSession(initialSessionId)
        }

        val sessionActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = mediaSession?.player
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                if (player != null) {
                    if (player.isPlaying) player.pause() else player.play()
                }
            }
            ACTION_NEXT -> {
                player?.seekToNextMediaItem()
            }
            ACTION_PREVIOUS -> {
                if (player != null) {
                    if (player.currentPosition > 3000L) {
                        player.seekTo(0L)
                    } else if (player.hasPreviousMediaItem()) {
                        player.seekToPreviousMediaItem()
                    } else {
                        player.seekTo(0L)
                    }
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        sleepTimerManager.detachPlayer()
        equalizerManager.release()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
