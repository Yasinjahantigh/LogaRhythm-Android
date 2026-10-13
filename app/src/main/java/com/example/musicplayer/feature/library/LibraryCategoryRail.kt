package com.example.musicplayer.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.musicplayer.ui.components.formatCount

/**
 * The library's category switcher.
 *
 * Instead of anonymous chips, every category is a small card that shows its icon and how much it
 * holds ("Albums · 98"), so the rail also works as an at-a-glance overview. The selected card
 * fills with the accent colour; the rest stay quiet.
 */
@Composable
fun LibraryCategoryRail(
    selected: LibraryTab,
    counts: Map<LibraryTab, Int>,
    onSelect: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.testTag("library_tabs"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items = LibraryTab.entries, key = { it.name }) { tab ->
            CategoryCard(
                tab = tab,
                count = counts[tab],
                isSelected = tab == selected,
                onClick = { onSelect(tab) }
            )
        }
    }
}

@Composable
private fun CategoryCard(
    tab: LibraryTab,
    count: Int?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (isSelected) scheme.primary else scheme.surfaceContainerHigh,
        label = "category_container"
    )
    val content by animateColorAsState(
        targetValue = if (isSelected) scheme.onPrimary else scheme.onSurface,
        label = "category_content"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = container,
        contentColor = content,
        modifier = Modifier.testTag("library_tab_${tab.name}")
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = stringResource(tab.titleRes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1
                )
                if (count != null) {
                    Text(
                        text = formatCount(count),
                        style = MaterialTheme.typography.labelSmall,
                        color = content.copy(alpha = 0.72f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
