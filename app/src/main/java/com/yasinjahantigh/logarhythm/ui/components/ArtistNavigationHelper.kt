package com.yasinjahantigh.logarhythm.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.domain.library.ArtistSplitConfig

/**
 * Renders a track's artist line with each split artist individually clickable.
 * When a track has multiple artists (e.g. "Artist A, Artist B" or "Artist A feat. Artist B"),
 * tapping any name opens that specific artist's page instead of searching the combined string.
 */
@Composable
fun SplitArtistText(
    rawArtist: String?,
    displayArtist: String,
    artistSplitConfig: ArtistSplitConfig,
    onArtistClick: ((String) -> Unit)?,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 1
) {
    val raw = rawArtist.orEmpty().trim()
    val names = remember(raw, artistSplitConfig) {
        if (raw.isBlank()) emptyList() else artistSplitConfig.namesOf(raw)
    }

    if (onArtistClick == null || names.isEmpty()) {
        Text(
            text = displayArtist,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
        return
    }

    if (names.size == 1) {
        Text(
            text = displayArtist,
            style = style,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable { onArtistClick(names.first()) }
        )
        return
    }

    val annotated = remember(displayArtist, names, color, style.fontWeight) {
        buildAnnotatedString {
            var cursor = 0
            var matchedAll = true
            val spans = mutableListOf<Triple<Int, Int, String>>()
            for (name in names) {
                val idx = displayArtist.indexOf(name, startIndex = cursor, ignoreCase = true)
                if (idx < 0) {
                    matchedAll = false
                    break
                }
                spans += Triple(idx, idx + name.length, name)
                cursor = idx + name.length
            }

            if (matchedAll) {
                append(displayArtist)
                spans.forEachIndexed { index, (start, end, targetName) ->
                    addLink(
                        clickable = LinkAnnotation.Clickable(
                            tag = "artist_$index",
                            styles = TextLinkStyles(
                                style = SpanStyle(color = color, fontWeight = style.fontWeight)
                            ),
                            linkInteractionListener = { onArtistClick(targetName) }
                        ),
                        start = start,
                        end = end
                    )
                }
            } else {
                names.forEachIndexed { index, targetName ->
                    if (index > 0) append(", ")
                    val start = length
                    append(targetName)
                    val end = length
                    addLink(
                        clickable = LinkAnnotation.Clickable(
                            tag = "artist_$index",
                            styles = TextLinkStyles(
                                style = SpanStyle(color = color, fontWeight = style.fontWeight)
                            ),
                            linkInteractionListener = { onArtistClick(targetName) }
                        ),
                        start = start,
                        end = end
                    )
                }
            }
        }
    }

    Text(
        text = annotated,
        style = style.copy(color = color),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/**
 * Wraps an artist navigation callback so tapping a combined artist string opens a quick
 * artist picker dialog when more than one artist is present, or navigates directly for one.
 */
@Composable
fun rememberArtistNavigationHandler(
    artistSplitConfig: ArtistSplitConfig,
    onNavigateToArtist: ((String) -> Unit)?
): ((String) -> Unit)? {
    if (onNavigateToArtist == null) return null

    var pickerChoices by remember { mutableStateOf<List<String>?>(null) }

    pickerChoices?.let { choices ->
        AlertDialog(
            onDismissRequest = { pickerChoices = null },
            title = { Text(text = stringResource(R.string.action_go_to_artist)) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    choices.forEach { name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    pickerChoices = null
                                    onNavigateToArtist(name)
                                }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pickerChoices = null }) {
                    Text(text = stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    return remember(artistSplitConfig, onNavigateToArtist) {
        { rawArtist ->
            val names = artistSplitConfig.namesOf(rawArtist)
            when {
                names.isEmpty() -> onNavigateToArtist(rawArtist)
                names.size == 1 -> onNavigateToArtist(names.first())
                else -> pickerChoices = names
            }
        }
    }
}
