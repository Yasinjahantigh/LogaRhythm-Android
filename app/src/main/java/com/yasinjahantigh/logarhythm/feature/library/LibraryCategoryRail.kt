package com.yasinjahantigh.logarhythm.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.yasinjahantigh.logarhythm.ui.components.formatCount
import com.yasinjahantigh.logarhythm.ui.designsystem.bounceOnPress

/**
 * The library's category switcher: compact single-line pills with inline counts so most tabs
 * remain visible at once and vertical list space stays generous.
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
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
    val interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() }

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = CircleShape,
        color = container,
        contentColor = content,
        modifier = Modifier
            .defaultMinSize(minHeight = 44.dp)
            .bounceOnPress(interactionSource, 0.94f)
            .testTag("library_tab_${tab.name}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(tab.titleRes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1
            )
            if (count != null && count > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formatCount(count),
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = if (isSelected) 0.85f else 0.68f),
                    maxLines = 1
                )
            }
        }
    }
}
