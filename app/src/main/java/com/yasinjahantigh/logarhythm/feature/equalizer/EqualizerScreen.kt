package com.yasinjahantigh.logarhythm.feature.equalizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yasinjahantigh.logarhythm.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EqualizerViewModel = hiltViewModel()
) {
    val state by viewModel.equalizerState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("equalizer_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.equalizer_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("equalizer_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                },
                actions = {
                    if (state.isSupported && state.isEnabled) {
                        IconButton(
                            onClick = viewModel::resetEqualizer,
                            modifier = Modifier.testTag("equalizer_reset_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.equalizer_reset)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            if (!state.isSupported) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.equalizer_not_supported),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Enable switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.equalizer_enable),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Switch(
                    checked = state.isEnabled,
                    onCheckedChange = viewModel::setEqualizerEnabled,
                    enabled = state.isSupported,
                    modifier = Modifier.testTag("equalizer_switch")
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Visual Frequency Curve
            if (state.bands.isNotEmpty()) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val outlineColor = MaterialTheme.colorScheme.outlineVariant

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val midY = h / 2f

                        // Center guideline
                        drawLine(
                            color = outlineColor,
                            start = Offset(0f, midY),
                            end = Offset(w, midY),
                            strokeWidth = 1.5f
                        )

                        val bands = state.bands
                        val pointList = bands.mapIndexed { idx, band ->
                            val x = if (bands.size > 1) {
                                (idx.toFloat() / (bands.size - 1).toFloat()) * w
                            } else w / 2f

                            val range = (band.maxLevelMb - band.minLevelMb).toFloat().coerceAtLeast(1f)
                            val normalized = (band.currentLevelMb - band.minLevelMb).toFloat() / range
                            // Invert y: level high is near y=0
                            val y = h - (normalized * h).coerceIn(4f, h - 4f)
                            Offset(x, y)
                        }

                        if (pointList.size > 1) {
                            val curvePath = Path()
                            val fillPath = Path()

                            curvePath.moveTo(pointList[0].x, pointList[0].y)
                            fillPath.moveTo(pointList[0].x, h)
                            fillPath.lineTo(pointList[0].x, pointList[0].y)

                            for (i in 0 until pointList.size - 1) {
                                val p0 = pointList[i]
                                val p1 = pointList[i + 1]
                                val controlX = (p0.x + p1.x) / 2f
                                curvePath.cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
                                fillPath.cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
                            }

                            fillPath.lineTo(pointList.last().x, h)
                            fillPath.close()

                            // Draw gradient fill
                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        primaryColor.copy(alpha = 0.35f),
                                        primaryColor.copy(alpha = 0.05f)
                                    )
                                )
                            )

                            // Draw stroke
                            drawPath(
                                path = curvePath,
                                color = primaryColor,
                                style = Stroke(width = 3.5f, cap = StrokeCap.Round)
                            )

                            // Draw nodes
                            pointList.forEach { pt ->
                                drawCircle(
                                    color = primaryColor,
                                    radius = 5.5f,
                                    center = pt
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Presets selector
            Text(
                text = stringResource(R.string.equalizer_presets),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = state.currentPreset.toInt() == -1,
                    onClick = { viewModel.setPreset(-1) },
                    enabled = state.isSupported && state.isEnabled,
                    label = { Text(stringResource(R.string.equalizer_custom)) },
                    modifier = Modifier.testTag("equalizer_preset_chip_custom")
                )

                state.presets.forEachIndexed { index, presetName ->
                    FilterChip(
                        selected = state.currentPreset.toInt() == index,
                        onClick = { viewModel.setPreset(index.toShort()) },
                        enabled = state.isSupported && state.isEnabled,
                        label = { Text(presetName) },
                        modifier = Modifier.testTag("equalizer_preset_chip_$index")
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Bands sliders
            Text(
                text = stringResource(R.string.equalizer_bands_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            state.bands.forEach { band ->
                val freqLabel = if (band.centerFreqHz >= 1000) {
                    "${band.centerFreqHz / 1000} kHz"
                } else {
                    "${band.centerFreqHz} Hz"
                }
                val dbLevel = band.currentLevelMb / 100

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = freqLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.equalizer_band_db, dbLevel),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = band.currentLevelMb.toFloat(),
                        onValueChange = { newLevel ->
                            viewModel.setBandLevel(band.index, newLevel.toInt().toShort())
                        },
                        valueRange = band.minLevelMb.toFloat()..band.maxLevelMb.toFloat(),
                        enabled = state.isSupported && state.isEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("equalizer_band_slider_${band.index}")
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Audio Effects: Bass Boost & Virtualizer
            Text(
                text = stringResource(R.string.audio_effects_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Bass Boost Slider
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.bass_boost_label),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${(state.bassBoostStrength / 10)}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = state.bassBoostStrength.toFloat(),
                    onValueChange = { viewModel.setBassBoostStrength(it.toInt().toShort()) },
                    valueRange = 0f..1000f,
                    enabled = state.isSupported && state.isEnabled && state.isBassBoostSupported,
                    modifier = Modifier.fillMaxWidth().testTag("bass_boost_slider")
                )
            }

            // Virtualizer Slider
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.virtualizer_label),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${(state.virtualizerStrength / 10)}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = state.virtualizerStrength.toFloat(),
                    onValueChange = { viewModel.setVirtualizerStrength(it.toInt().toShort()) },
                    valueRange = 0f..1000f,
                    enabled = state.isSupported && state.isEnabled && state.isVirtualizerSupported,
                    modifier = Modifier.fillMaxWidth().testTag("virtualizer_slider")
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
