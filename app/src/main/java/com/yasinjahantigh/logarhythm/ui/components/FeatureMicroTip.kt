package com.yasinjahantigh.logarhythm.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.data.datastore.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Stable IDs for one-time contextual micro-tips shown on first visit to a feature. */
object FeatureHintIds {
    const val PLAYER_COVER = "tip_player_cover"
    const val PLAYER_VINYL = "tip_player_vinyl"
    const val LYRICS_ACTIONS = "tip_lyrics_actions"
    const val LIBRARY_GESTURES = "tip_library_gestures"
    const val ARTIST_DETAIL = "tip_artist_detail"
    const val STATS_SORT_SHARE = "tip_stats_sort_share"
    const val TAG_FIXER_BG = "tip_tag_fixer_bg"
    const val HOME_RADAR = "tip_home_radar"
    const val EQUALIZER_AUTO = "tip_equalizer_auto"
    const val DOWNLOAD_PREVIEW = "tip_download_preview"
}

@HiltViewModel
class FeatureHintsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    val seenHints: StateFlow<Set<String>> = preferencesManager.seenFeatureHints
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = preferencesManager.seenFeatureHintsCached()
        )

    fun dismiss(hintId: String) {
        viewModelScope.launch {
            preferencesManager.markFeatureHintSeen(hintId)
        }
    }
}

/**
 * Ultra-compact, non-blocking first-time feature hint pill.
 * Never uses a modal dialog or scrim — users can tap anything else on screen immediately,
 * or tap the pill itself / × icon to dismiss it permanently.
 */
@Composable
fun FeatureMicroTip(
    hintId: String,
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector = Icons.Filled.AutoAwesome,
    viewModel: FeatureHintsViewModel = hiltViewModel()
) {
    val seenHints by viewModel.seenHints.collectAsStateWithLifecycle()
    val alreadySeen = hintId in seenHints
    var locallyDismissed by remember(hintId) { mutableStateOf(false) }
    var settled by remember(hintId) { mutableStateOf(false) }

    LaunchedEffect(hintId, enabled, alreadySeen) {
        if (enabled && !alreadySeen && !locallyDismissed) {
            delay(320L)
            settled = true
        }
    }

    val visible = enabled && settled && !alreadySeen && !locallyDismissed

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)) +
            expandVertically(tween(220)) +
            scaleIn(initialScale = 0.96f, animationSpec = tween(220)),
        exit = fadeOut(tween(160)) + shrinkVertically(tween(180)),
        modifier = modifier
    ) {
        val scheme = MaterialTheme.colorScheme
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = scheme.surfaceContainerHigh.copy(alpha = 0.94f),
            tonalElevation = 2.dp,
            border = BorderStroke(0.8.dp, scheme.primary.copy(alpha = 0.28f)),
            modifier = Modifier
                .widthIn(max = 520.dp)
                .clickable {
                    locallyDismissed = true
                    viewModel.dismiss(hintId)
                }
                .testTag("micro_tip_$hintId")
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp
                    ),
                    color = scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.btn_close),
                    tint = scheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
