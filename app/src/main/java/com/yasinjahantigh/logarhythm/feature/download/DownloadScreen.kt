package com.yasinjahantigh.logarhythm.feature.download

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.domain.model.DownloadItem
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import com.yasinjahantigh.logarhythm.ui.designsystem.LocalPlaybackBottomPadding
import kotlinx.coroutines.launch

@Composable
fun DownloadScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToTagEditor: (String) -> Unit = {},
    viewModel: DownloadViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val recentSearches by viewModel.recentSearches.collectAsStateWithLifecycle()
    val sourceFilter by viewModel.sourceFilter.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val savedCollections by viewModel.savedCollections.collectAsStateWithLifecycle()
    val collectionDetail by viewModel.collectionDetail.collectAsStateWithLifecycle()
    val albumSummary by viewModel.albumSummary.collectAsStateWithLifecycle()
    val albumPreview by viewModel.albumPreview.collectAsStateWithLifecycle()
    val albumBusy by viewModel.albumBusy.collectAsStateWithLifecycle()
    val preparing by viewModel.preparing.collectAsStateWithLifecycle()
    val updateState by viewModel.updateState.collectAsStateWithLifecycle()
    val version by viewModel.ytdlpVersion.collectAsStateWithLifecycle()
    val cookiesImported by viewModel.cookiesImported.collectAsStateWithLifecycle()
    val previewState by viewModel.previewState.collectAsStateWithLifecycle()
    val freshResults by viewModel.freshResults.collectAsStateWithLifecycle()
    val popularResults by viewModel.popularResults.collectAsStateWithLifecycle()
    val qualityPref by viewModel.downloadQualityPref.collectAsStateWithLifecycle()
    val pendingShareText by viewModel.pendingShareText.collectAsStateWithLifecycle()
    val routingStatus by viewModel.routingStatus.collectAsStateWithLifecycle()
    var inspectedTrack by remember { mutableStateOf<DownloadSearchResult?>(null) }

    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    albumPreview?.let { preview ->
        AlbumDownloadDialog(
            preview = preview,
            busy = albumBusy,
            onDismiss = viewModel::dismissAlbumPreview,
            onConfirm = viewModel::confirmAlbumDownload
        )
    }
    if (albumBusy && albumPreview == null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.download_album)) },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.album_resolving))
                }
            },
            confirmButton = {}
        )
    }

    LaunchedEffect(albumSummary) {
        val summary = albumSummary ?: return@LaunchedEffect
        Toast.makeText(
            context,
            when {
                !summary.found -> context.getString(R.string.download_album_not_found)
                summary.queued == 0 -> context.getString(R.string.download_album_none)
                summary.failed > 0 -> context.getString(R.string.download_album_partial, summary.album, summary.queued, summary.failed)
                else -> context.getString(R.string.download_album_queued, summary.album, summary.queued)
            },
            Toast.LENGTH_LONG
        ).show()
        viewModel.clearAlbumSummary()
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(pendingShareText) {
        if (pendingShareText != null) {
            selectedTab = 0
            viewModel.consumeSharedLink {
                selectedTab = 1
            }
        }
    }
    var pendingStart by remember { mutableStateOf<(() -> Unit)?>(null) }
    val searchListState = androidx.compose.foundation.lazy.rememberLazyListState()

    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingStart
        pendingStart = null
        if (granted) {
            action?.invoke()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.download_error_storage))
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    val cookiePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importCookies(uri)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.cookieImport.collect { success ->
            snackbarHostState.showSnackbar(
                context.getString(
                    if (success) R.string.download_settings_cookies_import_success
                    else R.string.download_settings_cookies_import_invalid
                )
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.savedEvents.collect { event ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            snackbarHostState.showSnackbar(
                context.getString(
                    if (event.usedFallback) R.string.download_fallback_notice
                    else R.string.download_saved_message,
                    event.title
                )
            )
        }
    }

    fun startDownload(result: DownloadSearchResult) {
        val action: () -> Unit = {
            viewModel.enqueue(result)
            selectedTab = 1
        }
        val needsLegacyWrite = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        if (needsLegacyWrite) {
            pendingStart = action
            runCatching {
                writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }.onFailure {
                pendingStart = null
                action()
            }
        } else {
            action()
        }
    }

    val bottomPadding = LocalPlaybackBottomPadding.current + 24.dp

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("download_screen"),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = LocalPlaybackBottomPadding.current)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
            ) {
                DownloadHeader(
                    onBack = onNavigateBack,
                    onOpenSettings = { showSettings = true }
                )
                com.yasinjahantigh.logarhythm.ui.components.FeatureMicroTip(
                    hintId = com.yasinjahantigh.logarhythm.ui.components.FeatureHintIds.DOWNLOAD_PREVIEW,
                    text = stringResource(R.string.tip_download_preview),
                    icon = Icons.Filled.Download,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text(stringResource(R.string.download_tab_search)) },
                        modifier = Modifier.testTag("download_tab_search")
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text(stringResource(R.string.download_tab_downloads)) },
                        modifier = Modifier.testTag("download_tab_downloads")
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = selectedTab,
                        label = "download_tab_switch"
                    ) { tab ->
                        if (tab == 0) {
                            DownloadSearchTab(
                                query = query,
                                onQueryChange = viewModel::onQueryChange,
                                onSearch = { viewModel.search() },
                                state = searchState,
                                sourceFilter = sourceFilter,
                                onSourceFilterChange = viewModel::setSourceFilter,
                                categoryFilter = categoryFilter,
                                onCategoryFilterChange = viewModel::setCategoryFilter,
                                savedCollections = savedCollections,
                                onOpenSavedCollection = viewModel::openSavedCollection,
                                listState = searchListState,
                                recentSearches = recentSearches,
                                onSelectRecent = viewModel::selectRecent,
                                onRemoveRecent = viewModel::removeRecent,
                                onClearRecent = viewModel::clearRecent,
                                history = history,
                                preparing = preparing,
                                bottomPadding = bottomPadding,
                                onResultClick = { item ->
                                    if (item.kind == com.yasinjahantigh.logarhythm.domain.model.DownloadResultKind.TRACK) {
                                        inspectedTrack = item
                                    } else {
                                        viewModel.openCollection(item)
                                    }
                                },
                                onDownloadTrack = { item -> startDownload(item) },
                                onDownloadAlbum = viewModel::previewAlbum,
                                routingStatus = routingStatus,
                                previewState = previewState,
                                onTogglePreview = viewModel::togglePreview,
                                freshResults = freshResults,
                                popularResults = popularResults,
                                onRetry = { viewModel.search() }
                            )
                        } else {
                            DownloadHistoryTab(
                                history = history,
                                bottomPadding = bottomPadding,
                                onPlay = viewModel::play,
                                onOpen = { openWith(context, it) },
                                onShare = { share(context, it) },
                                onCancel = viewModel::cancel,
                                onRetry = viewModel::retry,
                                onRetryAllFailed = viewModel::retryAllFailed,
                                onRemove = viewModel::remove,
                                onClearFinished = viewModel::clearFinished,
                                onEditTags = { item ->
                                    item.mediaStoreId?.let { id -> onNavigateToTagEditor("local:$id") }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    collectionDetail?.let { detail ->
        val followedKeys by viewModel.followedArtistKeys.collectAsStateWithLifecycle()
        val isFollowed = viewModel.isArtistFollowed(detail.header.title, followedKeys)
        DownloadCollectionSheet(
            state = detail,
            history = history,
            isArtistFollowed = isFollowed,
            onToggleFollowArtist = { viewModel.toggleFollowCollectionArtist(detail.header) },
            previewState = previewState,
            onTogglePreview = viewModel::togglePreview,
            onDismiss = viewModel::closeCollection,
            onRefresh = { viewModel.openCollection(detail.header, forceRefresh = true) },
            onDownloadAll = { tracks ->
                viewModel.downloadAllFromCollection(detail.header.title, tracks)
                viewModel.closeCollection()
            },
            onTrackClick = { track -> inspectedTrack = track },
            onTrackDownload = { track -> startDownload(track) },
            onAlbumClick = { albumItem -> viewModel.openCollection(albumItem) }
        )
    }

    inspectedTrack?.let { track ->
        val activeItem = history.firstOrNull {
            it.sourceUrl == track.url || (it.videoId == track.id && it.source == track.source)
        }
        val pKey = viewModel.isPreviewKey(track)
        OnlineTrackDetailSheet(
            result = track,
            activeItem = activeItem,
            isPreviewLoading = previewState.trackKey == pKey && previewState.isLoading,
            isPreviewPlaying = previewState.trackKey == pKey && previewState.isPlaying,
            onTogglePreview = { viewModel.togglePreview(track) },
            onDownload = { startDownload(track) },
            onDownloadAlbum = { viewModel.previewAlbum(track) },
            onDismiss = { inspectedTrack = null }
        )
    }

    if (showSettings) {
        DownloadSettingsSheet(
            version = version,
            updateState = updateState,
            onUpdate = viewModel::updateYtdlp,
            cookiesImported = cookiesImported,
            onImportCookies = { cookiePicker.launch(arrayOf("*/*")) },
            onClearCookies = viewModel::clearCookies,
            qualityPref = qualityPref,
            onQualityChange = viewModel::setDownloadQuality,
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
private fun DownloadHeader(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("download_back_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back)
            )
        }
        Text(
            text = stringResource(R.string.download_screen_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.testTag("download_settings_button")
        ) {
            Icon(
                imageVector = Icons.Filled.Tune,
                contentDescription = stringResource(R.string.cd_download_settings)
            )
        }
    }
}

private fun openWith(context: Context, item: DownloadItem) {
    val uri = item.contentUri?.let(Uri::parse) ?: return
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "audio/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, null))
    }.onFailure {
        Toast.makeText(context, R.string.download_error_content, Toast.LENGTH_SHORT).show()
    }
}

private fun share(context: Context, item: DownloadItem) {
    val uri = item.contentUri?.let(Uri::parse) ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "audio/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, null))
    }.onFailure {
        Toast.makeText(context, R.string.cannot_share_file, Toast.LENGTH_SHORT).show()
    }
}
