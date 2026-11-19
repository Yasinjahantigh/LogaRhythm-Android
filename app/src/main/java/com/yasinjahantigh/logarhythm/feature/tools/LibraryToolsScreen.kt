package com.yasinjahantigh.logarhythm.feature.tools

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.core.utils.rememberMediaStoreConsent
import com.yasinjahantigh.logarhythm.domain.library.TagSuggestionCleaner
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.feature.library.TrackListItem
import com.yasinjahantigh.logarhythm.feature.playlist.AddToPlaylistDialog
import com.yasinjahantigh.logarhythm.feature.playlist.PlaylistViewModel
import com.yasinjahantigh.logarhythm.feature.playlist.SongMenuBottomSheet
import com.yasinjahantigh.logarhythm.ui.components.ArtworkImage
import com.yasinjahantigh.logarhythm.ui.designsystem.LocalPlaybackBottomPadding

@Composable
fun LibraryToolsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onEditTrack: (String) -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    initialTab: Int = 0,
    viewModel: LibraryToolsViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val duplicates by viewModel.duplicates.collectAsStateWithLifecycle()
    val trackFileSizes by viewModel.trackFileSizes.collectAsStateWithLifecycle()
    val missing by viewModel.missingTags.collectAsStateWithLifecycle()
    val hiddenTracks by viewModel.hiddenTracks.collectAsStateWithLifecycle()
    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playlists by playlistViewModel.playlists.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val findingFixes by viewModel.findingFixes.collectAsStateWithLifecycle()
    val scanProcessed by viewModel.scanProcessed.collectAsStateWithLifecycle()
    val scanTotal by viewModel.scanTotal.collectAsStateWithLifecycle()
    val currentScanningTrack by viewModel.currentScanningTrack.collectAsStateWithLifecycle()
    val onlineMatchesCount by viewModel.onlineMatchesCount.collectAsStateWithLifecycle()
    val suggestVpn by viewModel.suggestVpn.collectAsStateWithLifecycle()
    val reSearchingTrackId by viewModel.reSearchingTrackId.collectAsStateWithLifecycle()
    val applyingProgress by viewModel.applyingProgress.collectAsStateWithLifecycle()
    val aiSuggestions by viewModel.aiSuggestions.collectAsStateWithLifecycle()
    val findingAiFixes by viewModel.findingAiFixes.collectAsStateWithLifecycle()
    val savingFix by viewModel.savingFix.collectAsStateWithLifecycle()
    val fixFailed by viewModel.fixFailed.collectAsStateWithLifecycle()
    val pendingScanPlan by viewModel.pendingScanPlan.collectAsStateWithLifecycle()
    val scannedTrackCount by viewModel.scannedTrackCount.collectAsStateWithLifecycle()

    val mediaStoreConsent = rememberMediaStoreConsent()
    val context = LocalContext.current

    LaunchedEffect(fixFailed) {
        if (fixFailed) {
            Toast.makeText(context, R.string.tools_fix_failed, Toast.LENGTH_SHORT).show()
        }
    }

    // Tab 0 = Auto Tag & Cover Fixer (primary!), 1 = Missing Tags, 2 = Duplicates, 3 = Hidden, 4 = AI Cleaner
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var editingSuggestion by remember { mutableStateOf<Pair<TagFixSuggestion, Boolean>?>(null) }
    var showDeleteAllDuplicatesConfirm by remember { mutableStateOf(false) }

    menuTrack?.let { track ->
        SongMenuBottomSheet(
            track = track,
            onDismissRequest = { menuTrack = null },
            onPlayNext = { viewModel.playNext(track) },
            onAddToQueue = { viewModel.addToQueue(track) },
            onAddToPlaylist = { showAddToPlaylist = true },
            onDeleteTrack = {
                menuTrack = null
                mediaStoreConsent.delete(track, onApproved = { approved ->
                    viewModel.deleteTrack(approved) { deleted ->
                        Toast.makeText(
                            context,
                            context.getString(if (deleted) R.string.song_deleted else R.string.track_delete_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                })
            },
            onEditTrack = { onEditTrack(track.id) },
            onHideTrack = { viewModel.hideTrack(track) }
        )
        if (showAddToPlaylist) {
            AddToPlaylistDialog(
                playlists = playlists,
                onDismiss = { showAddToPlaylist = false },
                onSelectPlaylist = { playlist ->
                    playlistViewModel.addTrackToPlaylist(playlist.id, track.id)
                    showAddToPlaylist = false
                },
                onCreatePlaylist = { name -> playlistViewModel.createPlaylist(name) }
            )
        }
    }

    if (showDeleteAllDuplicatesConfirm) {
        val extrasToDelete = remember(duplicates) {
            com.yasinjahantigh.logarhythm.domain.library.DuplicateFinder.extraDuplicatesToRemove(duplicates.orEmpty())
        }
        val totalExtraBytes = remember(extrasToDelete, trackFileSizes) {
            extrasToDelete.sumOf { trackFileSizes[it.id] ?: 0L }
        }
        AlertDialog(
            onDismissRequest = { showDeleteAllDuplicatesConfirm = false },
            title = { Text(stringResource(R.string.tools_duplicates_confirm_all_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.tools_duplicates_confirm_all_body,
                        extrasToDelete.size,
                        formatBytesHuman(totalExtraBytes)
                    )
                )
            },
            confirmButton = {
                Button(
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    onClick = {
                        showDeleteAllDuplicatesConfirm = false
                        mediaStoreConsent.deleteTracks(extrasToDelete, onApproved = { approved ->
                            viewModel.deleteTracks(approved) { count ->
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.tools_duplicates_deleted_count_toast, count),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        })
                    },
                    modifier = Modifier.testTag("tools_duplicates_confirm_delete_all")
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDuplicatesConfirm = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    editingSuggestion?.let { (suggestion, isAi) ->
        EditSuggestionDialog(
            suggestion = suggestion,
            onDismiss = { editingSuggestion = null },
            onSave = { updatedTags ->
                viewModel.updateSuggestion(suggestion.track.id, updatedTags, isAi = isAi)
                editingSuggestion = null
            },
            onReSearch = { titleQuery, artistQuery ->
                editingSuggestion = null
                viewModel.reSearchTrackOnline(
                    trackId = suggestion.track.id,
                    queryTitle = titleQuery,
                    queryArtist = artistQuery,
                    isAi = isAi
                ) { found ->
                    Toast.makeText(
                        context,
                        context.getString(
                            if (found) R.string.tools_research_found else R.string.tools_research_not_found_vpn
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    pendingScanPlan?.let { plan ->
        AlertDialog(
            onDismissRequest = viewModel::dismissScanConfirm,
            title = {
                Text(text = stringResource(R.string.tools_rescan_dialog_title))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = if (plan.remainingUnscannedCount > 0) {
                            stringResource(
                                R.string.tools_rescan_dialog_remaining_msg,
                                plan.alreadyScannedCount,
                                plan.remainingUnscannedCount
                            )
                        } else {
                            stringResource(
                                R.string.tools_rescan_dialog_all_done_msg,
                                plan.totalCandidates
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (plan.remainingUnscannedCount > 0) {
                        Button(
                            onClick = viewModel::confirmScanRemaining,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tools_confirm_scan_remaining")
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.tools_rescan_btn_remaining,
                                    plan.remainingUnscannedCount
                                )
                            )
                        }
                        FilledTonalButton(
                            onClick = viewModel::confirmRescanAll,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tools_confirm_rescan_all")
                        ) {
                            Text(text = stringResource(R.string.tools_rescan_btn_all))
                        }
                    }
                }
            },
            confirmButton = {
                if (plan.remainingUnscannedCount == 0) {
                    Button(
                        onClick = viewModel::confirmRescanAll,
                        modifier = Modifier.testTag("tools_confirm_rescan_all")
                    ) {
                        Text(text = stringResource(R.string.tools_rescan_btn_all))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissScanConfirm,
                    modifier = Modifier.testTag("tools_confirm_scan_cancel")
                ) {
                    Text(text = stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_tools_screen"),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 920.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("library_tools_back")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_close))
                }
                Text(
                    text = stringResource(R.string.settings_library_tools_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .horizontalScroll(rememberScrollState())
            ) {
                FilterChip(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    leadingIcon = {
                        Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = {
                        Text(
                            stringResource(R.string.tools_regex_cleaner) +
                                (suggestions?.let { " (${it.size})" } ?: "")
                        )
                    },
                    modifier = Modifier.testTag("tools_tab_fixer")
                )
                FilterChip(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    label = {
                        Text(
                            stringResource(R.string.tools_missing_tags) +
                                (missing?.let { " (${it.size})" } ?: "")
                        )
                    },
                    modifier = Modifier.testTag("tools_tab_missing")
                )
                FilterChip(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    label = {
                        Text(
                            stringResource(R.string.tools_duplicates) +
                                (duplicates?.let { " (${it.size})" } ?: "")
                        )
                    },
                    modifier = Modifier.testTag("tools_tab_duplicates")
                )
                FilterChip(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    label = {
                        Text(
                            stringResource(R.string.tools_hidden_tracks) +
                                (hiddenTracks?.let { " (${it.size})" } ?: "")
                        )
                    },
                    modifier = Modifier.testTag("tools_tab_hidden")
                )
                FilterChip(
                    selected = tab == 4,
                    onClick = {
                        tab = 4
                        if (viewModel.isAiConfigured && aiSuggestions == null) {
                            viewModel.suggestAiFixes()
                        }
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    label = {
                        Text(
                            stringResource(R.string.tools_ai_cleaner) +
                                (aiSuggestions?.let { " (${it.size})" } ?: "")
                        )
                    },
                    modifier = Modifier.testTag("tools_tab_ai")
                )
            }

            val bottom = LocalPlaybackBottomPadding.current
            LazyColumn(
                contentPadding = PaddingValues(top = 10.dp, bottom = bottom + 24.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                when (tab) {
                    0 -> {
                        // Primary Hero Card: Step-by-step Tag & Cover Fixer
                        item(key = "fixer_hero") {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .animateContentSize()
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Filled.AutoFixHigh,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.tools_fixer_hero_title),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = stringResource(R.string.tools_fixer_hero_desc),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Proactive VPN recommendation chip
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.VpnLock,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = stringResource(R.string.vpn_proactive_fixer_hint),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }

                                    // Action buttons or live scan progress
                                    if (findingFixes) {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            val fraction = if (scanTotal > 0) {
                                                (scanProcessed.toFloat() / scanTotal.toFloat()).coerceIn(0f, 1f)
                                            } else 0f
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = stringResource(
                                                        R.string.tools_scan_progress_fmt,
                                                        scanProcessed,
                                                        scanTotal
                                                    ),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = stringResource(
                                                        R.string.tools_online_matches_fmt,
                                                        onlineMatchesCount
                                                    ),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (scanTotal > 0) {
                                                LinearProgressIndicator(
                                                    progress = { fraction },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(6.dp)
                                                )
                                            } else {
                                                LinearProgressIndicator(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(6.dp)
                                                )
                                            }
                                            if (currentScanningTrack.isNotBlank()) {
                                                Text(
                                                    text = currentScanningTrack,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = viewModel::cancelScan,
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier.align(Alignment.End)
                                            ) {
                                                Text(stringResource(R.string.btn_cancel))
                                            }
                                        }
                                    } else {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Button(
                                                onClick = { viewModel.requestScan(includeCleanTracks = false) },
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .testTag("tools_start_smart_scan")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Search,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(stringResource(R.string.tools_scan_messy_btn))
                                            }
                                            FilledTonalButton(
                                                onClick = { viewModel.requestScan(includeCleanTracks = true) },
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier.testTag("tools_start_deep_scan")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Refresh,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(stringResource(R.string.tools_scan_all_btn))
                                            }
                                        }
                                        if (scannedTrackCount > 0) {
                                            Text(
                                                text = stringResource(R.string.tools_scanned_so_far_fmt, scannedTrackCount),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "fixer_micro_tip") {
                            com.yasinjahantigh.logarhythm.ui.components.FeatureMicroTip(
                                hintId = com.yasinjahantigh.logarhythm.ui.components.FeatureHintIds.TAG_FIXER_BG,
                                text = stringResource(R.string.tip_tag_fixer_bg),
                                icon = Icons.Filled.AutoFixHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }

                        // VPN Warning banner when online queries failed
                        if (suggestVpn) {
                            item(key = "fixer_vpn_warning") {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.VpnLock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = stringResource(R.string.tools_online_failed_vpn_hint),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }

                        // Applying progress banner
                        applyingProgress?.let { (done, total) ->
                            item(key = "applying_progress") {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(
                                            text = stringResource(R.string.tools_applying_progress_fmt, done, total),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            progress = { (done.toFloat() / total.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }

                        val list = suggestions
                        if (!findingFixes && list != null && list.isEmpty()) {
                            item { EmptyNote(stringResource(R.string.tools_no_fixes)) }
                        }
                        if (list != null && list.isNotEmpty()) {
                            item(key = "regex_apply_all_header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.tools_suggestions_count_fmt, list.size),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Button(
                                        enabled = !findingFixes && savingFix == null && applyingProgress == null,
                                        onClick = {
                                            val tracksToConsent = list.map { it.track }
                                            mediaStoreConsent.writeTracks(tracksToConsent, onApproved = {
                                                viewModel.applyAllSuggestions(isAi = false) { count ->
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.tools_updated_count_toast, count),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            })
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.testTag("tools_apply_all_button")
                                    ) {
                                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.tools_apply_all))
                                    }
                                }
                            }
                            items(list, key = { s -> "fix_" + s.track.id }) { suggestion ->
                                TagSuggestionCard(
                                    suggestion = suggestion,
                                    isAi = false,
                                    saving = savingFix == suggestion.track.id || applyingProgress != null,
                                    reSearching = reSearchingTrackId == suggestion.track.id,
                                    onEdit = { editingSuggestion = suggestion to false },
                                    onDismiss = { viewModel.dismissSuggestion(suggestion.track.id, isAi = false) },
                                    onApply = {
                                        mediaStoreConsent.write(suggestion.track, onApproved = {
                                            viewModel.applySuggestion(suggestion.track.id, isAi = false)
                                        })
                                    }
                                )
                            }
                        }
                    }

                    1 -> {
                        val tracks = missing
                        if (tracks != null && tracks.isEmpty()) item { EmptyNote(stringResource(R.string.tools_no_missing)) }
                        tracks?.let { list ->
                            items(list, key = { "m_" + it.id }) { track ->
                                TrackRow(
                                    track = track,
                                    current = currentTrack,
                                    isPlaying = isPlaying,
                                    onPlay = viewModel::play,
                                    onMenu = { menuTrack = it },
                                    onPlayNext = viewModel::playNext
                                )
                            }
                        }
                    }

                    2 -> {
                        val groups = duplicates
                        if (groups != null && groups.isEmpty()) item { EmptyNote(stringResource(R.string.tools_no_duplicates)) }
                        if (groups != null && groups.isNotEmpty()) {
                            val allExtras = com.yasinjahantigh.logarhythm.domain.library.DuplicateFinder.extraDuplicatesToRemove(groups)
                            val totalExtraBytes = allExtras.sumOf { trackFileSizes[it.id] ?: 0L }
                            item(key = "dup_summary_banner") {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = stringResource(
                                                R.string.tools_duplicates_summary_fmt,
                                                groups.size,
                                                allExtras.size,
                                                formatBytesHuman(totalExtraBytes)
                                            ),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Button(
                                            onClick = { showDeleteAllDuplicatesConfirm = true },
                                            enabled = allExtras.isNotEmpty(),
                                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error
                                            ),
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("tools_duplicates_delete_all_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteSweep,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.tools_duplicates_delete_all_btn, allExtras.size))
                                        }
                                    }
                                }
                            }
                            groups.forEach { group ->
                                item(key = "h_" + group.key) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.tools_duplicate_group, group.tracks.size),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        val reasonRes = when (group.reason) {
                                            com.yasinjahantigh.logarhythm.domain.library.DuplicateMatchReason.SAME_FILE_SIZE ->
                                                R.string.tools_duplicates_reason_size
                                            com.yasinjahantigh.logarhythm.domain.library.DuplicateMatchReason.MULTI_SOURCE ->
                                                R.string.tools_duplicates_reason_multisource
                                            com.yasinjahantigh.logarhythm.domain.library.DuplicateMatchReason.SAME_METADATA ->
                                                R.string.tools_duplicates_reason_tags
                                        }
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                text = stringResource(reasonRes),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                                items(group.tracks, key = { "d_" + group.key + "_" + it.id }) { track ->
                                    val isBest = track.id == group.recommendedKeepId
                                    val sizeBytes = trackFileSizes[track.id] ?: 0L
                                    val bitrateKbps = com.yasinjahantigh.logarhythm.domain.library.DuplicateFinder.estimateBitrateKbps(
                                        sizeBytes,
                                        track.durationMs
                                    )
                                    val metaParts = buildList {
                                        if (sizeBytes > 0L) add(formatBytesHuman(sizeBytes))
                                        if (bitrateKbps != null) add("~${bitrateKbps} kbps")
                                        add(com.yasinjahantigh.logarhythm.core.utils.TimeUtils.formatDuration(track.durationMs))
                                        val folderOrPath = track.folder.ifBlank {
                                            track.filePath.substringAfterLast('/', "").ifBlank { track.filePath }
                                        }
                                        if (folderOrPath.isNotBlank()) add(folderOrPath)
                                    }.joinToString(" · ")

                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isBest) {
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerLow
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 3.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(bottom = 8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(modifier = Modifier.weight(1f)) {
                                                    TrackRow(
                                                        track = track,
                                                        current = currentTrack,
                                                        isPlaying = isPlaying,
                                                        onPlay = viewModel::play,
                                                        onMenu = { menuTrack = it },
                                                        onPlayNext = viewModel::playNext
                                                    )
                                                }
                                                IconButton(
                                                    onClick = {
                                                        mediaStoreConsent.delete(track, onApproved = { approved ->
                                                            viewModel.deleteTrack(approved) { deleted ->
                                                                Toast.makeText(
                                                                    context,
                                                                    context.getString(
                                                                        if (deleted) R.string.song_deleted else R.string.track_delete_failed
                                                                    ),
                                                                    Toast.LENGTH_SHORT
                                                                ).show()
                                                            }
                                                        })
                                                    },
                                                    modifier = Modifier
                                                        .padding(end = 6.dp)
                                                        .size(48.dp)
                                                        .testTag("duplicate_delete_${track.id}")
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.DeleteOutline,
                                                        contentDescription = stringResource(R.string.tools_duplicates_delete_one_cd),
                                                        tint = if (isBest) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                if (isBest) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = MaterialTheme.colorScheme.primaryContainer
                                                    ) {
                                                        Text(
                                                            text = stringResource(R.string.tools_duplicates_best_badge),
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = metaParts,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                            if (track.filePath.isNotBlank()) {
                                                Text(
                                                    text = track.filePath,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    3 -> {
                        val hidden = hiddenTracks
                        if (hidden != null && hidden.isEmpty()) {
                            item { EmptyNote(stringResource(R.string.tools_no_hidden_tracks)) }
                        }
                        if (hidden != null && hidden.isNotEmpty()) {
                            item(key = "restore_all_header") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = { viewModel.unhideAllTracks() }) {
                                        Text(stringResource(R.string.btn_restore_all))
                                    }
                                }
                            }
                            items(hidden, key = { "h_" + it.id }) { track ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(end = 12.dp)
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        TrackRow(
                                            track = track,
                                            current = currentTrack,
                                            isPlaying = isPlaying,
                                            onPlay = viewModel::play,
                                            onMenu = { menuTrack = it },
                                            onPlayNext = viewModel::playNext
                                        )
                                    }
                                    TextButton(onClick = { viewModel.unhideTrack(track.id) }) {
                                        Text(stringResource(R.string.btn_restore))
                                    }
                                }
                            }
                        }
                    }

                    4 -> {
                        if (!viewModel.isAiConfigured) {
                            item {
                                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                    Column(
                                        modifier = Modifier.padding(18.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Filled.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = stringResource(R.string.tools_ai_not_configured_title),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = stringResource(R.string.tools_ai_not_configured_desc),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Button(onClick = onNavigateToSettings) {
                                            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(stringResource(R.string.tools_ai_configure_btn))
                                        }
                                    }
                                }
                            }
                        } else {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.tools_ai_cleaner),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Button(
                                        onClick = { viewModel.suggestAiFixes() },
                                        enabled = !findingAiFixes
                                    ) {
                                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(stringResource(R.string.tools_ai_scan_btn))
                                    }
                                }
                            }
                            if (findingAiFixes) {
                                item {
                                    Column(
                                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CircularProgressIndicator()
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = stringResource(R.string.tools_ai_scanning),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            val aiList = aiSuggestions
                            if (!findingAiFixes && aiList != null && aiList.isEmpty()) {
                                item { EmptyNote(stringResource(R.string.tools_no_fixes)) }
                            }
                            if (!findingAiFixes && aiList != null && aiList.isNotEmpty()) {
                                item(key = "ai_apply_all_header") {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.tools_suggestions_count_fmt, aiList.size),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Button(
                                            onClick = {
                                                val tracksToConsent = aiList.map { it.track }
                                                mediaStoreConsent.writeTracks(tracksToConsent, onApproved = {
                                                    viewModel.applyAllSuggestions(isAi = true) { count ->
                                                        Toast.makeText(
                                                            context,
                                                            context.getString(R.string.tools_updated_count_toast, count),
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                })
                                            }
                                        ) {
                                            Text(stringResource(R.string.tools_apply_all))
                                        }
                                    }
                                }
                            }
                            aiList?.let {
                                items(it, key = { s -> "ai_fix_" + s.track.id }) { suggestion ->
                                    TagSuggestionCard(
                                        suggestion = suggestion,
                                        isAi = true,
                                        saving = savingFix == suggestion.track.id || applyingProgress != null,
                                        reSearching = reSearchingTrackId == suggestion.track.id,
                                        onEdit = { editingSuggestion = suggestion to true },
                                        onDismiss = { viewModel.dismissSuggestion(suggestion.track.id, isAi = true) },
                                        onApply = {
                                            mediaStoreConsent.write(suggestion.track, onApproved = {
                                                viewModel.applySuggestion(suggestion.track.id, isAi = true)
                                            })
                                        }
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

@Composable
private fun TrackRow(
    track: Track,
    current: Track?,
    isPlaying: Boolean,
    onPlay: (Track) -> Unit,
    onMenu: (Track) -> Unit,
    onPlayNext: (Track) -> Unit
) {
    val isCurrent = current?.id == track.id
    TrackListItem(
        track = track,
        onClick = { onPlay(track) },
        isCurrentTrack = isCurrent,
        isPlaying = isCurrent && isPlaying,
        onMenuClick = { onMenu(track) },
        onPlayNext = { onPlayNext(track) }
    )
}

@Composable
private fun TagSuggestionCard(
    suggestion: TagFixSuggestion,
    isAi: Boolean,
    saving: Boolean,
    reSearching: Boolean,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
    onApply: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ArtworkImage(
                    uri = suggestion.artworkUrl ?: suggestion.track.artworkUri,
                    fallbackKey = suggestion.tags.title,
                    modifier = Modifier.size(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentDescription = suggestion.tags.title
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isAi) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = suggestion.tags.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = suggestion.tags.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (suggestion.matchedOnline) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CloudDone,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.tools_badge_online_match),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        if (suggestion.artworkUrl != null) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = stringResource(R.string.tools_new_cover_found),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onDismiss, enabled = !saving) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.btn_cancel),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            FixDiff(stringResource(R.string.tag_editor_field_title), suggestion.track.title, suggestion.tags.title)
            FixDiff(stringResource(R.string.tag_editor_field_artist), suggestion.track.artist, suggestion.tags.artist)
            FixDiff(stringResource(R.string.tag_editor_field_album), suggestion.track.album, suggestion.tags.album)
            FixDiff(stringResource(R.string.tag_editor_field_genre), suggestion.track.genre, suggestion.tags.genre)
            FixDiff(
                stringResource(R.string.tag_editor_field_year),
                if (suggestion.track.year > 0) suggestion.track.year.toString() else "—",
                if (suggestion.tags.year > 0) suggestion.tags.year.toString() else "—"
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (reSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
                TextButton(enabled = !saving && !reSearching, onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.tools_edit_or_research))
                }
                FilledTonalButton(
                    enabled = !saving && !reSearching,
                    onClick = onApply,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    AnimatedVisibility(visible = saving) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    }
                    if (!saving) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.btn_apply))
                }
            }
        }
    }
}

@Composable
private fun EditSuggestionDialog(
    suggestion: TagFixSuggestion,
    onDismiss: () -> Unit,
    onSave: (TagSuggestionCleaner.Tags) -> Unit,
    onReSearch: (String, String) -> Unit
) {
    var title by remember(suggestion) { mutableStateOf(suggestion.tags.title) }
    var artist by remember(suggestion) { mutableStateOf(suggestion.tags.artist) }
    var album by remember(suggestion) { mutableStateOf(suggestion.tags.album) }
    var genre by remember(suggestion) { mutableStateOf(suggestion.tags.genre) }
    var yearText by remember(suggestion) {
        mutableStateOf(if (suggestion.tags.year > 0) suggestion.tags.year.toString() else "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tools_edit_suggestion_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.tag_editor_field_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text(stringResource(R.string.tag_editor_field_artist)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text(stringResource(R.string.tag_editor_field_album)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = genre,
                        onValueChange = { genre = it },
                        label = { Text(stringResource(R.string.tag_editor_field_genre)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = yearText,
                        onValueChange = { yearText = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.tag_editor_field_year)) },
                        singleLine = true,
                        modifier = Modifier.width(96.dp)
                    )
                }
                OutlinedButton(
                    onClick = { onReSearch(title, artist) },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.tools_research_online_btn))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        suggestion.tags.copy(
                            title = title.trim().ifBlank { suggestion.tags.title },
                            artist = artist.trim().ifBlank { suggestion.tags.artist },
                            album = album.trim(),
                            genre = genre.trim(),
                            year = yearText.toIntOrNull()?.takeIf { it in 1900..2100 } ?: 0
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}

@Composable
private fun FixDiff(label: String, before: String, after: String) {
    if (before == after) return
    Text(
        text = "$label: $before → $after",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
private fun EmptyNote(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatBytesHuman(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
    } else if (mb >= 1.0) {
        String.format(java.util.Locale.US, "%.1f MB", mb)
    } else {
        String.format(java.util.Locale.US, "%.0f KB", bytes.toDouble() / 1024.0)
    }
}
