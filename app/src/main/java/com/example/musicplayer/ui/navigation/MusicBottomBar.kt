package com.example.musicplayer.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource

@Composable
fun MusicBottomBar(
    currentDestination: String?,
    onNavigateToDestination: (NavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val useRail = configuration.screenWidthDp >= 700 || configuration.screenHeightDp < configuration.screenWidthDp
    if (useRail) {
        NavigationRail(
            modifier = modifier.fillMaxHeight().width(80.dp).testTag("navigation_rail")
        ) {
            NavDestination.bottomNavDestinations.filterNotNull().forEach { destination ->
                val isSelected = currentDestination == destination.route
                val label = stringResource(id = destination.labelRes)
                val contentDescription = stringResource(id = destination.contentDescriptionRes)
                NavigationRailItem(
                    selected = isSelected,
                    onClick = { onNavigateToDestination(destination) },
                    icon = {
                        Icon(
                            imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = contentDescription
                        )
                    },
                    label = { Text(text = label) },
                    modifier = Modifier.testTag("nav_item_${destination.route}")
                )
            }
        }
    } else {
        NavigationBar(modifier = modifier.testTag("bottom_navigation_bar")) {
            NavDestination.bottomNavDestinations.filterNotNull().forEach { destination ->
                val isSelected = currentDestination == destination.route
                val label = stringResource(id = destination.labelRes)
                val contentDescription = stringResource(id = destination.contentDescriptionRes)
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onNavigateToDestination(destination) },
                    icon = {
                        Icon(
                            imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = contentDescription
                        )
                    },
                    label = { Text(text = label) },
                    modifier = Modifier.testTag("nav_item_${destination.route}")
                )
            }
        }
    }
}
