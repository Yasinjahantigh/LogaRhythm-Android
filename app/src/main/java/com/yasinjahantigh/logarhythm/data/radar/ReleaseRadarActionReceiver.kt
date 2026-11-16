package com.yasinjahantigh.logarhythm.data.radar

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yasinjahantigh.logarhythm.data.room.ArtistReleaseEntity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ReleaseRadarActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: ArtistFollowRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReleaseRadarNotifications.ACTION_DOWNLOAD_RELEASE) return
        val releaseId = intent.getStringExtra(ReleaseRadarNotifications.EXTRA_RELEASE_ID) ?: return
        val title = intent.getStringExtra(ReleaseRadarNotifications.EXTRA_TITLE) ?: return
        val artist = intent.getStringExtra(ReleaseRadarNotifications.EXTRA_ARTIST) ?: return
        val album = intent.getStringExtra(ReleaseRadarNotifications.EXTRA_ALBUM).orEmpty()
        val artwork = intent.getStringExtra(ReleaseRadarNotifications.EXTRA_ARTWORK)
        val source = intent.getStringExtra(ReleaseRadarNotifications.EXTRA_SOURCE).orEmpty()
        val sourceUrl = intent.getStringExtra(ReleaseRadarNotifications.EXTRA_SOURCE_URL).orEmpty()
        val durationMs = intent.getLongExtra(ReleaseRadarNotifications.EXTRA_DURATION_MS, 0L)

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.downloadRelease(
                    ArtistReleaseEntity(
                        releaseId = releaseId,
                        artistKey = ReleaseRadarService.normalizeArtistKey(artist),
                        artistName = artist,
                        title = title,
                        album = album,
                        artworkUrl = artwork,
                        source = source,
                        sourceUrl = sourceUrl,
                        durationMs = durationMs,
                        discoveredAt = System.currentTimeMillis(),
                        isSeen = true
                    )
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
