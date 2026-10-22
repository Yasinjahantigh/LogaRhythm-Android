package com.yasinjahantigh.logarhythm.feature.theme

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasinjahantigh.logarhythm.R
import com.yasinjahantigh.logarhythm.ui.designsystem.LocalPlaybackBottomPadding

private data class ThemePreset(
    val key: String,
    val nameRes: Int,
    val descriptionRes: Int,
    val colors: List<Color>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ThemeViewModel = hiltViewModel()
) {
    val themeStyle by viewModel.themeStyle.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val customAccent by viewModel.customAccent.collectAsStateWithLifecycle()
    val customBase by viewModel.customBase.collectAsStateWithLifecycle()

    val presets = listOf(
        ThemePreset(
            key = "default",
            nameRes = R.string.theme_preset_default,
            descriptionRes = R.string.theme_preset_default_desc,
            colors = listOf(Color(0xFFF4B266), Color(0xFF0E1013))
        ),
        ThemePreset(
            key = "oled",
            nameRes = R.string.theme_preset_oled,
            descriptionRes = R.string.theme_preset_oled_desc,
            colors = listOf(Color.Black, Color(0xFFF4B266))
        ),
        ThemePreset(
            key = "album_art",
            nameRes = R.string.theme_preset_album_art,
            descriptionRes = R.string.theme_preset_album_art_desc,
            colors = listOf(Color(0xFFE86A92), Color(0xFF6B4EFF), Color(0xFF0E1013))
        ),
        ThemePreset(
            key = "wallpaper",
            nameRes = R.string.theme_preset_wallpaper,
            descriptionRes = R.string.theme_preset_wallpaper_desc,
            colors = listOf(Color(0xFF64B5F6), Color(0xFF9C6ADE), Color(0xFF12141A))
        ),
        ThemePreset(
            key = "custom",
            nameRes = R.string.theme_preset_custom,
            descriptionRes = R.string.theme_preset_custom_desc,
            colors = listOf(Color(customAccent), if (customBase == "light") Color(0xFFF5F1E8) else Color(0xFF0E1013))
        )
    )

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("theme_studio_screen"),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.theme_studio_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(bottom = LocalPlaybackBottomPadding.current + 24.dp)
        ) {
            item {
                ThemePreviewCard(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }

            item {
                SectionTitle(stringResource(R.string.theme_presets_title))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(presets, key = { it.key }) { preset ->
                        PresetCard(
                            preset = preset,
                            selected = themeStyle == preset.key,
                            onClick = { viewModel.setThemeStyle(preset.key) }
                        )
                    }
                }
            }

            if (themeStyle == "custom") {
                item {
                    SectionTitle(stringResource(R.string.theme_custom_title))
                    CustomThemeEditor(
                        accent = customAccent,
                        base = customBase,
                        onAccentChange = viewModel::setAccent,
                        onBaseChange = viewModel::setBase
                    )
                }
            }

            item {
                SectionTitle(stringResource(R.string.theme_mode_title))
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "system" to R.string.settings_theme_system,
                        "light" to R.string.settings_theme_light,
                        "dark" to R.string.settings_theme_dark
                    ).forEach { (mode, label) ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { viewModel.setThemeMode(mode) },
                            label = { Text(stringResource(label)) },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.theme_mode_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            item {
                Text(
                    text = stringResource(R.string.theme_album_wallpaper_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun ThemePreviewCard(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerLow,
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Brush.linearGradient(listOf(scheme.primary, scheme.tertiary)))
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.theme_preview_track),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.theme_preview_album),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(shape = CircleShape, color = scheme.primary) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = scheme.onPrimary,
                        modifier = Modifier.padding(9.dp).size(22.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { 0.62f },
                modifier = Modifier.fillMaxWidth(),
                color = scheme.primary,
                trackColor = scheme.surfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = CircleShape, color = scheme.primaryContainer) {
                    Text(
                        text = stringResource(R.string.theme_preview_chip_primary),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                Surface(shape = CircleShape, color = scheme.secondaryContainer) {
                    Text(
                        text = stringResource(R.string.theme_preview_chip_secondary),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetCard(
    preset: ThemePreset,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .width(156.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .testTag("theme_preset_${preset.key}"),
        shape = RoundedCornerShape(24.dp),
        color = if (selected) scheme.primaryContainer else scheme.surfaceContainerLow,
        border = BorderStroke(
            2.dp,
            if (selected) scheme.primary else scheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(preset.colors))
            ) {
                if (selected) {
                    Surface(
                        shape = CircleShape,
                        color = scheme.primary,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = scheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(preset.nameRes),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(preset.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CustomThemeEditor(
    accent: Long,
    base: String,
    onAccentChange: (Long) -> Unit,
    onBaseChange: (String) -> Unit
) {
    val hsv = remember(accent) {
        FloatArray(3).also { AndroidColor.colorToHSV(accent.toInt(), it) }
    }
    var hue by remember(accent) { mutableFloatStateOf(hsv[0].coerceIn(0f, 360f)) }
    var saturation by remember(accent) { mutableFloatStateOf(hsv[1].coerceIn(0.3f, 0.85f)) }

    fun saveAccent() {
        val argb = Color.hsv(hue, saturation, 0.85f).toArgb().toLong() and 0xFFFFFFFFL
        onAccentChange(argb)
    }

    Surface(
        modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.theme_custom_base),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "dark" to R.string.theme_base_dark,
                    "light" to R.string.theme_base_light,
                    "oled" to R.string.theme_base_oled
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = base == key,
                        onClick = { onBaseChange(key) },
                        label = { Text(stringResource(label)) }
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.hsv(hue, saturation, 0.85f))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.theme_custom_accent),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = stringResource(R.string.theme_hue),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 10.dp)
            )
            Slider(
                value = hue,
                onValueChange = { hue = it },
                onValueChangeFinished = ::saveAccent,
                valueRange = 0f..360f
            )
            Text(
                text = stringResource(R.string.theme_saturation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = saturation,
                onValueChange = { saturation = it },
                onValueChangeFinished = ::saveAccent,
                valueRange = 0.3f..0.85f
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(
                    0xFFF4B266, 0xFFEF6C57, 0xFFE85D8A, 0xFFB06BFF,
                    0xFF5B8CFF, 0xFF2CB5A8, 0xFF6FBF73, 0xFFD6C15A
                ).forEach { value ->
                    val swatch = Color(value)
                    val isSelected = (accent and 0xFFFFFFL) == (value and 0xFFFFFFL)
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .clickable {
                                val swatchHsv = FloatArray(3).also { AndroidColor.colorToHSV(value.toInt(), it) }
                                hue = swatchHsv[0]
                                saturation = swatchHsv[1].coerceIn(0.3f, 0.85f)
                                onAccentChange(value)
                            }
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.align(Alignment.Center).size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 10.dp)
    )
}
