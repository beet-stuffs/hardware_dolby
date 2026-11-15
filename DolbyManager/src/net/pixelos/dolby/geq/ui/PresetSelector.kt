/*
 * Copyright (C) 2024 Paranoid Android
 *           (C) 2026 PixelOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package net.pixelos.dolby.geq.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import net.pixelos.dolby.R
import net.pixelos.dolby.geq.data.Preset
import com.android.settingslib.spa.framework.theme.SettingsSpace
import com.android.settingslib.spa.widget.ui.CategoryTitle

/**
 * Horizontally scrolling chips with all presets, user presets first. A preset that isn't in the
 * list (custom gains) is shown as an extra selected chip at the start.
 */
@Composable
fun PresetSelector(
    presets: List<Preset>,
    currentPreset: Preset,
    onPresetSelected: (Preset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCurrent = { preset: Preset ->
        preset.name == currentPreset.name && preset.isUserDefined == currentPreset.isUserDefined
    }
    val chips = if (presets.any(isCurrent)) presets else listOf(currentPreset) + presets
    val selectedIndex = chips.indexOfFirst(isCurrent)
    val listState = rememberLazyListState()

    // Bring the selected preset into view, also when it changes from the top bar actions.
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) listState.animateScrollToItem(selectedIndex)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = SettingsSpace.small1)) {
            CategoryTitle(stringResource(R.string.dolby_geq_preset))
        }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = SettingsSpace.small1),
            horizontalArrangement = Arrangement.spacedBy(SettingsSpace.extraSmall4),
        ) {
            items(chips, key = { "${it.isUserDefined}/${it.name}" }) { preset ->
                val selected = isCurrent(preset)
                FilterChip(
                    selected = selected,
                    onClick = { if (!selected) onPresetSelected(preset) },
                    label = { Text(preset.name) },
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    } else null,
                )
            }
        }
    }
}
