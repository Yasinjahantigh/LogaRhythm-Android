package com.yasinjahantigh.logarhythm.feature.download

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.data.download.DownloadCatalogCache
import com.yasinjahantigh.logarhythm.domain.model.DownloadErrorKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadItem
import com.yasinjahantigh.logarhythm.domain.model.DownloadResultKind
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchCategory
import com.yasinjahantigh.logarhythm.domain.model.DownloadSearchResult
import com.yasinjahantigh.logarhythm.domain.model.DownloadSource
import com.yasinjahantigh.logarhythm.ui.components.ArtworkImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSearchTab(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    state: DownloadSearchUiState,
    sourceFilter: DownloadSource?,
    onSourceFilterChange: (DownloadSource?) -> Unit,
    categoryFilter: DownloadSearchCategory,
    onCategoryFilterChange: (DownloadSearchCategory) -> Unit,
    savedCollections: List<DownloadCatalogCache.SavedCollectionSummary>,
    onOpenSavedCollection: (String) -> Unit,
    listState: LazyListState,
    recentSearches: List<String>,
    onSelectRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    history: List<DownloadItem>,
    preparing: Boolean,
    bottomPadding: Dp,
    onResultClick: (DownloadSearchResult) -> Unit,
    onDownloadTrack: (DownloadSearchResult) -> Unit = onResultClick,
    onDownloadAlbum: (DownloadSearchResult) -> Unit = {},
    routingStatus: com.yasinjahantigh.logarhythm.data.download.SourceRoutingStatus =
        com.yasinjahantigh.logarhythm.data.download.SourceRoutingStatus(),
    previewState: com.yasinjahantigh.logarhythm.data.download.StreamPreviewState =
        com.yasinjahantigh.logarhythm.data.download.StreamPreviewState(),
    onTogglePreview: (DownloadSearchResult) -> Unit = {},
    freshResults: List<DownloadSearchResult> = emptyList(),
    popularResults: List<DownloadSearchResult> = emptyList(),
    showOwned: Boolean = false,
    onToggleShowOwned: () -> Unit = {},
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("download_search_field"),
            placeholder = {
                Text(text = stringResource(R.string.download_search_hint), maxLines = 1)
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.cd_clear_search)
                            )
                        }
                    }
                    IconButton(
                        onClick = { onSearch(); focusManager.clearFocus() },
                        modifier = Modifier.testTag("download_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = stringResource(R.string.download_tab_search),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            singleLine = true,
            shape = CircleShape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                unfocusedBorderColor = Color.Transparent
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = { onSearch(); focusManager.clearFocus() }
            )
        )

        CategoryFilterRow(
            selected = categoryFilter,
            onSelect = onCategoryFilterChange,
            showOwned = showOwned,
            onToggleShowOwned = onToggleShowOwned
        )

        SourceFilterRow(
            selected = sourceFilter,
            onSelect = onSourceFilterChange
        )

        var showVpnTip by remember { androidx.compose.runtime.mutableStateOf(true) }
        if (showVpnTip) {
            val fastestLabel = stringResource(downloadSourceLabelRes(routingStatus.fastestSource))
            val bannerText = when {
                routingStatus.isVpnActive && routingStatus.isBenchmarking ->
                    stringResource(R.string.download_routing_vpn_probing)
                routingStatus.isVpnActive ->
                    stringResource(R.string.download_routing_vpn_fastest, fastestLabel)
                else ->
                    stringResource(R.string.download_routing_no_vpn_musicman)
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (routingStatus.isVpnActive) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                } else {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.58f)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.VpnLock,
                        contentDescription = null,
                        tint = if (routingStatus.isVpnActive) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = bannerText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (routingStatus.isVpnActive) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { showVpnTip = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.btn_close),
                            tint = if (routingStatus.isVpnActive) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        if (preparing && sourceFilter != DownloadSource.MUSICMAN && sourceFilter != DownloadSource.SAAVN && sourceFilter != DownloadSource.RADIOJAVAN && sourceFilter != DownloadSource.DEEZER) {
            PreparingRow()
        }

        if (state.searching && state.results.isNotEmpty()) {
            androidx.compose.material3.LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
            )
        }

        when {
            state.searching && state.results.isEmpty() -> SearchSkeleton()
            state.error == DownloadErrorKind.NETWORK && state.results.isEmpty() -> NetworkErrorCard(onRetry = onRetry)
            state.error != null && state.results.isEmpty() -> ContentErrorCard(onRetry = onRetry)
            !state.hasSearched -> PreSearchDiscoveryContent(
                freshResults = freshResults,
                popularResults = popularResults,
                savedCollections = savedCollections,
                recentSearches = recentSearches,
                onResultClick = onResultClick,
                onOpenSavedCollection = onOpenSavedCollection,
                onSelectRecent = onSelectRecent,
                onRemoveRecent = onRemoveRecent,
                onClearRecent = onClearRecent,
                bottomPadding = bottomPadding
            )
            state.results.isEmpty() -> NoResults()
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = bottomPadding
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.results, key = { "${it.kind.name}_${it.source.raw}_${it.id}" }) { result ->
                    val pKey = "${result.kind.name}_${result.source.raw}_${result.id}"
                    DownloadResultCard(
                        result = result,
                        activeItem = history.firstOrNull { it.sourceUrl == result.url },
                        onClick = { onResultClick(result) },
                        onDownloadClick = if (result.kind == DownloadResultKind.TRACK) {
                            { onDownloadTrack(result) }
                        } else null,
                        onDownloadAlbum = if (result.kind == DownloadResultKind.TRACK) {
                            { onDownloadAlbum(result) }
                        } else null,
                        isPreviewLoading = previewState.trackKey == pKey && previewState.isLoading,
                        isPreviewPlaying = previewState.trackKey == pKey && previewState.isPlaying,
                        onTogglePreview = if (result.kind == DownloadResultKind.TRACK) {
                            { onTogglePreview(result) }
                        } else null,
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    selected: DownloadSearchCategory,
    onSelect: (DownloadSearchCategory) -> Unit,
    showOwned: Boolean,
    onToggleShowOwned: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(
            DownloadSearchCategory.ALL to R.string.download_category_all,
            DownloadSearchCategory.TRACK to R.string.download_category_track,
            DownloadSearchCategory.ALBUM to R.string.download_category_album,
            DownloadSearchCategory.ARTIST to R.string.download_category_artist
        ).forEach { (category, labelRes) ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = { Text(stringResource(labelRes)) }
            )
        }
        FilterChip(
            selected = showOwned,
            onClick = onToggleShowOwned,
            label = { Text(stringResource(R.string.download_filter_show_owned)) },
            modifier = Modifier.testTag("download_filter_show_owned")
        )
    }
}

@Composable
private fun DiscoveryFeedsShelf(
    freshResults: List<DownloadSearchResult>,
    popularResults: List<DownloadSearchResult>,
    onResultClick: (DownloadSearchResult) -> Unit
) {
    var selectedFeed by remember { mutableIntStateOf(if (freshResults.isNotEmpty()) 0 else 1) }
    val activeList = if (selectedFeed == 0 && freshResults.isNotEmpty()) freshResults else popularResults
    if (activeList.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (freshResults.isNotEmpty()) {
                FilterChip(
                    selected = selectedFeed == 0,
                    onClick = { selectedFeed = 0 },
                    label = { Text(stringResource(R.string.download_feed_fresh)) }
                )
            }
            if (popularResults.isNotEmpty()) {
                FilterChip(
                    selected = selectedFeed == 1,
                    onClick = { selectedFeed = 1 },
                    label = { Text(stringResource(R.string.download_feed_popular)) }
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(activeList, key = { "feed_${selectedFeed}_${it.id}" }) { item ->
                Surface(
                    modifier = Modifier
                        .width(126.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onResultClick(item) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        ArtworkImage(
                            uri = item.thumbnailUrl,
                            fallbackKey = item.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(92.dp),
                            shape = RoundedCornerShape(12.dp),
                            contentDescription = item.title
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.artist.isNotBlank()) {
                            Text(
                                text = item.artist,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedCollectionsRow(
    collections: List<DownloadCatalogCache.SavedCollectionSummary>,
    onOpen: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.download_saved_collections_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(collections, key = { it.cacheKey }) { item ->
                Surface(
                    modifier = Modifier
                        .width(120.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onOpen(item.cacheKey) },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        ArtworkImage(
                            uri = item.thumbnailUrl,
                            fallbackKey = item.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(92.dp),
                            shape = if (item.kind == DownloadResultKind.ARTIST) CircleShape else RoundedCornerShape(12.dp),
                            contentDescription = item.title
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceFilterRow(
    selected: DownloadSource?,
    onSelect: (DownloadSource?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.download_source_all)) },
            modifier = Modifier.testTag("download_source_all")
        )
        for (source in listOf(
            DownloadSource.RADIOJAVAN,
            DownloadSource.DEEZER,
            DownloadSource.MUSICMAN,
            DownloadSource.SAAVN,
            DownloadSource.YOUTUBE,
            DownloadSource.SOUNDCLOUD,
            DownloadSource.BANDCAMP
        )) {
            FilterChip(
                selected = selected == source,
                onClick = { onSelect(source) },
                label = { Text(stringResource(downloadSourceLabelRes(source))) },
                modifier = Modifier.testTag("download_source_${source.raw}")
            )
        }
    }
}

@Composable
private fun PreparingRow() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.download_preparing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SearchSkeleton() {
    val transition = rememberInfiniteTransition(label = "download_skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "download_skeleton_alpha"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = alpha))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.45f)
                            .height(11.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkErrorCard(onRetry: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.WifiOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.download_error_network_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.download_error_vpn_or_musicman),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.testTag("download_retry_button")
            ) {
                Text(text = stringResource(R.string.download_retry))
            }
        }
    }
}

@Composable
private fun ContentErrorCard(onRetry: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.download_error_content),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.download_error_vpn_or_musicman),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onRetry) {
                Text(text = stringResource(R.string.download_retry))
            }
        }
    }
}

@Composable
private fun NoResults() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.SearchOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.download_search_no_results),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.download_search_no_results_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.vpn_proactive_download_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PreSearchDiscoveryContent(
    freshResults: List<DownloadSearchResult>,
    popularResults: List<DownloadSearchResult>,
    savedCollections: List<DownloadCatalogCache.SavedCollectionSummary>,
    recentSearches: List<String>,
    onResultClick: (DownloadSearchResult) -> Unit,
    onOpenSavedCollection: (String) -> Unit,
    onSelectRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecent: () -> Unit,
    bottomPadding: Dp
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 4.dp,
            bottom = bottomPadding + 12.dp
        )
    ) {
        if (freshResults.isNotEmpty() || popularResults.isNotEmpty()) {
            item(key = "discovery_feeds") {
                DiscoveryFeedsShelf(
                    freshResults = freshResults,
                    popularResults = popularResults,
                    onResultClick = onResultClick
                )
            }
        }
        if (savedCollections.isNotEmpty()) {
            item(key = "saved_collections") {
                SavedCollectionsRow(
                    collections = savedCollections,
                    onOpen = onOpenSavedCollection
                )
            }
        }
        if (recentSearches.isNotEmpty()) {
            item(key = "recent_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.download_search_recent),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = onClearRecent) {
                        Text(text = stringResource(R.string.download_clear_recent))
                    }
                }
            }
            items(recentSearches, key = { it }) { term ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectRecent(term) }
                        .padding(start = 12.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = term,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onRemoveRecent(term) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        item(key = "intro") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 24.dp,
                        end = 24.dp,
                        top = if (recentSearches.isEmpty() && freshResults.isEmpty()) 48.dp else 24.dp,
                        bottom = 24.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.download_search_intro_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.download_search_intro_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
