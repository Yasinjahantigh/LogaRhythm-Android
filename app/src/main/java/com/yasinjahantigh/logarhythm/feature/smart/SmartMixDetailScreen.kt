package com.yasinjahantigh.logarhythm.feature.smart

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.core.utils.TimeUtils
import com.yasinjahantigh.logarhythm.domain.smart.SmartMixType
import com.yasinjahantigh.logarhythm.domain.smart.TasteInsights
import com.yasinjahantigh.logarhythm.feature.library.TrackListItem
import com.yasinjahantigh.logarhythm.feature.playlist.CreatePlaylistDialog
import com.yasinjahantigh.logarhythm.ui.components.DetailHero
import com.yasinjahantigh.logarhythm.ui.designsystem.LocalPlaybackBottomPadding
import kotlinx.coroutines.launch

@Composable
fun SmartMixDetailScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SmartMixDetailViewModel = hiltViewModel()
) {
    BackHandler(onBack = onNavigateBack)

    val mix by viewModel.mix.collectAsStateWithLifecycle()
    val tracks by viewModel.tracks.collectAsStateWithLifecycle()
    val tasteInsights by viewModel.tasteInsights.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showSaveDialog by remember { mutableStateOf(false) }

    val title = stringResource(viewModel.type.titleRes())
    val description = stringResource(viewModel.type.descriptionRes())

    if (showSaveDialog) {
        CreatePlaylistDialog(
            initialName = title,
            onDismiss = { showSaveDialog = false },
            onConfirm = { name ->
                showSaveDialog = false
                viewModel.saveAsPlaylist(name) {
                    scope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.smart_mix_saved, name))
                    }
                }
            }
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("smart_mix_screen"),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        val bottomPadding = LocalPlaybackBottomPadding.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
            ) {
                item {
                    DetailHero(
                        title = title,
                        subtitle = description,
                        meta = stringResource(R.string.playlist_track_count, tracks.size) +
                            " • " + TimeUtils.formatDuration(tracks.sumOf { it.durationMs }),
                        artworkUri = mix?.artworkUris?.firstOrNull(),
                        icon = viewModel.type.icon(),
                        onBack = onNavigateBack,
                        onPlay = viewModel::playAll,
                        onShuffle = viewModel::shuffleAll,
                        actionsEnabled = tracks.isNotEmpty(),
                        backTag = "smart_mix_back_button",
                        playTag = "smart_mix_play_button",
                        shuffleTag = "smart_mix_shuffle_button",
                        topEndAction = {
                            IconButton(
                                onClick = { showSaveDialog = true },
                                enabled = tracks.isNotEmpty(),
                                modifier = Modifier.testTag("smart_mix_save_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BookmarkAdd,
                                    contentDescription = stringResource(R.string.smart_mix_save_as_playlist)
                                )
                            }
                        }
                    )
                }

                if (viewModel.type.isAdaptiveTasteMix && tasteInsights != null) {
                    item(key = "taste_insights_card") {
                        LearnedTasteCard(
                            type = viewModel.type,
                            insights = tasteInsights!!,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                if (tracks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.smart_mix_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                        TrackListItem(
                            track = track,
                            onClick = { viewModel.playTrackAt(index) },
                            onPlayNext = { viewModel.playNext(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LearnedTasteCard(
    type: SmartMixType,
    insights: TasteInsights,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        modifier = modifier
            .fillMaxWidth()
            .testTag("smart_mix_taste_insights_card")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.smart_taste_card_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                if (type == SmartMixType.TIME_OF_DAY_VIBE) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = stringResource(insights.activeTimeSlot.labelRes()),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Text(
                text = if (insights.analyzedPlays < 5) {
                    stringResource(R.string.smart_taste_learning_warming, insights.analyzedPlays)
                } else {
                    stringResource(
                        R.string.smart_taste_learning_ready,
                        insights.analyzedPlays,
                        insights.confidencePercent
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
            )

            LinearProgressIndicator(
                progress = { (insights.confidencePercent / 100f).coerceIn(0.05f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )

            if (insights.topArtists.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.smart_taste_top_artists, insights.topArtists.joinToString(" · ")),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (insights.topGenres.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.smart_taste_top_genres, insights.topGenres.joinToString(" · ")),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
