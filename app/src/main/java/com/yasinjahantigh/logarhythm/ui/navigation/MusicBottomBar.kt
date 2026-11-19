package com.yasinjahantigh.logarhythm.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yasinjahantigh.logarhythm.ui.designsystem.bounceOnPress

/**
 * Portrait navigation: ONE floating dock with lively expanding pill animation for the active tab.
 *
 * When something is playing, the mini player sits smoothly on top inside the same rounded surface,
 * separated by a subtle hairline divider from the animated navigation pills below.
 */
@Composable
fun MusicDock(
    currentDestination: String?,
    onNavigateToDestination: (NavDestination) -> Unit,
    modifier: Modifier = Modifier,
    miniPlayer: (@Composable () -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(30.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .shadow(
                elevation = 16.dp,
                shape = shape,
                ambientColor = scheme.primary.copy(alpha = 0.14f),
                spotColor = scheme.primary.copy(alpha = 0.22f)
            )
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            .testTag("bottom_navigation_bar"),
        shape = shape,
        color = scheme.surfaceContainerHigh,
        border = BorderStroke(0.8.dp, scheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column {
            if (miniPlayer != null) {
                miniPlayer()
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    thickness = 0.6.dp,
                    color = scheme.outlineVariant.copy(alpha = 0.45f)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 7.dp),
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    val container by animateColorAsState(
        targetValue = if (isSelected) scheme.primaryContainer else Color.Transparent,
        animationSpec = tween(durationMillis = 240),
        label = "dock_item_container"
    )
    val tint by animateColorAsState(
        targetValue = if (isSelected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 220),
        label = "dock_item_tint"
    )
    val iconScaleState = animateFloatAsState(
        targetValue = if (isSelected) 1.12f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "dock_icon_scale"
    )
    val horizontalPad by animateDpAsState(
        targetValue = if (isSelected) 18.dp else 14.dp,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "dock_pad"
    )
    val label = stringResource(id = destination.labelRes)
    val description = stringResource(id = destination.contentDescriptionRes)

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .bounceOnPress(interactionSource, pressedScale = 0.92f)
            .clip(CircleShape)
            .background(container)
            .selectable(
                selected = isSelected,
                onClick = {
                    if (!isSelected) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    onClick()
                },
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(bounded = true)
            )
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = 0.72f,
                    stiffness = Spring.StiffnessMedium
                )
            )
            .padding(horizontal = horizontalPad, vertical = 10.dp)
            .testTag("nav_item_${destination.route}"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                contentDescription = description,
                tint = tint,
                modifier = Modifier
                    .size(23.dp)
                    .graphicsLayer {
                        val s = iconScaleState.value
                        scaleX = s
                        scaleY = s
                    }
            )
            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn(tween(180)) + expandHorizontally(
                    animationSpec = spring(
                        dampingRatio = 0.74f,
                        stiffness = Spring.StiffnessMedium
                    ),
                    expandFrom = Alignment.Start
                ),
                exit = fadeOut(tween(130)) + shrinkHorizontally(
                    animationSpec = tween(160),
                    shrinkTowards = Alignment.Start
                )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = tint,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Landscape / tablet navigation: a rail with lively spring icon scale and matching dock colours. */
@Composable
fun MusicNavRail(
    currentDestination: String?,
    onNavigateToDestination: (NavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    NavigationRail(
        modifier = modifier
            .fillMaxHeight()
            .width(92.dp)
            .testTag("navigation_rail"),
        containerColor = scheme.surfaceContainerLow
    ) {
        Spacer(Modifier.weight(0.15f))
        NavDestination.bottomNavDestinations.forEach { destination ->
            val isSelected = currentDestination == destination.route
            val iconScale by animateFloatAsState(
                targetValue = if (isSelected) 1.12f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "rail_icon_scale"
            )
            NavigationRailItem(
                selected = isSelected,
                onClick = { onNavigateToDestination(destination) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = stringResource(id = destination.contentDescriptionRes),
                        modifier = Modifier.graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        }
                    )
                },
                label = {
                    Text(
                        text = stringResource(id = destination.labelRes),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = scheme.onPrimaryContainer,
                    selectedTextColor = scheme.onSurface,
                    indicatorColor = scheme.primaryContainer,
                    unselectedIconColor = scheme.onSurfaceVariant,
                    unselectedTextColor = scheme.onSurfaceVariant
                ),
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .testTag("nav_item_${destination.route}")
            )
        }
        Spacer(Modifier.weight(1f))
    }
}
