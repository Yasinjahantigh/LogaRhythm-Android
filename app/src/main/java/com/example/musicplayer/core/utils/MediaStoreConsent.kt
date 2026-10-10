package com.example.musicplayer.core.utils

import android.app.Activity
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.musicplayer.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Starts Android's native consent UI only after a user initiates a write/delete operation.
 * The caller remains responsible for verifying the actual file operation after approval.
 * When Android requires consent, failures to create or launch the system request fail closed.
 * Older API levels proceed directly and let the repository report denied access honestly.
 */
@Composable
fun rememberMediaStoreConsent(): MediaStoreConsentActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingWrite by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingWriteCancelled by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingDelete by remember { mutableStateOf<Track?>(null) }
    var pendingDeleteConfirmed by remember { mutableStateOf<((Track) -> Unit)?>(null) }
    var pendingDeleteCancelled by remember { mutableStateOf<((Track) -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (pendingWrite != null) {
            val action = pendingWrite
            val cancelled = pendingWriteCancelled
            pendingWrite = null
            pendingWriteCancelled = null
            if (result.resultCode == Activity.RESULT_OK) action?.invoke() else cancelled?.invoke()
        } else {
            val track = pendingDelete
            val confirmed = pendingDeleteConfirmed
            val cancelled = pendingDeleteCancelled
            pendingDelete = null
            pendingDeleteConfirmed = null
            pendingDeleteCancelled = null
            if (track != null) {
                if (result.resultCode == Activity.RESULT_OK) confirmed?.invoke(track)
                else cancelled?.invoke(track)
            }
        }
    }

    return MediaStoreConsentActions(
        requestWrite = { track, onApproved, onCancelled ->
            if (pendingWrite != null || pendingDelete != null) {
                onCancelled() // This launcher supports one outstanding platform dialog at a time.
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                scope.launch {
                    val eligibility = withContext(Dispatchers.IO) { isMediaStoreMp3(context, track) }
                    if (pendingWrite != null || pendingDelete != null) {
                        onCancelled()
                        return@launch
                    }
                    when (eligibility) {
                        null -> onCancelled() // Unknown provider/query state: fail closed.
                        false -> onApproved() // Known non-MP3 item: the embedded writer will refuse it.
                        true -> {
                            val uri = Uri.parse(track.uri)
                            val intent = runCatching {
                                MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
                            }.getOrNull()
                            if (intent == null) {
                                onCancelled()
                            } else {
                                pendingWrite = onApproved
                                pendingWriteCancelled = onCancelled
                                runCatching { launcher.launch(IntentSenderRequest.Builder(intent.intentSender).build()) }
                                    .onFailure {
                                        pendingWrite = null
                                        pendingWriteCancelled = null
                                        onCancelled() // Never bypass a failed system-consent launch.
                                    }
                            }
                        }
                    }
                }
            } else onApproved()
        },
        requestDelete = { track, onApproved, onCancelled ->
            if (pendingWrite != null || pendingDelete != null) {
                onCancelled(track)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                scope.launch {
                    val eligibility = withContext(Dispatchers.IO) { isMediaStoreItem(context, track) }
                    if (pendingWrite != null || pendingDelete != null) {
                        onCancelled(track)
                        return@launch
                    }
                    when (eligibility) {
                        null -> onCancelled(track) // Failed query is not proof that the item is absent.
                        false -> onApproved(track) // Known absent; repository verifies before DB cleanup.
                        true -> {
                            val uri = Uri.parse(track.uri)
                            val intent = runCatching {
                                MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
                            }.getOrNull()
                            if (intent == null) {
                                onCancelled(track)
                            } else {
                                pendingDelete = track
                                pendingDeleteConfirmed = onApproved
                                pendingDeleteCancelled = onCancelled
                                runCatching { launcher.launch(IntentSenderRequest.Builder(intent.intentSender).build()) }
                                    .onFailure {
                                        pendingDelete = null
                                        pendingDeleteConfirmed = null
                                        pendingDeleteCancelled = null
                                        onCancelled(track) // Never bypass a failed system-consent launch.
                                    }
                            }
                        }
                    }
                }
            } else onApproved(track)
        }
    )
}

private fun isMediaStoreItem(context: android.content.Context, track: Track): Boolean? {
    if (!track.id.startsWith("local:")) return false
    val uri = runCatching { Uri.parse(track.uri) }.getOrNull() ?: return false
    val expectedId = track.id.removePrefix("local:").toLongOrNull() ?: return false
    if (!uri.scheme.equals("content", ignoreCase = true) || !uri.authority.equals("media", ignoreCase = true) ||
        uri.lastPathSegment?.toLongOrNull() != expectedId) return false
    return try {
        context.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media._ID), null, null, null)
            ?.use { cursor -> cursor.moveToFirst() && cursor.getLong(0) == expectedId }
    } catch (_: Exception) {
        null
    }
}

private fun isMediaStoreMp3(context: android.content.Context, track: Track): Boolean? {
    if (!track.id.startsWith("local:")) return false
    val uri = runCatching { Uri.parse(track.uri) }.getOrNull() ?: return false
    val expectedId = track.id.removePrefix("local:").toLongOrNull() ?: return false
    if (!uri.scheme.equals("content", ignoreCase = true) || !uri.authority.equals("media", ignoreCase = true) ||
        uri.lastPathSegment?.toLongOrNull() != expectedId) return false
    return try {
        context.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media._ID, MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (!cursor.moveToFirst() || cursor.getLong(0) != expectedId) false
                else cursor.getString(1).orEmpty().substringAfterLast('.', "").equals("mp3", ignoreCase = true)
            }
    } catch (_: Exception) {
        null
    }
}

data class MediaStoreConsentActions internal constructor(
    private val requestWrite: (Track, () -> Unit, () -> Unit) -> Unit,
    private val requestDelete: (Track, (Track) -> Unit, (Track) -> Unit) -> Unit
) {
    fun write(track: Track, onApproved: () -> Unit, onCancelled: () -> Unit = {}) =
        requestWrite(track, onApproved, onCancelled)

    fun delete(track: Track, onApproved: (Track) -> Unit, onCancelled: (Track) -> Unit = {}) =
        requestDelete(track, onApproved, onCancelled)
}
