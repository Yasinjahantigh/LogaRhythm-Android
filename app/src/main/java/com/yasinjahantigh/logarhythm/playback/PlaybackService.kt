package com.yasinjahantigh.logarhythm.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.MainActivity
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import com.yasinjahantigh.logarhythm.data.room.MusicDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground MediaSessionService running ExoPlayer.
 * Manages audio focus, audio becoming noisy events, system notifications,
 * equalizer audio session attachment, and background sleep timer.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.yasinjahantigh.logarhythm.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.yasinjahantigh.logarhythm.action.NEXT"
        const val ACTION_PREVIOUS = "com.yasinjahantigh.logarhythm.action.PREVIOUS"
        private const val ACTION_TOGGLE_SHUFFLE = "com.yasinjahantigh.logarhythm.action.TOGGLE_SHUFFLE"
        private const val ACTION_CYCLE_REPEAT = "com.yasinjahantigh.logarhythm.action.CYCLE_REPEAT"
    }

    @Inject
    lateinit var playerController: PlayerController

    @Inject
    lateinit var equalizerManager: EqualizerManager

    @Inject
    lateinit var sleepTimerManager: SleepTimerManager

    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var musicDao: MusicDao

    private var mediaSession: MediaSession? = null
    private var enhancer: PlaybackEnhancer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val sessionCallback = object : MediaSession.Callback {
        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY))
                .add(SessionCommand(ACTION_CYCLE_REPEAT, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        @OptIn(UnstableApi::class)
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_TOGGLE_SHUFFLE -> playerController.toggleShuffle()
                ACTION_CYCLE_REPEAT -> playerController.cycleRepeat()
                else -> return Futures.immediateFuture(
                    SessionResult(SessionError.ERROR_NOT_SUPPORTED)
                )
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            updateNotificationButtons()
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // Local files never stall on a network, so a short buffer is plenty and keeps RAM low.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(10_000, 20_000, 1_000, 2_000)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setLoadControl(loadControl)
            // Keeps the CPU awake for local playback with the screen off (no stutter on idle devices).
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        enhancer = PlaybackEnhancer(this, player, preferencesManager, musicDao, sleepTimerManager)
            .also { it.start() }

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

        val bitmapLoader = AudioBitmapLoader(this)

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .setBitmapLoader(bitmapLoader)
            .setCallback(sessionCallback)
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .build()
                .apply {
                    setSmallIcon(R.drawable.ic_music_placeholder)
                }
        )

        updateNotificationButtons()
        scope.launch {
            combine(playerController.shuffle, playerController.repeatMode) { _, _ -> }
                .collect { updateNotificationButtons() }
        }
    }

    @OptIn(UnstableApi::class)
    private fun updateNotificationButtons() {
        val session = mediaSession ?: return
        val shuffle = playerController.shuffle.value
        val repeatMode = playerController.repeatMode.value
        session.setMediaButtonPreferences(
            listOf(
                CommandButton.Builder(
                    if (shuffle) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF
                )
                    .setSessionCommand(SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY))
                    .setDisplayName(getString(R.string.cd_shuffle))
                    .build(),
                CommandButton.Builder(
                    when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
                        Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
                        else -> CommandButton.ICON_REPEAT_OFF
                    }
                )
                    .setSessionCommand(SessionCommand(ACTION_CYCLE_REPEAT, Bundle.EMPTY))
                    .setDisplayName(
                        getString(
                            if (repeatMode == Player.REPEAT_MODE_ONE) R.string.cd_repeat_one
                            else R.string.cd_repeat
                        )
                    )
                    .build()
            )
        )
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
        scope.cancel()
        enhancer?.release()
        enhancer = null
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
