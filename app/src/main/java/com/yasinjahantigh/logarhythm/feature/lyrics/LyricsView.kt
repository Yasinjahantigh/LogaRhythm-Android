package com.yasinjahantigh.logarhythm.feature.lyrics

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ManageSearch
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FormatLineSpacing
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Translate
import java.util.Locale
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import com.yasinjahantigh.logarhythm.domain.model.LyricsCandidate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.domain.model.Lyrics
import com.yasinjahantigh.logarhythm.core.utils.rememberMediaStoreConsent
import kotlinx.coroutines.launch
import java.net.URLEncoder
import kotlin.math.roundToInt

@Composable
fun LyricsView(
    viewModel: LyricsViewModel,
    modifier: Modifier = Modifier,
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {}
) {
    val lyrics by viewModel.currentLyrics.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val activeIndex by viewModel.activeLineIndex.collectAsStateWithLifecycle()
    val offsetMs by viewModel.offsetMs.collectAsStateWithLifecycle()
    val isHidden by viewModel.isHidden.collectAsStateWithLifecycle()
    val isMismatch by viewModel.lyricsMismatch.collectAsStateWithLifecycle()
    var showOffsetDialog by remember { mutableStateOf(false) }
    var isTapToSyncMode by remember { mutableStateOf(false) }
    var showLyricCardDialog by remember { mutableStateOf(false) }
    var showLrcEditorSheet by remember { mutableStateOf(false) }

    if (showOffsetDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showOffsetDialog = false },
            title = { Text(stringResource(R.string.lyrics_offset_title)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = String.format(java.util.Locale.ROOT, "%+.2f s", offsetMs / 1000f),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.lyrics_offset_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Fine adjustment (+/- 0.1s)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.nudgeOffset(-100L) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.lyrics_offset_fine_back))
                        }
                        FilledTonalButton(
                            onClick = { viewModel.nudgeOffset(100L) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.lyrics_offset_fine))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Standard adjustment (+/- 0.5s)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.nudgeOffset(-500L) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.lyrics_offset_later))
                        }
                        FilledTonalButton(
                            onClick = { viewModel.nudgeOffset(500L) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.lyrics_offset_earlier))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Coarse adjustment (+/- 1.0s)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.nudgeOffset(-1000L) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.lyrics_offset_1s_back))
                        }
                        FilledTonalButton(
                            onClick = { viewModel.nudgeOffset(1000L) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.lyrics_offset_1s))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            showOffsetDialog = false
                            isTapToSyncMode = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.TouchApp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.lyrics_tap_to_sync))
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showOffsetDialog = false }) {
                    Text(stringResource(R.string.btn_close))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { viewModel.resetOffset() }) {
                    Text(stringResource(R.string.lyrics_offset_reset))
                }
            }
        )
    }
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isAnyOnlineEnabled by viewModel.isAnyOnlineEnabled.collectAsStateWithLifecycle()
    val lyricsLineSpacingDp by viewModel.lyricsLineSpacingDp.collectAsStateWithLifecycle()
    val isManualEditOpen by viewModel.isManualEditOpen.collectAsStateWithLifecycle()
    val showCandidatePicker by viewModel.showCandidatePicker.collectAsStateWithLifecycle()
    val lyricsCandidates by viewModel.lyricsCandidates.collectAsStateWithLifecycle()
    val isSearchingCandidates by viewModel.isSearchingCandidates.collectAsStateWithLifecycle()
    val saveMessage by viewModel.saveMessage.collectAsStateWithLifecycle()
    val translateLang by viewModel.translateLang.collectAsStateWithLifecycle()
    val translatedLines by viewModel.translatedLines.collectAsStateWithLifecycle()
    val isTranslating by viewModel.isTranslating.collectAsStateWithLifecycle()
    val translationFailed by viewModel.translationFailed.collectAsStateWithLifecycle()

    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showLineSpacingDialog by remember { mutableStateOf(false) }
    var trackForLyricsDeletion by remember { mutableStateOf<com.yasinjahantigh.logarhythm.domain.model.Track?>(null) }

    if (showLineSpacingDialog) {
        LyricsLineSpacingDialog(
            currentSpacingDp = lyricsLineSpacingDp,
            onSpacingChange = viewModel::setLyricsLineSpacing,
            onDismiss = { showLineSpacingDialog = false }
        )
    }

    val context = LocalContext.current
    LaunchedEffect(translationFailed) {
        if (translationFailed) {
            Toast.makeText(context, context.getString(R.string.lyrics_translate_failed), Toast.LENGTH_SHORT).show()
        }
    }
    val mediaStoreConsent = rememberMediaStoreConsent()
    val requestLyricsWrite: ((com.yasinjahantigh.logarhythm.domain.model.Track) -> Unit) -> Unit = { action ->
        val targetTrack = currentTrack
        if (targetTrack != null) {
            mediaStoreConsent.write(
                track = targetTrack,
                onApproved = { action(targetTrack) }, // Bind the consent to the exact requested track.
                onCancelled = { viewModel.reportSaveFailure() }
            )
        } else viewModel.reportSaveFailure()
    }
    LaunchedEffect(saveMessage) {
        val message = when (saveMessage) {
            "embedded" -> context.getString(R.string.lyrics_embedded_success)
            "cached_only" -> context.getString(R.string.lyrics_cached_only)
            "failed" -> context.getString(R.string.lyrics_embed_failed)
            "deleted" -> context.getString(R.string.lyrics_deleted)
            "hidden" -> context.getString(R.string.lyrics_hidden_in_app)
            "delete_failed" -> context.getString(R.string.lyrics_delete_failed)
            "reset_failed" -> context.getString(R.string.lyrics_reset_failed)
            else -> null
        }
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.consumeSaveMessage()
        }
    }
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val swipeOffsetX = remember { Animatable(0f) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            requestLyricsWrite { track -> viewModel.importLyricsFromUri(uri, track) }
        }
    }

    val exportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportLyricsToUri(uri)
            Toast.makeText(context, context.getString(R.string.lyrics_saved_to_file), Toast.LENGTH_SHORT).show()
        }
    }

    val onSearchGoogleLyrics: () -> Unit = {
        val title = currentTrack?.title.orEmpty().trim()
        val artist = currentTrack?.artist.orEmpty().trim()
        val query = "$title $artist lyrics"
        val encoded = URLEncoder.encode(query, "UTF-8")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded"))
        context.startActivity(intent)
    }

    val onPasteFromClipboard: () -> Unit = {
        val clipText = clipboardManager.getText()?.text
        if (!clipText.isNullOrBlank()) {
            requestLyricsWrite { track -> viewModel.pasteAndSaveLyrics(clipText, track) }
        } else {
            Toast.makeText(context, context.getString(R.string.clipboard_empty), Toast.LENGTH_SHORT).show()
        }
    }

    if (showDeleteConfirmDialog) {
        val doomed = trackForLyricsDeletion
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                trackForLyricsDeletion = null
            },
            title = { Text(text = stringResource(R.string.dialog_delete_lyrics_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.dialog_delete_lyrics_confirm, doomed?.title.orEmpty()),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    // Most common case: the lyrics are simply wrong. One tap, no system prompt.
                    Button(
                        onClick = {
                            showDeleteConfirmDialog = false
                            trackForLyricsDeletion = null
                            if (doomed != null) viewModel.deleteLyrics(doomed, removeFromFile = false)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().testTag("lyrics_delete_in_app_button")
                    ) {
                        Text(text = stringResource(R.string.lyrics_remove_in_app))
                    }
                    Text(
                        text = stringResource(R.string.lyrics_remove_in_app_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Also strips the copy stored inside the audio file; needs the system's write consent.
                    OutlinedButton(
                        onClick = {
                            showDeleteConfirmDialog = false
                            trackForLyricsDeletion = null
                            if (doomed != null) {
                                mediaStoreConsent.write(
                                    track = doomed,
                                    onApproved = { viewModel.deleteLyrics(doomed, removeFromFile = true) },
                                    onCancelled = { viewModel.deleteLyrics(doomed, removeFromFile = false) }
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("lyrics_delete_everywhere_button")
                    ) {
                        Text(text = stringResource(R.string.lyrics_remove_everywhere))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        trackForLyricsDeletion = null
                    }
                ) {
                    Text(text = stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (isManualEditOpen) {
        ManualLyricsDialog(
            initialContent = lyrics?.let {
                if (it.isSynced) {
                    it.lines.joinToString("\n") { line ->
                        val min = line.timestampMs / 60_000L
                        val sec = (line.timestampMs % 60_000L) / 1_000L
                        val frac = (line.timestampMs % 1_000L) / 10L
                        String.format(Locale.US, "[%02d:%02d.%02d]%s", min, sec, frac, line.text)
                    }
                } else it.plainText.orEmpty()
            }.orEmpty(),
            onDismiss = { viewModel.setManualEditOpen(false) },
            onSave = { text -> requestLyricsWrite { track -> viewModel.saveManualLyrics(text, track) } },
            onSearchGoogle = onSearchGoogleLyrics,
            onPasteClipboard = {
                val clipText = clipboardManager.getText()?.text
                if (!clipText.isNullOrBlank()) {
                    clipText
                } else null
            }
        )
    }

    if (showCandidatePicker) {
        LyricsCandidatePickerDialog(
            initialTitle = currentTrack?.title.orEmpty(),
            initialArtist = currentTrack?.artist.orEmpty(),
            candidates = lyricsCandidates,
            isSearching = isSearchingCandidates,
            onDismiss = viewModel::dismissCandidatePicker,
            onSearch = { title, artist -> viewModel.searchCandidatesOnline(title, artist) },
            onSelectCandidate = { candidate ->
                requestLyricsWrite { track -> viewModel.selectCandidate(candidate, track) }
            },
            onResetToDefault = {
                viewModel.resetLyricsToDefault(currentTrack)
                viewModel.dismissCandidatePicker()
            }
        )
    }

    if (showLyricCardDialog && currentTrack != null && lyrics != null && !lyrics!!.isEmpty) {
        LyricCardShareDialog(
            track = currentTrack!!,
            lyrics = lyrics!!,
            initialFocusIndex = activeIndex.coerceAtLeast(0),
            onDismiss = { showLyricCardDialog = false }
        )
    }

    if (showLrcEditorSheet && lyrics != null && !lyrics!!.isEmpty) {
        LrcLineEditorSheet(
            lyrics = lyrics!!,
            livePositionMs = viewModel.livePositionMs,
            onSaveLines = { lines ->
                requestLyricsWrite { track ->
                    viewModel.saveTimestampedLines(lines, track)
                }
            },
            onDismiss = { showLrcEditorSheet = false }
        )
    }

    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    Surface(
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(swipeOffsetX.value.roundToInt(), 0) }
            .pointerInput(isRtl) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            val current = swipeOffsetX.value
                            if (kotlin.math.abs(current) > 120f) {
                                val goNext = if (isRtl) current > 0 else current < 0
                                if (goNext) onNext() else onPrevious()
                            }
                            swipeOffsetX.animateTo(0f, spring())
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch { swipeOffsetX.animateTo(0f, spring()) }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            swipeOffsetX.snapTo((swipeOffsetX.value + dragAmount).coerceIn(-200f, 200f))
                        }
                    }
                )
            }
            .testTag("lyrics_view"),
        color = Color.Transparent,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Dedicated slim header above lyrics so badges and action icons never overlap scrolled text.
            if (!isLoading && lyrics != null && !lyrics!!.isEmpty) {
                com.yasinjahantigh.logarhythm.ui.components.FeatureMicroTip(
                    hintId = com.yasinjahantigh.logarhythm.ui.components.FeatureHintIds.LYRICS_ACTIONS,
                    text = stringResource(R.string.tip_lyrics_actions),
                    icon = Icons.Filled.Share,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
                var showLyricsMenu by remember { mutableStateOf(false) }
                var showTranslateMenu by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (lyrics!!.isSynced) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                        ) {
                            Text(
                                text = stringResource(if (lyrics!!.isSynced) R.string.lyrics_synced_badge else R.string.lyrics_plain_badge),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (lyrics!!.isSynced) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        val lyricsSource = lyrics!!.source.orEmpty()
                        val sourceLabel = when {
                            lyricsSource == "embedded" -> stringResource(R.string.lyrics_source_embedded)
                            lyricsSource == "companion" -> stringResource(R.string.lyrics_source_companion)
                            lyricsSource == "user_manual" -> stringResource(R.string.lyrics_source_manual)
                            lyricsSource == "imported_file" -> stringResource(R.string.lyrics_source_imported)
                            lyricsSource == "user_clipboard" -> stringResource(R.string.lyrics_source_clipboard)
                            lyricsSource.startsWith("manual") -> stringResource(R.string.lyrics_source_manual)
                            lyricsSource == "lrclib" -> stringResource(R.string.lyrics_source_online, stringResource(R.string.lyrics_provider_lrclib))
                            lyricsSource == "netease" -> stringResource(R.string.lyrics_source_online, stringResource(R.string.lyrics_provider_netease))
                            lyricsSource == "kugou" -> stringResource(R.string.lyrics_source_online, stringResource(R.string.lyrics_provider_kugou))
                            lyricsSource == "lyrics_ovh" -> stringResource(R.string.lyrics_source_online, stringResource(R.string.lyrics_provider_lyrics_ovh))
                            lyricsSource == "genius" -> stringResource(R.string.lyrics_source_online, stringResource(R.string.lyrics_provider_genius))
                            lyricsSource == "custom" -> stringResource(R.string.lyrics_source_online, stringResource(R.string.lyrics_provider_custom))
                            else -> stringResource(R.string.lyrics_source_cached)
                        }

                        Text(
                            text = sourceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .testTag("lyrics_source_badge")
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            IconButton(
                                onClick = { showTranslateMenu = true },
                                modifier = Modifier.size(48.dp).testTag("lyrics_translate_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Translate,
                                    contentDescription = stringResource(R.string.lyrics_translate_action),
                                    tint = if (translateLang.isNotBlank()) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showTranslateMenu,
                                onDismissRequest = { showTranslateMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.lyrics_translate_off)) },
                                    trailingIcon = if (translateLang.isBlank()) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                    } else null,
                                    onClick = {
                                        showTranslateMenu = false
                                        viewModel.setTranslateLanguage("")
                                    },
                                    modifier = Modifier.testTag("lyrics_translate_option_off")
                                )
                                val translateOptions = listOf(
                                    "fa" to R.string.lyrics_translate_lang_fa,
                                    "en" to R.string.lyrics_translate_lang_en,
                                    "ar" to R.string.lyrics_translate_lang_ar,
                                    "tr" to R.string.lyrics_translate_lang_tr,
                                    "es" to R.string.lyrics_translate_lang_es,
                                    "fr" to R.string.lyrics_translate_lang_fr,
                                    "de" to R.string.lyrics_translate_lang_de,
                                    "ru" to R.string.lyrics_translate_lang_ru
                                )
                                translateOptions.forEach { (code, labelRes) ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(labelRes)) },
                                        trailingIcon = if (translateLang == code) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                        } else null,
                                        onClick = {
                                            showTranslateMenu = false
                                            viewModel.setTranslateLanguage(code)
                                        },
                                        modifier = Modifier.testTag("lyrics_translate_option_$code")
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { showLyricCardDialog = true },
                            modifier = Modifier.size(48.dp).testTag("lyrics_top_share_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.lyrics_share_card_title),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = { viewModel.searchCandidatesOnline() },
                            modifier = Modifier.size(48.dp).testTag("lyrics_candidates_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ManageSearch,
                                contentDescription = stringResource(R.string.lyrics_search_candidates_btn),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = {
                                trackForLyricsDeletion = currentTrack
                                showDeleteConfirmDialog = trackForLyricsDeletion != null
                            },
                            modifier = Modifier.size(48.dp).testTag("lyrics_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_delete_lyrics),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showLyricsMenu = true },
                                modifier = Modifier.size(48.dp).testTag("lyrics_more_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.player_more_options),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = showLyricsMenu,
                                onDismissRequest = { showLyricsMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.lyrics_edit_action)) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showLyricsMenu = false
                                        viewModel.setManualEditOpen(true)
                                    },
                                    modifier = Modifier.testTag("lyrics_manual_edit_button")
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.cd_import_lrc)) },
                                    leadingIcon = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                                    onClick = {
                                        showLyricsMenu = false
                                        filePicker.launch(arrayOf("*/*", "text/*"))
                                    },
                                    modifier = Modifier.testTag("lyrics_import_button")
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_export_lrc)) },
                                    leadingIcon = { Icon(Icons.Default.SaveAlt, contentDescription = null) },
                                    onClick = {
                                        showLyricsMenu = false
                                        exportPicker.launch("${currentTrack?.title.orEmpty()}.lrc")
                                    },
                                    modifier = Modifier.testTag("lyrics_export_button")
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_search_lyrics_google)) },
                                    leadingIcon = { Icon(Icons.Default.OpenInBrowser, contentDescription = null) },
                                    onClick = {
                                        showLyricsMenu = false
                                        onSearchGoogleLyrics()
                                    },
                                    modifier = Modifier.testTag("lyrics_google_search_button")
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.lyrics_line_spacing_action)) },
                                    leadingIcon = { Icon(Icons.Default.FormatLineSpacing, contentDescription = null) },
                                    onClick = {
                                        showLyricsMenu = false
                                        showLineSpacingDialog = true
                                    },
                                    modifier = Modifier.testTag("lyrics_line_spacing_button")
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.lyrics_offset_action)) },
                                    leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                                    onClick = {
                                        showLyricsMenu = false
                                        showOffsetDialog = true
                                    },
                                    modifier = Modifier.testTag("lyrics_offset_button")
                                )
                                if (lyrics?.isSynced == true) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.lyrics_tap_to_sync)) },
                                        leadingIcon = { Icon(Icons.Default.TouchApp, contentDescription = null) },
                                        onClick = {
                                            showLyricsMenu = false
                                            isTapToSyncMode = true
                                        },
                                        modifier = Modifier.testTag("lyrics_tap_to_sync_button")
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.lyrics_lrc_editor_title)) },
                                    leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                                    onClick = {
                                        showLyricsMenu = false
                                        showLrcEditorSheet = true
                                    },
                                    modifier = Modifier.testTag("lyrics_lrc_editor_button")
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.lyrics_share_card_title)) },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = {
                                        showLyricsMenu = false
                                        showLyricCardDialog = true
                                    },
                                    modifier = Modifier.testTag("lyrics_share_card_button")
                                )
                            }
                        }
                    }
                }
                if (isTranslating || translateLang.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
                            onClick = { viewModel.setTranslateLanguage("") },
                            modifier = Modifier.testTag("lyrics_translate_status_chip")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isTranslating) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        strokeWidth = 1.8.dp,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Text(
                                        text = stringResource(R.string.lyrics_translating),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                } else {
                                    Text(
                                        text = stringResource(
                                            R.string.lyrics_translate_badge,
                                            translateLang.uppercase(Locale.US)
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.lyrics_translate_off),
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.lyrics_loading),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                isHidden -> {
                    HiddenLyricsContent(onRestore = viewModel::restoreLyrics)
                }

                lyrics == null || lyrics?.isEmpty == true -> {
                    LyricsEmptyContent(
                        isOnlineEnabled = isAnyOnlineEnabled,
                        onImportClick = { filePicker.launch(arrayOf("*/*", "text/*")) },
                        onFetchOnlineClick = { viewModel.retryOnlineLyrics() },
                        onSearchGoogleClick = onSearchGoogleLyrics,
                        onPasteClipboardClick = onPasteFromClipboard,
                        onManualEnterClick = { viewModel.setManualEditOpen(true) }
                    )
                }

                lyrics?.isSynced == true -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (isMismatch) {
                            MismatchBanner(
                                onHide = { viewModel.deleteLyrics(removeFromFile = false) },
                                onFindOthers = { viewModel.searchCandidatesOnline() }
                            )
                        }
                        if (isTapToSyncMode) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                tonalElevation = 4.dp
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.TouchApp,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.lyrics_tap_to_sync_banner),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { isTapToSyncMode = false },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                        SyncedLyricsContent(
                            lyrics = lyrics!!,
                            activeIndex = activeIndex,
                            offsetMs = offsetMs,
                            positionFlow = viewModel.livePositionMs,
                            trackDurationMs = currentTrack?.durationMs ?: 0L,
                            translatedLines = translatedLines,
                            lineSpacingDp = lyricsLineSpacingDp,
                            isTapToSyncMode = isTapToSyncMode,
                            onLineClick = { line ->
                                if (isTapToSyncMode) {
                                    val newOffset = viewModel.syncLineToCurrentPosition(line.timestampMs)
                                    if (newOffset != null) {
                                        val offsetSec = String.format(Locale.ROOT, "%+.2f", newOffset / 1000f)
                                        android.widget.Toast.makeText(
                                            context,
                                            context.getString(R.string.lyrics_tap_to_sync_success, offsetSec),
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    isTapToSyncMode = false
                                } else {
                                    viewModel.seekTo((line.timestampMs - offsetMs).coerceAtLeast(0L))
                                }
                            }
                        )
                    }
                }

                else -> {
                    PlainLyricsContent(
                        plainText = lyrics?.plainText.orEmpty(),
                        translatedLines = translatedLines,
                        lineSpacingDp = lyricsLineSpacingDp
                    )
                }
            }
            }
        }
    }
}

/**
 * Fades the top and bottom edges of a scrolling area into the background so lines dissolve
 * instead of being cut off by a hard edge. Works on any background (no colour matching needed).
 */
private fun Modifier.fadingEdges(top: Dp, bottom: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val topPx = top.toPx()
        val bottomPx = bottom.toPx()
        if (topPx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black,
                    startY = 0f,
                    endY = topPx
                ),
                size = Size(size.width, topPx),
                blendMode = BlendMode.DstIn
            )
        }
        if (bottomPx > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Black,
                    1f to Color.Transparent,
                    startY = size.height - bottomPx,
                    endY = size.height
                ),
                topLeft = Offset(0f, size.height - bottomPx),
                size = Size(size.width, bottomPx),
                blendMode = BlendMode.DstIn
            )
        }
    }

/**
 * Synced lyrics, set like a poster: big, bold, start-aligned lines (so Persian hugs the right
 * edge and English the left, line by line, via content-based text direction). The active line is
 * full strength; lines already sung recede the most, upcoming lines stay readable. No bouncing or
 * scaling, so long Persian lines never jitter or clip; the motion is a calm scroll and a fade.
 */
@Composable
private fun SyncedLyricsContent(
    lyrics: Lyrics,
    activeIndex: Int,
    offsetMs: Long,
    positionFlow: kotlinx.coroutines.flow.StateFlow<Long>,
    trackDurationMs: Long = 0L,
    translatedLines: List<String>? = null,
    lineSpacingDp: Float = 12f,
    isTapToSyncMode: Boolean = false,
    onLineClick: (com.yasinjahantigh.logarhythm.domain.model.LyricLine) -> Unit
) {
    val listState = rememberLazyListState()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Collect live position only to compute whether we are currently inside an instrumental break.
    val livePosMs by positionFlow.collectAsStateWithLifecycle()
    val activeGap = remember(lyrics.lines, activeIndex, livePosMs / 250L, offsetMs, trackDurationMs, isTapToSyncMode) {
        if (isTapToSyncMode) null
        else com.yasinjahantigh.logarhythm.domain.lyrics.LyricsInstrumentalDetector.findActiveGap(
            lines = lyrics.lines,
            activeIndex = activeIndex,
            effectivePosMs = livePosMs + offsetMs,
            trackDurationMs = trackDurationMs
        )
    }

    LaunchedEffect(activeIndex) {
        if (!isTapToSyncMode && activeIndex >= 0 && activeIndex < lyrics.lines.size) {
            // +1 accounts for the intro slot item at index 0
            listState.animateScrollToItem(activeIndex.coerceAtLeast(0))
        }
    }

    val lineStyle = MaterialTheme.typography.headlineSmall.copy(
        fontWeight = FontWeight.Bold,
        fontSize = if (isLandscape) 22.sp else 26.sp,
        lineHeight = if (isLandscape) 34.sp else 42.sp,
        textDirection = TextDirection.Content
    )
    val translatedStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Medium,
        fontSize = if (isLandscape) 15.sp else 17.sp,
        lineHeight = if (isLandscape) 22.sp else 25.sp,
        textDirection = TextDirection.Content
    )
    val onSurface = MaterialTheme.colorScheme.onSurface
    val primaryColor = MaterialTheme.colorScheme.primary
    val verticalItemPadding = (lineSpacingDp * (if (isLandscape) 0.45f else 0.55f)).coerceIn(3f, 20f).dp

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy((lineSpacingDp * 0.35f).coerceIn(1f, 14.dp.value).dp),
        contentPadding = PaddingValues(
            top = if (isLandscape) 12.dp else 16.dp,
            bottom = if (isLandscape) 96.dp else 200.dp,
            start = 8.dp,
            end = 8.dp
        ),
        modifier = Modifier
            .fillMaxSize()
            .fadingEdges(top = 24.dp, bottom = 72.dp)
    ) {
        // Intro instrumental slot (strictly visible ONLY while playback is in the intro break; 0dp otherwise)
        item(key = "instrumental_intro") {
            InstrumentalInterludeSlot(
                visible = activeGap?.slotIndex == -1,
                progress = if (activeGap?.slotIndex == -1) activeGap.progress else 0f
            )
        }

        itemsIndexed(
            items = lyrics.lines,
            key = { index, line -> "${line.timestampMs}_$index" }
        ) { index, line ->
            val isExplicitInstrumental = com.yasinjahantigh.logarhythm.domain.lyrics.LyricsInstrumentalDetector.isInstrumentalMarker(line.text)
            if (isExplicitInstrumental) {
                // Explicit blank/instrumental LRC line leaves ZERO trace before or after its exact moment.
                InstrumentalInterludeSlot(
                    visible = activeGap?.slotIndex == index,
                    progress = if (activeGap?.slotIndex == index) activeGap.progress else 0f
                )
            } else {
                val isCurrent = index == activeIndex
                val isDuringBreakAfterThis = activeGap?.slotIndex == index
                val targetAlpha = when {
                    isTapToSyncMode -> 0.9f
                    isCurrent && isDuringBreakAfterThis -> 0.42f
                    isCurrent -> 1f
                    activeIndex < 0 -> 0.5f
                    index < activeIndex -> if (activeIndex - index == 1) 0.34f else 0.24f
                    index - activeIndex == 1 -> 0.62f
                    index - activeIndex == 2 -> 0.5f
                    else -> 0.4f
                }
                val alpha by animateFloatAsState(
                    targetValue = targetAlpha,
                    animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
                    label = "lyric_alpha_$index"
                )
                val lineScale by animateFloatAsState(
                    targetValue = if (isCurrent && !isDuringBreakAfterThis && !isTapToSyncMode) 1.04f else 1f,
                    animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
                    label = "lyric_scale_$index"
                )
                val translatedText = translatedLines?.getOrNull(index)?.takeIf { it.isNotBlank() }

                Column(modifier = Modifier.fillMaxWidth()) {
                    val lineModifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (isTapToSyncMode) primaryColor.copy(alpha = 0.08f)
                            else Color.Transparent
                        )
                        .clickable { onLineClick(line) }
                        .graphicsLayer {
                            this.alpha = alpha
                            scaleX = lineScale
                            scaleY = lineScale
                        }
                        .padding(horizontal = 14.dp, vertical = verticalItemPadding)
                        .testTag("lyric_line_$index")

                    Column(modifier = lineModifier) {
                        if (isCurrent && line.words.isNotEmpty() && !isTapToSyncMode) {
                            KaraokeLine(
                                line = line,
                                offsetMs = offsetMs,
                                positionFlow = positionFlow,
                                style = lineStyle,
                                color = onSurface,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = line.text,
                                style = lineStyle,
                                color = if (isTapToSyncMode) primaryColor else onSurface,
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (translatedText != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = translatedText,
                                style = translatedStyle,
                                color = if (isCurrent) primaryColor.copy(alpha = 0.92f) else onSurface.copy(alpha = 0.72f),
                                textAlign = TextAlign.Start,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Natural instrumental interlude slot right between line[index] and line[index+1]:
                    // expands ONLY during that exact moment and collapses to 0dp before/after!
                    InstrumentalInterludeSlot(
                        visible = isDuringBreakAfterThis,
                        progress = if (isDuringBreakAfterThis) activeGap?.progress ?: 0f else 0f
                    )
                }
            }
        }

        // Outro instrumental slot after the final vocal line when trackDurationMs is known
        item(key = "instrumental_outro") {
            InstrumentalInterludeSlot(
                visible = activeGap?.slotIndex == -2,
                progress = if (activeGap?.slotIndex == -2) activeGap.progress else 0f
            )
        }
    }
}

@Composable
private fun InstrumentalInterludeSlot(
    visible: Boolean,
    progress: Float
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.expandVertically(
            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
            expandFrom = Alignment.CenterVertically
        ) + androidx.compose.animation.fadeIn(animationSpec = tween(320)),
        exit = androidx.compose.animation.shrinkVertically(
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            shrinkTowards = Alignment.CenterVertically
        ) + androidx.compose.animation.fadeOut(animationSpec = tween(240))
    ) {
        InstrumentalInterludeIndicator(progress = progress)
    }
}

@Composable
private fun InstrumentalInterludeIndicator(progress: Float) {
    val infinite = androidx.compose.animation.core.rememberInfiniteTransition(label = "instrumental_interlude")
    val noteFloat by infinite.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "note_float"
    )
    val noteScale by infinite.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.09f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "note_scale"
    )
    val dotPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * kotlin.math.PI).toFloat(),
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart
        ),
        label = "dot_phase"
    )

    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 14.dp)
            .testTag("lyrics_instrumental_indicator"),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            shape = CircleShape,
            color = primary.copy(alpha = 0.14f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = stringResource(R.string.lyrics_instrumental_cd),
                    tint = primary,
                    modifier = Modifier
                        .size(22.dp)
                        .graphicsLayer {
                            translationY = noteFloat
                            scaleX = noteScale
                            scaleY = noteScale
                        }
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(3) { i ->
                        val wave = (kotlin.math.sin((dotPhase - i * 0.9f).toDouble()).toFloat() + 1f) * 0.5f
                        val dotProgressBoost = ((progress * 3f - i).coerceIn(0f, 1f)) * 0.35f
                        val dotAlpha = (0.35f + wave * 0.45f + dotProgressBoost).coerceIn(0.25f, 1f)
                        val dotScale = 0.78f + wave * 0.36f
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .graphicsLayer {
                                    alpha = dotAlpha
                                    scaleX = dotScale
                                    scaleY = dotScale
                                    translationY = -wave * 4f
                                }
                                .clip(CircleShape)
                                .background(primary)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsLineSpacingDialog(
    currentSpacingDp: Float,
    onSpacingChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    var localSpacing by remember(currentSpacingDp) { mutableFloatStateOf(currentSpacingDp) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("lyrics_line_spacing_dialog"),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FormatLineSpacing,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.lyrics_line_spacing_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf(
                        6f to R.string.lyrics_line_spacing_compact,
                        14f to R.string.lyrics_line_spacing_normal,
                        24f to R.string.lyrics_line_spacing_spacious
                    )
                    presets.forEach { (presetDp, labelRes) ->
                        val selected = kotlin.math.abs(localSpacing - presetDp) <= 2.5f
                        androidx.compose.material3.FilterChip(
                            selected = selected,
                            onClick = {
                                localSpacing = presetDp
                                onSpacingChange(presetDp)
                            },
                            label = { Text(stringResource(labelRes)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                androidx.compose.material3.Slider(
                    value = localSpacing,
                    onValueChange = {
                        localSpacing = it
                        onSpacingChange(it)
                    },
                    valueRange = 4f..32f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lyrics_line_spacing_slider")
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_close))
            }
        }
    )
}

/** Word-by-word highlight for enhanced LRC. Only this line collects the playback position. */
@Composable
private fun KaraokeLine(
    line: com.yasinjahantigh.logarhythm.domain.model.LyricLine,
    offsetMs: Long,
    positionFlow: kotlinx.coroutines.flow.StateFlow<Long>,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier
) {
    val position by positionFlow.collectAsStateWithLifecycle()
    val now = position + offsetMs
    val text = androidx.compose.ui.text.buildAnnotatedString {
        line.words.forEach { word ->
            val alpha = if (now >= word.startMs) 1f else 0.42f
            withStyle(androidx.compose.ui.text.SpanStyle(color = color.copy(alpha = alpha))) { append(word.text) }
        }
    }
    Text(text = text, style = style, textAlign = TextAlign.Start, modifier = modifier)
}

@Composable
private fun MismatchBanner(onHide: () -> Unit, onFindOthers: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).testTag("lyrics_mismatch_banner")
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = stringResource(R.string.lyrics_mismatch_banner),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                androidx.compose.material3.TextButton(onClick = onFindOthers) {
                    Text(stringResource(R.string.lyrics_mismatch_find_others))
                }
                androidx.compose.material3.TextButton(onClick = onHide) {
                    Text(stringResource(R.string.lyrics_mismatch_hide))
                }
            }
        }
    }
}

@Composable
private fun HiddenLyricsContent(onRestore: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.lyrics_hidden_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.lyrics_hidden_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        androidx.compose.material3.FilledTonalButton(
            onClick = onRestore,
            modifier = Modifier.testTag("lyrics_restore_button")
        ) { Text(stringResource(R.string.lyrics_restore)) }
    }
}

@Composable
private fun PlainLyricsContent(
    plainText: String,
    translatedLines: List<String>? = null,
    lineSpacingDp: Float = 12f
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val computedLineHeight = (24f + lineSpacingDp * 0.85f).coerceIn(26f, 52f).sp
    val primaryStyle = MaterialTheme.typography.titleMedium.copy(
        fontSize = 19.sp,
        lineHeight = computedLineHeight,
        textDirection = TextDirection.Content
    )
    val translatedStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = 15.sp,
        lineHeight = 22.sp,
        textDirection = TextDirection.Content
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .fadingEdges(top = 20.dp, bottom = 40.dp)
            .verticalScroll(rememberScrollState())
            .padding(
                top = if (isLandscape) 12.dp else 16.dp,
                bottom = if (isLandscape) 32.dp else 48.dp,
                start = 22.dp,
                end = 22.dp
            )
    ) {
        if (translatedLines.isNullOrEmpty()) {
            Text(
                text = plainText,
                style = primaryStyle,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                textAlign = TextAlign.Start
            )
        } else {
            val rawLines = remember(plainText) { plainText.lines() }
            rawLines.forEachIndexed { index, rawLine ->
                if (rawLine.isBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                } else {
                    Text(
                        text = rawLine,
                        style = primaryStyle,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.94f),
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val translation = translatedLines.getOrNull(index)?.takeIf { it.isNotBlank() }
                    if (translation != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = translation,
                            style = translatedStyle,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.86f),
                            textAlign = TextAlign.Start,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height((lineSpacingDp * 0.55f).coerceIn(4f, 16f).dp))
                }
            }
        }
    }
}

@Composable
private fun LyricsEmptyContent(
    isOnlineEnabled: Boolean,
    onImportClick: () -> Unit,
    onFetchOnlineClick: () -> Unit,
    onSearchGoogleClick: () -> Unit,
    onPasteClipboardClick: () -> Unit,
    onManualEnterClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.lyrics_empty_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.lyrics_empty_prompt_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        if (isOnlineEnabled) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.vpn_proactive_lyrics_hint),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        Spacer(modifier = Modifier.height(20.dp))

        // Spacious vertical layout preventing cramped rows and text overlaps
        Column(
            modifier = Modifier.fillMaxWidth(0.92f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isOnlineEnabled) {
                FilledTonalButton(
                    onClick = onFetchOnlineClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("lyrics_fetch_online_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.lyrics_search_candidates_btn),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            OutlinedButton(
                onClick = onSearchGoogleClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("lyrics_search_google_button")
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInBrowser,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_search_lyrics_google),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            OutlinedButton(
                onClick = onPasteClipboardClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("lyrics_paste_clipboard_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.action_paste_lyrics),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            OutlinedButton(
                onClick = onImportClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("lyrics_empty_import_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FileUpload,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.btn_import_lrc),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            TextButton(
                onClick = onManualEnterClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .testTag("lyrics_enter_manually_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.lyrics_enter_manually_btn),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun ManualLyricsDialog(
    initialContent: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onSearchGoogle: () -> Unit,
    onPasteClipboard: () -> String?
) {
    var text by remember { mutableStateOf(initialContent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.lyrics_manual_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.lyrics_manual_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clip = onPasteClipboard()
                            if (clip != null) text = clip
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(R.string.action_paste_lyrics), style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = onSearchGoogle,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(R.string.action_search_lyrics_google), style = MaterialTheme.typography.labelMedium)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text(stringResource(R.string.lyrics_manual_dialog_placeholder)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 280.dp)
                        .testTag("manual_lyrics_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(text) },
                enabled = text.isNotBlank(),
                modifier = Modifier.testTag("manual_lyrics_save_btn")
            ) {
                Text(text = stringResource(R.string.save_lyrics))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.btn_cancel))
            }
        }
    )
}

@Composable
private fun LyricsCandidatePickerDialog(
    initialTitle: String,
    initialArtist: String,
    candidates: List<LyricsCandidate>,
    isSearching: Boolean,
    onDismiss: () -> Unit,
    onSearch: (String, String) -> Unit,
    onSelectCandidate: (LyricsCandidate) -> Unit,
    onResetToDefault: () -> Unit
) {
    var titleQuery by remember { mutableStateOf(initialTitle) }
    var artistQuery by remember { mutableStateOf(initialArtist) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.lyrics_candidates_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Search inputs so user can adjust messy title/artist tags
                OutlinedTextField(
                    value = titleQuery,
                    onValueChange = { titleQuery = it },
                    label = { Text(stringResource(R.string.lyrics_custom_query_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("candidate_title_input")
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = artistQuery,
                    onValueChange = { artistQuery = it },
                    label = { Text(stringResource(R.string.lyrics_custom_artist_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("candidate_artist_input")
                )
                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { onSearch(titleQuery, artistQuery) },
                    enabled = titleQuery.isNotBlank() && !isSearching,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("candidate_search_btn")
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.lyrics_loading))
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.lyrics_search_custom_btn))
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                when {
                    isSearching -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    candidates.isEmpty() -> {
                        Text(
                            text = stringResource(R.string.lyrics_candidates_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        )
                    }
                    else -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            candidates.forEach { candidate ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectCandidate(candidate) }
                                        .testTag("candidate_item_${candidate.id}"),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        val candidateTitle = candidate.trackName.takeUnless {
                                            it.isBlank() || it.equals("null", ignoreCase = true) || it.equals("<unknown>", ignoreCase = true)
                                        } ?: stringResource(R.string.unknown_title)

                                        val cleanArtist = candidate.artistName.takeUnless {
                                            it.isBlank() || it.equals("null", ignoreCase = true) || it.equals("<unknown>", ignoreCase = true) || it.equals("unknown artist", ignoreCase = true)
                                        }
                                        val cleanAlbum = candidate.albumName.takeUnless {
                                            it.isBlank() || it.equals("null", ignoreCase = true) || it.equals("<unknown>", ignoreCase = true)
                                        }
                                        val subtitle = listOfNotNull(cleanArtist, cleanAlbum).joinToString(" • ").ifBlank {
                                            stringResource(R.string.unknown_artist)
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = candidateTitle,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (candidate.isSynced)
                                                    MaterialTheme.colorScheme.primaryContainer
                                                else MaterialTheme.colorScheme.surfaceContainerHigh
                                            ) {
                                                Text(
                                                    text = stringResource(
                                                        if (candidate.isSynced) R.string.lyrics_synced_badge
                                                        else R.string.lyrics_plain_badge
                                                    ),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (candidate.isSynced)
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                                            ) {
                                                Text(
                                                    text = candidate.sourceName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }

                                            FilledTonalButton(
                                                onClick = { onSelectCandidate(candidate) },
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.lyrics_search_select),
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onResetToDefault,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(R.string.lyrics_reset_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.btn_cancel))
            }
        }
    )
}
