package com.yasinjahantigh.logarhythm.feature.update

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.data.update.AppRelease
import com.yasinjahantigh.logarhythm.data.update.UpdateState
import com.yasinjahantigh.logarhythm.ui.components.formatCount

@Composable
fun UpdateDialogHost(
    state: UpdateState,
    installedVersion: String,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        UpdateState.Hidden, UpdateState.Checking -> Unit
        is UpdateState.Available -> AvailableDialog(state.release, installedVersion, onDownload, onDismiss)
        is UpdateState.Downloading -> DownloadingDialog(state, onDismiss)
        is UpdateState.Failed -> FailedDialog(onDownload, onDismiss)
    }
}

@Composable
private fun AvailableDialog(
    release: AppRelease,
    installedVersion: String,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val size = Formatter.formatShortFileSize(context, release.apkSizeBytes)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }
        },
        title = {
            Text(
                text = stringResource(R.string.update_available_title, release.versionName),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Version transition banner
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "v$installedVersion",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "v${release.versionName}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(
                            text = if (release.downloads > 0) {
                                stringResource(R.string.update_meta, size, formatCount(release.downloads.toInt()))
                            } else size,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Changelog / Release notes section
                val isPersian = LocalConfiguration.current.locales[0]?.language.equals("fa", ignoreCase = true)
                val cleanedNotes = formatReleaseNotes(release.notes, preferPersian = isPersian)
                if (cleanedNotes.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.update_whats_new),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Text(
                            text = cleanedNotes,
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 20.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDownload) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.update_download_install))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_later))
            }
        }
    )
}

private val HEADING_REGEX = Regex("""^#{1,3}\s+(.+)$""")

private fun classifySection(heading: String): String? {
    val normalized = heading.trim().lowercase()
    return when {
        "فارسی" in normalized || "تغییرات" in normalized || "persian" in normalized -> "fa"
        "what's new" in normalized || "whats new" in normalized || "english" in normalized ||
            "changelog" in normalized || "changes" in normalized -> "en"
        else -> null
    }
}

private fun isPromoOrMetaLine(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed == "---" ||
        trimmed.startsWith("**Full Changelog**") ||
        trimmed.startsWith("Full Changelog:") ||
        trimmed.startsWith("📥") ||
        trimmed.startsWith("☕") ||
        trimmed.startsWith("💬") ||
        trimmed.startsWith("📢") ||
        trimmed.startsWith("https://t.me/") ||
        trimmed.startsWith("https://daramet.com/") ||
        trimmed.startsWith("https://github.com/Yasinjahantigh/LogaRhythm-Android/releases")
}

/**
 * Formats GitHub release markdown text into clean bullet points for the in-app dialog.
 * When bilingual sections (e.g. `## What's New` and `## فارسی`) are present, picks the
 * section matching [preferPersian] while falling back gracefully for older single-language notes.
 */
internal fun formatReleaseNotes(raw: String, preferPersian: Boolean = false): String {
    val lines = raw.lines()
    val enLines = mutableListOf<String>()
    val faLines = mutableListOf<String>()
    val otherLines = mutableListOf<String>()
    var activeLang: String? = null

    for (rawLine in lines) {
        val trimmed = rawLine.trim()
        val headingMatch = HEADING_REGEX.matchEntire(trimmed)
        if (headingMatch != null) {
            activeLang = classifySection(headingMatch.groupValues[1])
            continue
        }
        when (activeLang) {
            "en" -> enLines += trimmed
            "fa" -> faLines += trimmed
            else -> otherLines += trimmed
        }
    }

    val chosen = when {
        enLines.isNotEmpty() && faLines.isNotEmpty() -> if (preferPersian) faLines else enLines
        faLines.isNotEmpty() -> faLines
        enLines.isNotEmpty() -> enLines
        else -> otherLines
    }

    return chosen
        .filter { it.isNotBlank() && !isPromoOrMetaLine(it) }
        .map { line ->
            when {
                line.startsWith("* ") || line.startsWith("- ") -> "•  " + line.substring(2).trim()
                line.startsWith("• ") || line.startsWith("•") -> "•  " + line.removePrefix("•").trim()
                else -> line
            }
        }
        .joinToString("\n")
}

@Composable
private fun DownloadingDialog(state: UpdateState.Downloading, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_downloading_title)) },
        text = {
            Column {
                if (state.fraction >= 0f) {
                    LinearProgressIndicator(
                        progress = { state.fraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.update_downloading_percent, (state.fraction * 100).toInt()))
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.update_downloading_wait))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.update_downloading_hint),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_hide))
            }
        }
    )
}

@Composable
private fun FailedDialog(onRetry: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_failed_title)) },
        text = { Text(stringResource(R.string.update_failed_body)) },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.update_retry))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_close))
            }
        }
    )
}
