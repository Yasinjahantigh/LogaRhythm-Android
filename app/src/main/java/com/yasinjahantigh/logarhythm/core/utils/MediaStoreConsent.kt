package com.yasinjahantigh.logarhythm.core.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.yasinjahantigh.logarhythm.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Asks for permission before tag edits, lyrics embedding or file deletion: the MediaStore
 * consent sheet on Android 11+, and WRITE_EXTERNAL_STORAGE on API 26-29. Every failure path
 * reports cancellation, and callers still verify the file operation afterwards.
 */
@Composable
fun rememberMediaStoreConsent(): MediaStoreConsentActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val holder = remember { PendingHolder() }

    val senderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val approved = result.resultCode == Activity.RESULT_OK
        when (val pending = holder.take()) {
            is Pending.Write -> if (approved) pending.onApproved() else pending.onCancelled()
            is Pending.Delete -> if (approved) pending.onApproved(pending.track) else pending.onCancelled(pending.track)
            else -> Unit
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = holder.take() as? Pending.Permission ?: return@rememberLauncherForActivityResult
        if (granted) pending.onGranted() else pending.onDenied()
    }

    fun withLegacyAccess(granted: () -> Unit, denied: () -> Unit) {
        val has = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
        when {
            has -> granted()
            holder.busy -> denied()
            else -> {
                holder.pending = Pending.Permission(granted, denied)
                runCatching { permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
                    .onFailure { holder.take(); denied() }
            }
        }
    }

    return MediaStoreConsentActions(
        requestWrite = { track, onApproved, onCancelled ->
            when {
                holder.busy -> onCancelled() // One platform dialog at a time.
                Build.VERSION.SDK_INT < Build.VERSION_CODES.R -> withLegacyAccess(onApproved, onCancelled)
                else -> {
                    val uri = libraryUri(track)
                    if (uri == null) {
                        onApproved() // Not a library file; tag writers will reject it.
                    } else if (hasWriteGrant(context, uri)) {
                        onApproved()
                    } else {
                        val sender = runCatching {
                            MediaStore.createWriteRequest(context.contentResolver, listOf(uri)).intentSender
                        }.getOrNull()
                        if (sender == null) {
                            onCancelled()
                        } else {
                            holder.pending = Pending.Write(onApproved, onCancelled)
                            runCatching { senderLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
                                .onFailure { holder.take(); onCancelled() }
                        }
                    }
                }
            }
        },
        requestDelete = { track, onApproved, onCancelled ->
            when {
                holder.busy -> onCancelled(track)
                Build.VERSION.SDK_INT < Build.VERSION_CODES.R ->
                    withLegacyAccess({ onApproved(track) }, { onCancelled(track) })
                else -> scope.launch {
                    val exists = withContext(Dispatchers.IO) { libraryItemExists(context, track) }
                    when {
                        holder.busy -> onCancelled(track)
                        exists == null -> onCancelled(track) // A failed query is not proof the file is gone.
                        exists == false -> onApproved(track) // Already gone; the repository verifies and cleans up.
                        else -> {
                            val uri = libraryUri(track)
                            val sender = uri?.let {
                                runCatching {
                                    MediaStore.createDeleteRequest(context.contentResolver, listOf(it)).intentSender
                                }.getOrNull()
                            }
                            if (sender == null) {
                                onCancelled(track)
                            } else {
                                holder.pending = Pending.Delete(track, onApproved, onCancelled)
                                runCatching { senderLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
                                    .onFailure { holder.take(); onCancelled(track) }
                            }
                        }
                    }
                }
            }
        }
    )
}

private class PendingHolder {
    var pending: Pending? = null
    val busy: Boolean get() = pending != null
    fun take(): Pending? = pending.also { pending = null }
}

private sealed interface Pending {
    class Write(val onApproved: () -> Unit, val onCancelled: () -> Unit) : Pending
    class Delete(val track: Track, val onApproved: (Track) -> Unit, val onCancelled: (Track) -> Unit) : Pending
    class Permission(val onGranted: () -> Unit, val onDenied: () -> Unit) : Pending
}

/** The exact MediaStore URI of a local library track, or null when the identity does not line up. */
private fun libraryUri(track: Track): Uri? {
    if (!track.id.startsWith("local:")) return null
    val expectedId = track.id.removePrefix("local:").toLongOrNull() ?: return null
    val uri = runCatching { Uri.parse(track.uri) }.getOrNull() ?: return null
    val ok = uri.scheme.equals("content", ignoreCase = true) &&
        uri.authority.equals("media", ignoreCase = true) &&
        uri.lastPathSegment?.toLongOrNull() == expectedId
    return uri.takeIf { ok }
}

private fun hasWriteGrant(context: Context, uri: Uri): Boolean = try {
    context.checkUriPermission(uri, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION) ==
        PackageManager.PERMISSION_GRANTED
} catch (_: Exception) {
    false
}

/** true = exists, false = confirmed absent (or not a library item), null = could not tell. */
private fun libraryItemExists(context: Context, track: Track): Boolean? {
    val uri = libraryUri(track) ?: return false
    val expectedId = track.id.removePrefix("local:").toLong()
    return try {
        context.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media._ID), null, null, null)
            ?.use { it.moveToFirst() && it.getLong(0) == expectedId }
    } catch (_: Exception) {
        null
    }
}

class MediaStoreConsentActions internal constructor(
    private val requestWrite: (Track, () -> Unit, () -> Unit) -> Unit,
    private val requestDelete: (Track, (Track) -> Unit, (Track) -> Unit) -> Unit
) {
    fun write(track: Track, onApproved: () -> Unit, onCancelled: () -> Unit = {}) =
        requestWrite(track, onApproved, onCancelled)

    fun delete(track: Track, onApproved: (Track) -> Unit, onCancelled: (Track) -> Unit = {}) =
        requestDelete(track, onApproved, onCancelled)
}
