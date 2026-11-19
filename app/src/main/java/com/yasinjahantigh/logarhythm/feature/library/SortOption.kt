package com.yasinjahantigh.logarhythm.feature.library

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yasinjahantigh.logarhythm.R

enum class SortField(@param:StringRes val titleRes: Int) {
    TITLE(R.string.sort_title),
    ARTIST(R.string.sort_artist),
    ALBUM(R.string.sort_album),
    YEAR(R.string.sort_year),
    DATE_ADDED(R.string.sort_date_added),
    DATE_MODIFIED(R.string.sort_date_modified),
    DURATION(R.string.sort_duration),
    PLAY_COUNT(R.string.sort_play_count),
    LAST_PLAYED(R.string.sort_last_played),
    TRACK_COUNT(R.string.sort_track_count),
    RANDOM(R.string.sort_random);

    fun appliesTo(tab: LibraryTab): Boolean = this in fieldsFor(tab)

    companion object {
        fun fieldsFor(tab: LibraryTab): List<SortField> = when (tab) {
            LibraryTab.SONGS -> listOf(
                TITLE, ARTIST, ALBUM, YEAR, DATE_ADDED, DATE_MODIFIED,
                DURATION, PLAY_COUNT, LAST_PLAYED, RANDOM
            )
            LibraryTab.ALBUMS -> listOf(TITLE, ARTIST, YEAR, TRACK_COUNT, RANDOM)
            LibraryTab.ARTISTS -> listOf(TITLE, TRACK_COUNT, RANDOM)
            LibraryTab.PLAYLISTS, LibraryTab.GENRES, LibraryTab.FOLDERS -> listOf(TITLE)
        }
    }
}

enum class SortDirection(@param:StringRes val titleRes: Int) {
    ASCENDING(R.string.sort_ascending),
    DESCENDING(R.string.sort_descending)
}

data class SortOption(
    val field: SortField = SortField.TITLE,
    val direction: SortDirection = SortDirection.ASCENDING
)

/** Filter chips on the Songs tab; every other tab shows the whole category. */
enum class TrackFilter(@param:StringRes val titleRes: Int) {
    ALL(R.string.filter_all),
    FAVORITES(R.string.filter_favorites),
    DOWNLOADED(R.string.filter_downloaded),
    RECENTLY_ADDED(R.string.filter_recently_added)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortBottomSheet(
    currentSort: SortOption,
    onSortSelected: (SortOption) -> Unit,
    onDismissRequest: () -> Unit,
    availableFields: List<SortField> = SortField.entries
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp, bottom = 16.dp)
                .testTag("sort_bottom_sheet")
        ) {
            Text(
                text = stringResource(R.string.sort_by_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Direction selector chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = currentSort.direction == SortDirection.ASCENDING,
                    onClick = {
                        onSortSelected(currentSort.copy(direction = SortDirection.ASCENDING))
                    },
                    label = { Text(stringResource(R.string.sort_ascending)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("sort_ascending_chip")
                )

                FilterChip(
                    selected = currentSort.direction == SortDirection.DESCENDING,
                    onClick = {
                        onSortSelected(currentSort.copy(direction = SortDirection.DESCENDING))
                    },
                    label = { Text(stringResource(R.string.sort_descending)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("sort_descending_chip")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))

            // Scrollable field list so every option (including Random at the bottom) is always visible
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                availableFields.forEach { field ->
                    val isSelected = currentSort.field == field
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onSortSelected(currentSort.copy(field = field))
                                onDismissRequest()
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .testTag("sort_field_${field.name.lowercase()}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                onSortSelected(currentSort.copy(field = field))
                                onDismissRequest()
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(field.titleRes),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f)
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
