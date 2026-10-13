package com.example.musicplayer.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background

/**
 * Portrait navigation: ONE floating dock instead of a separate mini player and tab bar.
 *
 * When something is playing, the mini player sits on top inside the same rounded surface and the
 * navigation pills sit below it. The selected destination expands into a labelled pill; the others
 * collapse to icons, which keeps three destinations light and leaves room for the music.
 */
@Composable
fun MusicDock(
    currentDestination: String?,
    onNavigateToDestination: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
    miniPlayer: (@Composable () -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(32.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .shadow(
                elevation = 14.dp,
                shape = shape,
                ambientColor = scheme.primary.copy(alpha = 0.18f),
                spotColor = scheme.primary.copy(alpha = 0.28f)
            )
            .testTag("bottom_navigation_bar"),
        shape = shape,
        color = scheme.surfaceContainerHigh,
        border = BorderStroke(0.8.dp, scheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column {
            if (miniPlayer != null) {
                miniPlayer()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavDestination.bottomNavDestinations.forEach { destination ->
                    DockItem(
                        destination = destination,
                        isSelected = currentDestination == destination.route,
                        onClick = { onNavigateToDestination(destination) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DockItem(
    destination: NavDestination,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val container by animateColorAsState(
        targetValue = if (isSelected) scheme.primaryContainer else scheme.surfaceContainerHigh,
        label = "dock_item_container"
    )
    val tint by animateColorAsState(
        targetValue = if (isSelected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        label = "dock_item_tint"
    )
    val label = stringResource(id = destination.labelRes)
    val description = stringResource(id = destination.contentDescriptionRes)

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .selectable(selected = isSelected, onClick = onClick, role = Role.Tab)
            .animateContentSize()
            .padding(horizontal = 18.dp, vertical = 11.dp)
            .testTag("nav_item_${destination.route}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
            contentDescription = description,
            tint = tint
        )
        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = tint,
                    maxLines = 1
                )
            }
        }
    }
}

/** Landscape / tablet navigation: a rail with the same destinations and colours as the dock. */
@Composable
fun MusicNavRail(
    currentDestination: String?,
    onNavigateToDestination: (NavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    NavigationRail(
        modifier = modifier.fillMaxHeight().width(88.dp).testTag("navigation_rail"),
        containerColor = scheme.surfaceContainerLow
    ) {
        NavDestination.bottomNavDestinations.forEach { destination ->
            val isSelected = currentDestination == destination.route
            NavigationRailItem(
                selected = isSelected,
                onClick = { onNavigateToDestination(destination) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = stringResource(id = destination.contentDescriptionRes)
                    )
                },
                label = {
                    Text(
                        text = stringResource(id = destination.labelRes),
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = scheme.onPrimaryContainer,
                    selectedTextColor = scheme.onSurface,
                    indicatorColor = scheme.primaryContainer,
                    unselectedIconColor = scheme.onSurfaceVariant,
                    unselectedTextColor = scheme.onSurfaceVariant
                ),
                modifier = Modifier.testTag("nav_item_${destination.route}")
            )
        }
    }
}
