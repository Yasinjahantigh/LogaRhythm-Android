package com.yasinjahantigh.logarhythm.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import com.yasinjahantigh.logarhythm.MainActivity
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.domain.model.DownloadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadNotifications @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun foregroundInfo(
        downloadId: String,
        title: String,
        progress: Int,
        etaSeconds: Long,
        status: DownloadStatus
    ): ForegroundInfo {
        val notification = progressNotification(downloadId, title, progress, etaSeconds, status)
        val id = notificationId(downloadId)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(id, notification)
        }
    }

    fun notifyFinished(downloadId: String, title: String, success: Boolean, message: String?) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_placeholder)
            .setContentTitle(
                context.getString(
                    if (success) R.string.download_notification_done
                    else R.string.download_notification_failed,
                    title
                )
            )
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
        if (!message.isNullOrBlank()) builder.setContentText(message)
        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId(downloadId), builder.build())
        }
    }

    private fun progressNotification(
        downloadId: String,
        title: String,
        progress: Int,
        etaSeconds: Long,
        status: DownloadStatus
    ): Notification {
        val indeterminate = status == DownloadStatus.QUEUED ||
            status == DownloadStatus.SAVING
        val clamped = progress.coerceIn(0, 100)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_placeholder)
            .setContentTitle(context.getString(R.string.download_notification_title, title))
            .setContentText(statusText(status, clamped, etaSeconds))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openAppIntent())
            .setProgress(100, clamped, indeterminate)
            .addAction(
                R.drawable.ic_music_placeholder,
                context.getString(R.string.download_cancel),
                cancelIntent(downloadId)
            )
        return builder.build()
    }

    private fun statusText(status: DownloadStatus, progress: Int, etaSeconds: Long): String =
        when (status) {
            DownloadStatus.QUEUED -> context.getString(R.string.download_preparing)
            DownloadStatus.DOWNLOADING -> context.getString(R.string.download_status_downloading, progress)
            DownloadStatus.SAVING -> context.getString(R.string.download_status_saving)
            DownloadStatus.SAVED -> context.getString(R.string.download_status_saved)
            DownloadStatus.FAILED -> context.getString(R.string.download_status_failed)
            DownloadStatus.CANCELLED -> context.getString(R.string.download_status_cancelled)
        }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun cancelIntent(downloadId: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        downloadId.hashCode(),
        Intent(context, DownloadActionReceiver::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_ID, downloadId)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun notificationId(downloadId: String): Int = downloadId.hashCode() and 0x7fffffff

    companion object {
        const val CHANNEL_ID = "downloads"
        const val ACTION_CANCEL = "com.yasinjahantigh.logarhythm.action.CANCEL_DOWNLOAD"
        const val EXTRA_ID = "download_id"

        fun createChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.download_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.download_notification_channel_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }
}
