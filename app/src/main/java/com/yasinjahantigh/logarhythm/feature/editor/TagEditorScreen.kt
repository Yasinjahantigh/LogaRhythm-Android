package com.yasinjahantigh.logarhythm.feature.editor

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.domain.model.Track
import com.yasinjahantigh.logarhythm.domain.model.TrackEdit
import com.yasinjahantigh.logarhythm.domain.model.TrackEditFailure
import com.yasinjahantigh.logarhythm.core.utils.rememberMediaStoreConsent
import com.yasinjahantigh.logarhythm.ui.components.ArtworkImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagEditorScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TagEditorViewModel = hiltViewModel()
) {
    val track by viewModel.track.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mediaStoreConsent = rememberMediaStoreConsent()

    var initialized by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var albumArtist by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var trackNumber by remember { mutableStateOf("") }
    var discNumber by remember { mutableStateOf("") }
    var composer by remember { mutableStateOf("") }
    var pickedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pickedArtwork by remember { mutableStateOf<ByteArray?>(null) }
    var removeArtwork by remember { mutableStateOf(false) }
    var showCropDialog by remember { mutableStateOf(false) }
    var isLoadingCover by remember { mutableStateOf(false) }
    var coverError by remember { mutableStateOf(false) }

    LaunchedEffect(track) {
        val current = track
        if (current != null && !initialized) {
            title = current.title
            artist = current.artist
            album = current.album
            albumArtist = current.albumArtist
            genre = current.genre
            year = current.year.takeIf { it > 0 }?.toString().orEmpty()
            trackNumber = current.trackNumber.takeIf { it > 0 }?.toString().orEmpty()
            discNumber = current.discNumber.takeIf { it > 0 }?.toString().orEmpty()
            composer = current.composer
            initialized = true
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.saved.collect {
            Toast.makeText(context, context.getString(R.string.tag_editor_saved), Toast.LENGTH_SHORT).show()
            onNavigateBack()
        }
    }

    val coverPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isLoadingCover = true
        coverError = false
        scope.launch {
            val bitmap = CoverBitmap.decode(context, uri)
            isLoadingCover = false
            if (bitmap == null) {
                coverError = true
            } else {
                pickedBitmap = bitmap
                removeArtwork = false
                showCropDialog = true
            }
        }
    }

    if (showCropDialog && pickedBitmap != null) {
        CoverCropDialog(
            bitmap = pickedBitmap!!,
            onConfirm = { bytes ->
                pickedArtwork = bytes
                showCropDialog = false
            },
            onDismiss = { showCropDialog = false }
        )
    }

    val isLocal = track?.id?.startsWith("local:") == true
    val canSave = track != null && isLocal && !isSaving

    fun save() {
        val current = track ?: return
        val edit = TrackEdit(
            title = title,
            artist = artist,
            album = album,
            albumArtist = albumArtist,
            genre = genre,
            year = year.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            trackNumber = trackNumber.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            discNumber = discNumber.toIntOrNull()?.coerceAtLeast(0) ?: 0,
            composer = composer,
            artwork = pickedArtwork,
            artworkMime = if (pickedArtwork != null) "image/jpeg" else null,
            removeArtwork = removeArtwork
        )
        mediaStoreConsent.write(
            track = current,
            onApproved = { viewModel.save(edit) },
            onCancelled = viewModel::onConsentDenied
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("tag_editor_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tag_editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, enabled = !isSaving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = { save() },
                        enabled = canSave,
                        modifier = Modifier.testTag("tag_editor_save")
                    ) {
                        Text(
                            text = stringResource(if (isSaving) R.string.saving else R.string.btn_save),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        if (track == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.tag_editor_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(118.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    val picked = pickedBitmap
                    if (picked != null) {
                        androidx.compose.foundation.Image(
                            bitmap = picked.asImageBitmap(),
                            contentDescription = stringResource(R.string.tag_editor_cover),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (!removeArtwork) {
                        ArtworkImage(
                            uri = track?.artworkUri,
                            fallbackKey = track?.title.orEmpty(),
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(20.dp),
                            glyphSize = 36.sp,
                            contentDescription = stringResource(R.string.tag_editor_cover)
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.tag_editor_cover_removed),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    if (isLoadingCover) CircularProgressIndicator(modifier = Modifier.size(30.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        enabled = !isSaving,
                        onClick = {
                            coverPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("tag_editor_pick_cover")
                    ) {
                        Text(stringResource(R.string.tag_editor_change_cover))
                    }
                    if (pickedBitmap != null || (track?.artworkUri != null && !removeArtwork)) {
                        TextButton(
                            enabled = !isSaving,
                            onClick = {
                                pickedBitmap = null
                                pickedArtwork = null
                                removeArtwork = true
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.tag_editor_remove_cover),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (coverError) {
                        Text(
                            text = stringResource(R.string.tag_editor_cover_load_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            failure?.let { reason ->
                Text(
                    text = stringResource(failureText(reason)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                        .testTag("tag_editor_error")
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (!isLocal) {
                Text(
                    text = stringResource(R.string.tag_editor_not_local),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            EditorField(
                label = stringResource(R.string.tag_editor_field_title),
                value = title,
                onValueChange = { title = it },
                tag = "tag_editor_title"
            )
            EditorField(
                label = stringResource(R.string.tag_editor_field_artist),
                value = artist,
                onValueChange = { artist = it },
                tag = "tag_editor_artist"
            )
            EditorField(
                label = stringResource(R.string.tag_editor_field_album),
                value = album,
                onValueChange = { album = it },
                tag = "tag_editor_album"
            )
            EditorField(
                label = stringResource(R.string.tag_editor_field_album_artist),
                value = albumArtist,
                onValueChange = { albumArtist = it },
                tag = "tag_editor_album_artist"
            )
            EditorField(
                label = stringResource(R.string.tag_editor_field_genre),
                value = genre,
                onValueChange = { genre = it },
                tag = "tag_editor_genre"
            )
            EditorField(
                label = stringResource(R.string.tag_editor_field_year),
                value = year,
                onValueChange = { value ->
                    if (value.length <= 4 && value.all(Char::isDigit)) year = value
                },
                tag = "tag_editor_year",
                keyboardType = KeyboardType.Number
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    EditorField(
                        label = stringResource(R.string.tag_editor_field_track),
                        value = trackNumber,
                        onValueChange = { value ->
                            if (value.length <= 4 && value.all(Char::isDigit)) trackNumber = value
                        },
                        tag = "tag_editor_track",
                        keyboardType = KeyboardType.Number
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    EditorField(
                        label = stringResource(R.string.tag_editor_field_disc),
                        value = discNumber,
                        onValueChange = { value ->
                            if (value.length <= 3 && value.all(Char::isDigit)) discNumber = value
                        },
                        tag = "tag_editor_disc",
                        keyboardType = KeyboardType.Number
                    )
                }
            }

            EditorField(
                label = stringResource(R.string.tag_editor_field_composer),
                value = composer,
                onValueChange = { composer = it },
                tag = "tag_editor_composer"
            )

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { save() },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("tag_editor_save_bottom")
            ) {
                Text(stringResource(if (isSaving) R.string.saving else R.string.btn_save))
            }
            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
private fun EditorField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    tag: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .testTag(tag)
    )
}

private fun failureText(failure: TrackEditFailure): Int = when (failure) {
    TrackEditFailure.NOT_LOCAL -> R.string.tag_editor_not_local
    TrackEditFailure.UNSUPPORTED -> R.string.tag_editor_error_unsupported
    TrackEditFailure.DENIED -> R.string.tag_editor_error_denied
    TrackEditFailure.NO_SPACE -> R.string.tag_editor_error_no_space
    TrackEditFailure.FAILED -> R.string.tag_editor_error_failed
}
