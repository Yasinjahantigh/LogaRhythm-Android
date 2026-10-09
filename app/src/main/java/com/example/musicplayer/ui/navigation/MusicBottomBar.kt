package com.example.musicplayer.ui.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
    NavigationBar(
        modifier = modifier.testTag("bottom_navigation_bar")
    ) {
        NavDestination.bottomNavDestinations.filterNotNull().forEach { destination ->
            val isSelected = currentDestination == destination.route
            val label = stringResource(id = destination.labelRes)
            val contentDescription = stringResource(id = destination.contentDescriptionRes)

            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigateToDestination(destination) },
                icon = {
                    Icon(
                        imageVector = if (isSelected) {
                            destination.selectedIcon
                        } else {
                            destination.unselectedIcon
                        },
                        contentDescription = contentDescription
                    )
                },
                label = {
                    Text(text = label)
                },
                modifier = Modifier.testTag("nav_item_${destination.route}")
            )
        }
    }
}
