/*
 * Copyright (C) 2024 Paranoid Android
 *           (C) 2026 PixelOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package net.pixelos.dolby.geq.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.pixelos.dolby.R
import com.android.settingslib.spa.framework.theme.SettingsSpace
import java.util.Locale

/** Gains are -100..100 for the DSP, which is -10..10 dB. */
internal val GainRange = -100f..100f

internal fun gainToDb(gain: Float) = gain / 10f

private val BandSliderHeight = 240.dp
private val BandTrackWidth = 20.dp
private val BandThumbSize = DpSize(28.dp, 4.dp)

/** All the bands next to each other, every band takes the same width. */
@Composable
fun EqualizerBands(
    bands: List<Int>,
    sliderStates: List<SliderState>,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        sliderStates.forEachIndexed { index, state ->
            BandSlider(
                frequency = bands[index],
                state = state,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BandSlider(
    frequency: Int,
    state: SliderState,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val db = formatDb(gainToDb(state.value))
    val dbDescription = stringResource(R.string.dolby_geq_db, db)
    val frequencyDescription = if (frequency >= 1000) {
        stringResource(R.string.dolby_geq_band_khz, (frequency / 1000).toString())
    } else {
        stringResource(R.string.dolby_geq_band_hz, frequency.toString())
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BandText(db, MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(SettingsSpace.extraSmall4))
        VerticalSlider(
            state = state,
            modifier = Modifier
                .height(BandSliderHeight)
                .semantics {
                    contentDescription = frequencyDescription
                    stateDescription = dbDescription
                },
            // Higher gains at the top.
            reverseDirection = true,
            interactionSource = interactionSource,
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    sliderState = it,
                    thumbSize = BandThumbSize,
                )
            },
            track = {
                SliderDefaults.Track(
                    sliderState = it,
                    trackCornerSize = BandTrackWidth / 2,
                    modifier = Modifier.width(BandTrackWidth),
                    drawStopIndicator = null,
                )
            },
        )
        Spacer(Modifier.height(SettingsSpace.extraSmall4))
        BandText(
            text = if (frequency >= 1000) "${frequency / 1000}k" else "$frequency",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BandText(text: String, color: androidx.compose.ui.graphics.Color) {
    val style = MaterialTheme.typography.labelMedium
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        color = color,
        style = style,
        textAlign = TextAlign.Center,
        maxLines = 1,
        // Shrink instead of clipping with a large font scale.
        autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = style.fontSize),
    )
}

internal fun formatDb(db: Float): String = String.format(Locale.getDefault(), "%.1f", db)
