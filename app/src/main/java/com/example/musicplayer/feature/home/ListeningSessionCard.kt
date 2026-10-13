package com.example.musicplayer.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * Quick start: one big "Shuffle all" button and three time-boxed sessions (15/30/60 min),
 * replacing the old explanatory card with just the actions.
 */
@Composable
fun ListeningSessionCard(
    onStartSession: (Int) -> Unit,
    onShuffleAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("home_listening_session_card"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onShuffleAll,
            shape = CircleShape,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("home_shuffle_all_button")
        ) {
            Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(22.dp))
            Text(
                text = stringResource(R.string.home_shuffle_all_action),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(15, 30, 60).forEach { minutes ->
                FilledTonalButton(
                    onClick = { onStartSession(minutes) },
                    shape = CircleShape,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .testTag("home_session_${minutes}_button"),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.home_session_minutes, minutes),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
