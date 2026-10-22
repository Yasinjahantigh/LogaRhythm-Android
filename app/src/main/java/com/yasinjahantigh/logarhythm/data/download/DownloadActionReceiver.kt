package com.yasinjahantigh.logarhythm.data.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yasinjahantigh.logarhythm.domain.repository.DownloadRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Handles the "Cancel" action on a download notification. */
@AndroidEntryPoint
class DownloadActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: DownloadRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadNotifications.ACTION_CANCEL) return
        val downloadId = intent.getStringExtra(DownloadNotifications.EXTRA_ID) ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repository.cancel(downloadId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
