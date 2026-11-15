/*
 * Copyright (C) 2026 PixelOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package net.pixelos.dolby.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.android.settingslib.spa.framework.theme.SettingsDimension
import com.android.settingslib.spa.framework.theme.SettingsOpacity.alphaForEnabled
import com.android.settingslib.spa.framework.theme.SettingsShape
import com.android.settingslib.spa.framework.theme.SettingsSpace
import com.android.settingslib.spa.framework.theme.surfaceTone
import com.android.settingslib.spa.widget.ui.SettingsBody
import com.android.settingslib.spa.widget.ui.SettingsTitle
import kotlin.math.roundToInt

/** Max width of the content, so it doesn't stretch on large screens. */
val ContentMaxWidth = 640.dp

/** Centers [content] horizontally and limits its width to [ContentMaxWidth]. */
@Composable
fun CenteredContent(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth(),
            content = content,
        )
    }
}

/**
 * Container for a custom row inside a SPA Category, with the same background, corners and
 * paddings as the SPA preferences next to it.
 */
@Composable
fun SettingsRowContainer(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = SettingsDimension.preferenceMinHeight)
            .background(MaterialTheme.colorScheme.surfaceBright, SettingsShape.CornerExtraSmall2)
            .padding(
                start = SettingsDimension.itemPaddingStart,
                end = SettingsDimension.itemPaddingEnd,
                top = SettingsSpace.small1,
                bottom = SettingsSpace.small1,
            ),
        verticalArrangement = Arrangement.Center,
        content = content,
    )
}

/**
 * Looks like the SPA SliderPreference, but follows [value] when it changes from outside and can
 * be disabled. The value is applied when the user lets go, like the old SeekBarPreference.
 */
@Composable
fun AmountSliderPreference(
    title: String,
    value: Int,
    valueRange: IntRange,
    onValueChangeFinished: (Int) -> Unit,
    enabled: Boolean = true,
    summary: String? = null,
) {
    var position by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val shown = position.roundToInt()
    SettingsRowContainer {
        Column(Modifier.alphaForEnabled(enabled)) {
            SettingsTitle(title)
            if (!summary.isNullOrEmpty()) SettingsBody(summary)
        }
        Slider(
            value = position,
            onValueChange = { position = it },
            onValueChangeFinished = {
                val newValue = position.roundToInt()
                position = newValue.toFloat()
                if (newValue != value) onValueChangeFinished(newValue)
            },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            enabled = enabled,
            colors = SliderDefaults.colors(
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceTone
            ),
            modifier = Modifier.semantics { stateDescription = shown.toString() },
        )
    }
}
