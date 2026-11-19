package com.yasinjahantigh.logarhythm.feature.artist

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.data.repository.ArtistPhotoCandidate
import com.yasinjahantigh.logarhythm.feature.editor.CoverBitmap
import com.yasinjahantigh.logarhythm.feature.editor.CoverCropDialog
import kotlinx.coroutines.launch

/**
 * Bottom sheet for editing an artist's photo: pick from device gallery (with square crop),
 * search online portraits by name (helpful when auto-match picked the wrong artist), or reset.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditArtistPhotoSheet(
    artistName: String,
    hasCustomOrFetchedPhoto: Boolean,
    candidates: List<ArtistPhotoCandidate>,
    searching: Boolean,
    onSearch: (String) -> Unit,
    onSelectOnlineUrl: (String) -> Unit,
    onSelectGalleryBytes: (ByteArray) -> Unit,
    onResetPhoto: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember(artistName) { mutableStateOf(artistName) }
    var pickedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadingGallery by remember { mutableStateOf(false) }

    LaunchedEffect(artistName) {
        onSearch(artistName)
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        loadingGallery = true
        scope.launch {
            val decoded = CoverBitmap.decode(context, uri)
            loadingGallery = false
            if (decoded != null && !decoded.isRecycled) {
                pickedBitmap = decoded
            }
        }
    }

    val cropTarget = pickedBitmap?.takeUnless { it.isRecycled }
    if (cropTarget != null) {
        CoverCropDialog(
            bitmap = cropTarget,
            onConfirm = { bytes ->
                pickedBitmap = null
                onSelectGalleryBytes(bytes)
                onDismiss()
            },
            onDismiss = { pickedBitmap = null }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("artist_photo_edit_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.artist_photo_edit_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !loadingGallery,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("artist_photo_pick_gallery")
                ) {
                    if (loadingGallery) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.artist_photo_from_gallery),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (hasCustomOrFetchedPhoto) {
                    OutlinedButton(
                        onClick = {
                            onResetPhoto()
                            onDismiss()
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("artist_photo_reset")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Restore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.artist_photo_reset))
                    }
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text(stringResource(R.string.artist_photo_search_hint)) },
                trailingIcon = {
                    IconButton(
                        onClick = { onSearch(query) },
                        enabled = !searching && query.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = stringResource(R.string.artist_photo_search_hint)
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (query.isNotBlank()) onSearch(query)
                }),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("artist_photo_search_input")
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(276.dp),
                contentAlignment = Alignment.Center
            ) {
                when {
                    searching -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = stringResource(R.string.artist_photo_searching),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    candidates.isEmpty() -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.artist_photo_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { onSearch(query) }) {
                                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.download_retry))
                            }
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(candidates, key = { it.url }) { candidate ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable {
                                            onSelectOnlineUrl(candidate.url)
                                            onDismiss()
                                        }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        AsyncImage(
                                            model = candidate.url,
                                            contentDescription = candidate.label,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Surface(
                                            shape = CircleShape,
                                            color = Color.Black.copy(alpha = 0.58f),
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(6.dp)
                                        ) {
                                            Text(
                                                text = candidate.source,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White,
                                                maxLines = 1,
                                                modifier = Modifier
                                                    .background(Color.Transparent)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
