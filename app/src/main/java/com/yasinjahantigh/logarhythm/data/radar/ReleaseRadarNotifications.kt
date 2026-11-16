package com.yasinjahantigh.logarhythm.data.radar

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.yasinjahantigh.logarhythm.MainActivity
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.data.room.ArtistReleaseEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReleaseRadarNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    baseClient: OkHttpClient
) {

    private val client = baseClient.newBuilder()
        .callTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun notifyReleases(releases: List<ArtistReleaseEntity>) {
        if (releases.isEmpty() || !canPostNotifications()) return
        createChannel(context)

        if (releases.size > 3) {
            val artists = releases.map { it.artistName }.distinct().take(3).joinToString(", ")
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music_placeholder)
                .setContentTitle(context.getString(R.string.radar_notif_summary_title, releases.size))
                .setContentText(artists)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent())
            try {
                NotificationManagerCompat.from(context).notify(SUMMARY_ID, builder.build())
            } catch (_: SecurityException) {
            }
            return
        }

        for (release in releases) {
            val bitmap = fetchLargeIcon(release.artworkUrl)
            val downloadIntent = Intent(context, ReleaseRadarActionReceiver::class.java).apply {
                action = ACTION_DOWNLOAD_RELEASE
                putExtra(EXTRA_RELEASE_ID, release.releaseId)
                putExtra(EXTRA_TITLE, release.title)
                putExtra(EXTRA_ARTIST, release.artistName)
                putExtra(EXTRA_ALBUM, release.album)
                putExtra(EXTRA_ARTWORK, release.artworkUrl)
                putExtra(EXTRA_SOURCE, release.source)
                putExtra(EXTRA_SOURCE_URL, release.sourceUrl)
                putExtra(EXTRA_DURATION_MS, release.durationMs)
            }
            val pendingDownload = PendingIntent.getBroadcast(
                context,
                release.releaseId.hashCode(),
                downloadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music_placeholder)
                .setContentTitle(context.getString(R.string.radar_notif_title, release.artistName))
                .setContentText(release.title)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent())
                .addAction(
                    R.drawable.ic_music_placeholder,
                    context.getString(R.string.radar_notif_download),
                    pendingDownload
                )
            if (bitmap != null) {
                builder.setLargeIcon(bitmap)
            }

            try {
                NotificationManagerCompat.from(context).notify(notificationId(release.releaseId), builder.build())
            } catch (_: SecurityException) {
            }
        }
    }

    private suspend fun fetchLargeIcon(url: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isNullOrBlank() || !url.startsWith("http")) return@withContext null
        try {
            val req = Request.Builder().url(url).get().build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val bytes = resp.body?.bytes() ?: return@withContext null
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        SUMMARY_ID,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private fun notificationId(releaseId: String): Int =
        RELEASE_ID_BASE + (releaseId.hashCode() and 0x0fffffff)

    companion object {
        const val CHANNEL_ID = "release_radar"
        const val ACTION_DOWNLOAD_RELEASE = "com.yasinjahantigh.logarhythm.action.DOWNLOAD_RADAR_RELEASE"
        const val EXTRA_RELEASE_ID = "release_id"
        const val EXTRA_TITLE = "release_title"
        const val EXTRA_ARTIST = "release_artist"
        const val EXTRA_ALBUM = "release_album"
        const val EXTRA_ARTWORK = "release_artwork"
        const val EXTRA_SOURCE = "release_source"
        const val EXTRA_SOURCE_URL = "release_source_url"
        const val EXTRA_DURATION_MS = "release_duration_ms"
        private const val SUMMARY_ID = 8_500
        private const val RELEASE_ID_BASE = 8_600

        fun createChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.radar_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.radar_notification_channel_desc)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
