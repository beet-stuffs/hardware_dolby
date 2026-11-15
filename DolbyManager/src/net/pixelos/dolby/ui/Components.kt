/*
 * Copyright (C) 2026 PixelOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package net.pixelos.dolby.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.android.settingslib.spa.framework.theme.SettingsDimension
import com.android.settingslib.spa.framework.theme.SettingsOpacity.alphaForEnabled
import com.android.settingslib.spa.framework.theme.SettingsShape
import com.android.settingslib.spa.framework.theme.SettingsSpace
import com.android.settingslib.spa.widget.ui.SettingsBody
import com.android.settingslib.spa.widget.ui.SettingsTitle
import kotlin.math.ceil
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
 * Container for a custom row inside a SPA Category, matching the look of the SPA expressive
 * preferences (surfaceBright background, small inner corners, same paddings).
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

/** One option of a [ConnectedSelector]. */
data class SelectorOption(
    val value: Int,
    val label: String,
    val icon: Painter? = null,
)

/** A title (and optional summary) followed by a [ConnectedSelector]. */
@Composable
fun SelectorPreference(
    title: String,
    options: List<SelectorOption>,
    selectedValue: Int?,
    onSelect: (Int) -> Unit,
    enabled: Boolean = true,
    summary: String? = null,
) {
    SettingsRowContainer {
        Column(Modifier.alphaForEnabled(enabled)) {
            SettingsTitle(title)
            if (!summary.isNullOrEmpty()) SettingsBody(summary)
        }
        Spacer(Modifier.height(SettingsSpace.extraSmall6))
        ConnectedSelector(
            options = options,
            selectedValue = selectedValue,
            onSelect = onSelect,
            enabled = enabled,
        )
    }
}

/**
 * A connected button group where a single option is selected, like the M3 expressive
 * segmented buttons. The options wrap into more rows when their labels don't fit, for example
 * with a large font scale.
 */
@Composable
fun ConnectedSelector(
    options: List<SelectorOption>,
    selectedValue: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (options.isEmpty()) return
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    val hasIcons = options.any { it.icon != null }
    val widestLabel: Dp = remember(options, labelStyle, density) {
        val px = options.maxOf { textMeasurer.measure(it.label, labelStyle).size.width }
        with(density) { px.toDp() }
    }
    val itemPadding = if (hasIcons) 8.dp else 12.dp

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val spacing = ButtonGroupDefaults.ConnectedSpaceBetween
        val itemMinWidth = widestLabel + itemPadding * 2 + 4.dp
        val fitting = ((maxWidth + spacing) / (itemMinWidth + spacing)).toInt()
            .coerceIn(1, options.size)
        // Spread the options evenly over the rows instead of leaving a lonely one.
        val rowCount = ceil(options.size / fitting.toFloat()).toInt()
        val perRow = ceil(options.size / rowCount.toFloat()).toInt()

        Column(verticalArrangement = Arrangement.spacedBy(SettingsSpace.extraSmall4)) {
            options.chunked(perRow).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                ) {
                    row.forEachIndexed { index, option ->
                        SelectorButton(
                            option = option,
                            checked = option.value == selectedValue,
                            enabled = enabled,
                            shapes = connectedShapes(index, row.size),
                            hasIcons = hasIcons,
                            padding = itemPadding,
                            onClick = { onSelect(option.value) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun connectedShapes(index: Int, count: Int): ToggleButtonShapes =
    when {
        count == 1 -> ToggleButtonDefaults.shapes()
        index == 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
        index == count - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
    }

@Composable
private fun SelectorButton(
    option: SelectorOption,
    checked: Boolean,
    enabled: Boolean,
    shapes: ToggleButtonShapes,
    hasIcons: Boolean,
    padding: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToggleButton(
        checked = checked,
        // Selecting the selected option again does nothing, like a radio button.
        onCheckedChange = { if (!checked) onClick() },
        modifier = modifier
            .heightIn(min = if (hasIcons) 64.dp else 48.dp)
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                selected = checked
            },
        enabled = enabled,
        shapes = shapes,
        contentPadding = PaddingValues(horizontal = padding, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            option.icon?.let {
                Icon(painter = it, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(Modifier.height(SettingsSpace.extraSmall2))
            }
            Text(
                text = option.label,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A slider with its title and current value, applying the value when the user lets go,
 * like the old SeekBarPreference. Follows [value] when it changes from outside.
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
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                modifier = Modifier
                    .weight(1f)
                    .semantics { stateDescription = shown.toString() },
            )
            Text(
                text = shown.toString(),
                modifier = Modifier
                    .widthIn(min = 40.dp)
                    .padding(start = SettingsSpace.extraSmall6)
                    .alphaForEnabled(enabled),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.End,
            )
        }
    }
}
