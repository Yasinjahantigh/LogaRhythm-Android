package com.yasinjahantigh.logarhythm.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnLock
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.core.utils.TimeUtils
import com.yasinjahantigh.logarhythm.data.room.ArtistReleaseEntity
import com.yasinjahantigh.logarhythm.data.room.FollowedArtistEntity
import com.yasinjahantigh.logarhythm.ui.components.ArtworkImage
import com.yasinjahantigh.logarhythm.ui.designsystem.bounceOnPress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseRadarHomeSection(
    followedArtists: List<FollowedArtistEntity>,
    releases: List<ArtistReleaseEntity>,
    unseenCount: Int,
    refreshing: Boolean,
    radarEnabled: Boolean,
    onRefresh: () -> Unit,
    onDownloadRelease: (ArtistReleaseEntity) -> Unit,
    onDismissRelease: (ArtistReleaseEntity) -> Unit,
    onMarkSeen: () -> Unit,
    onUnfollowArtist: (String) -> Unit,
    onSetRadarEnabled: (Boolean) -> Unit
) {
    if (followedArtists.isEmpty()) return
    var showManageSheet by remember { mutableStateOf(false) }
    var selectedArtistKey by remember { mutableStateOf<String?>(null) }
    var downloadingIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val filteredReleases = remember(releases, selectedArtistKey) {
        val base = if (selectedArtistKey == null) {
            releases
        } else {
            releases.filter { it.artistKey == selectedArtistKey }
        }
        base.sortedWith(
            compareBy<ArtistReleaseEntity> { it.isSeen }
                .thenByDescending { it.discoveredAt }
        )
    }

    val spinTransition = rememberInfiniteTransition(label = "radar_refresh_spin")
    val spinAngle by spinTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_spin_angle"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
            .animateContentSize()
            .testTag("release_radar_section")
    ) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Podcasts,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.radar_section_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (unseenCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onMarkSeen() }
                        ) {
                            Text(
                                text = stringResource(R.string.radar_new_count_badge, unseenCount),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.radar_tracking_summary, followedArtists.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (unseenCount > 0) {
                IconButton(
                    onClick = onMarkSeen,
                    modifier = Modifier.testTag("radar_mark_seen_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DoneAll,
                        contentDescription = stringResource(R.string.radar_mark_all_seen),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            IconButton(
                onClick = onRefresh,
                enabled = !refreshing,
                modifier = Modifier.testTag("radar_refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.radar_refresh_action),
                    modifier = Modifier.graphicsLayer {
                        rotationZ = if (refreshing) spinAngle else 0f
                    }
                )
            }
            IconButton(
                onClick = { showManageSheet = true },
                modifier = Modifier.testTag("radar_manage_button")
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = stringResource(R.string.radar_manage_followed)
                )
            }
        }

        if (filteredReleases.isNotEmpty()) {
            com.yasinjahantigh.logarhythm.ui.components.FeatureMicroTip(
                hintId = com.yasinjahantigh.logarhythm.ui.components.FeatureHintIds.HOME_RADAR,
                text = stringResource(R.string.tip_home_radar),
                icon = Icons.Filled.Podcasts,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        // Artist Filter Chips
        if (followedArtists.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                item(key = "radar_filter_all") {
                    FilterChip(
                        selected = selectedArtistKey == null,
                        onClick = { selectedArtistKey = null },
                        label = { Text(stringResource(R.string.radar_filter_all)) },
                        shape = CircleShape
                    )
                }
                items(followedArtists, key = { "chip_${it.artistKey}" }) { artist ->
                    FilterChip(
                        selected = selectedArtistKey == artist.artistKey,
                        onClick = {
                            selectedArtistKey = if (selectedArtistKey == artist.artistKey) null else artist.artistKey
                        },
                        leadingIcon = {
                            ArtworkImage(
                                uri = artist.artworkUrl,
                                fallbackKey = artist.displayName,
                                modifier = Modifier.size(20.dp),
                                shape = CircleShape,
                                contentDescription = null
                            )
                        },
                        label = {
                            Text(
                                text = artist.displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        shape = CircleShape
                    )
                }
            }
        }

        // Release Cards or VPN-aware Empty Card
        if (filteredReleases.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.VpnLock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.radar_empty_card_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = stringResource(R.string.radar_empty_vpn_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(
                            onClick = onRefresh,
                            enabled = !refreshing,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (refreshing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.radar_check_now))
                        }
                        TextButton(onClick = { showManageSheet = true }) {
                            Text(stringResource(R.string.radar_manage_followed))
                        }
                    }
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredReleases, key = { it.releaseId }) { release ->
                    val isBusy = release.releaseId in downloadingIds
                    RadarReleaseCard(
                        release = release,
                        isDownloading = isBusy,
                        onDownload = {
                            downloadingIds = downloadingIds + release.releaseId
                            onDownloadRelease(release)
                        },
                        onDismiss = { onDismissRelease(release) }
                    )
                }
            }
        }
    }

    if (showManageSheet) {
        ModalBottomSheet(
            onDismissRequest = { showManageSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = stringResource(R.string.radar_manage_followed),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.radar_background_alerts),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.radar_vpn_tip_short),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = radarEnabled,
                            onCheckedChange = onSetRadarEnabled
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(followedArtists, key = { it.artistKey }) { artist ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ArtworkImage(
                                    uri = artist.artworkUrl,
                                    fallbackKey = artist.displayName,
                                    modifier = Modifier.size(42.dp),
                                    shape = CircleShape,
                                    contentDescription = artist.displayName
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = artist.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    artist.latestTrackTitle?.takeIf { it.isNotBlank() }?.let { latest ->
                                        Text(
                                            text = latest,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(onClick = { onUnfollowArtist(artist.artistKey) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.radar_unfollow),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onRefresh()
                            showManageSheet = false
                        },
                        enabled = !refreshing,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.radar_check_now))
                    }
                    TextButton(onClick = { showManageSheet = false }) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}

@Composable
private fun RadarReleaseCard(
    release: ArtistReleaseEntity,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    val isNew = !release.isSeen
    val releaseDateLabel = remember(release.discoveredAt, Locale.getDefault()) {
        if (release.discoveredAt <= 0L) null
        else runCatching {
            DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
                .format(Date(release.discoveredAt))
        }.getOrNull()
    }
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (isNew) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f))
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        },
        modifier = Modifier
            .width(184.dp)
            .testTag("radar_release_card_${release.releaseId}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.12f)
            ) {
                ArtworkImage(
                    uri = release.artworkUrl,
                    fallbackKey = release.title,
                    icon = Icons.Filled.MusicNote,
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                    contentDescription = release.title
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Black.copy(alpha = 0.32f),
                                0.55f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = 0.58f)
                            )
                        )
                )
                Surface(
                    shape = CircleShape,
                    color = if (isNew) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Black.copy(alpha = 0.55f)
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                ) {
                    Text(
                        text = stringResource(
                            if (isNew) R.string.radar_badge_new else R.string.radar_badge_latest
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isNew) MaterialTheme.colorScheme.onPrimary else Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                // Dismiss button on top-end so users can clear unwanted releases
                Surface(
                    onClick = onDismiss,
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.56f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(30.dp)
                        .testTag("radar_dismiss_${release.releaseId}")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.radar_dismiss_release),
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                if (release.durationMs > 0L) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                    ) {
                        Text(
                            text = TimeUtils.formatDuration(release.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text = release.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = release.artistName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!releaseDateLabel.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarToday,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = releaseDateLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = onDownload,
                    enabled = !isDownloading,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                ) {
                    AnimatedVisibility(visible = isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    if (!isDownloading) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.radar_notif_download),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
