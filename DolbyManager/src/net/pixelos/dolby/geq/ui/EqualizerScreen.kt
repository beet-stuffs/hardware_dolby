/*
 * Copyright (C) 2024 Paranoid Android
 *           (C) 2026 PixelOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package net.pixelos.dolby.geq.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.pixelos.dolby.R
import net.pixelos.dolby.geq.data.Preset
import net.pixelos.dolby.ui.CenteredContent
import net.pixelos.dolby.ui.SettingsRowContainer
import com.android.settingslib.spa.framework.theme.SettingsSpace
import com.android.settingslib.spa.widget.scaffold.SettingsScaffold
import com.android.settingslib.spa.widget.ui.Category
import kotlin.math.roundToInt

private enum class EqualizerDialog { NEW_PRESET, RENAME_PRESET, DELETE_PRESET, RESET_GAINS }

@Composable
fun EqualizerScreen(viewModel: EqualizerViewModel) {
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val currentPreset by viewModel.preset.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<EqualizerDialog?>(null) }

    // One slider state per band, recreated when the gains change from outside (preset change,
    // reset). Dragging updates the state, and the gain is applied when the user lets go.
    val bandGains = currentPreset.bandGains
    val sliderStates = remember(bandGains) {
        bandGains.mapIndexed { index, bandGain ->
            SliderState(value = bandGain.gain.toFloat(), valueRange = GainRange).also { state ->
                state.onValueChangeFinished = {
                    viewModel.setGain(index, state.value.roundToInt())
                }
            }
        }
    }

    SettingsScaffold(
        title = stringResource(R.string.dolby_preset),
        actions = {
            EqualizerActions(
                isUserPreset = currentPreset.isUserDefined,
                onShowDialog = { dialog = it },
                onResetGains = viewModel::reset,
            )
        },
    ) { paddingValues ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(paddingValues.calculateTopPadding()))
            CenteredContent {
                PresetSelector(
                    presets = presets,
                    currentPreset = currentPreset,
                    onPresetSelected = viewModel::setPreset,
                )
                Spacer(Modifier.height(SettingsSpace.extraSmall4))
                Category {
                    SettingsRowContainer {
                        FrequencyResponse(
                            gainsDb = { sliderStates.map { gainToDb(it.value) } },
                        )
                    }
                    SettingsRowContainer {
                        EqualizerBands(
                            bands = bandGains.map { it.band },
                            sliderStates = sliderStates,
                        )
                    }
                }
            }
            Spacer(Modifier.height(paddingValues.calculateBottomPadding()))
        }
    }

    EqualizerDialogs(
        dialog = dialog,
        currentPreset = currentPreset,
        viewModel = viewModel,
        onDismiss = { dialog = null },
    )
}

@Composable
private fun RowScope.EqualizerActions(
    isUserPreset: Boolean,
    onShowDialog: (EqualizerDialog) -> Unit,
    onResetGains: () -> Unit,
) {
    TooltipIconButton(
        icon = ImageVector.vectorResource(R.drawable.save_as_24px),
        text = stringResource(R.string.dolby_geq_new_preset),
        onClick = { onShowDialog(EqualizerDialog.NEW_PRESET) },
    )
    TooltipIconButton(
        icon = ImageVector.vectorResource(R.drawable.reset_settings_24px),
        text = stringResource(R.string.dolby_geq_reset_gains),
        onClick = {
            // Built-in presets go back to flat right away, user presets ask first.
            if (isUserPreset) onShowDialog(EqualizerDialog.RESET_GAINS) else onResetGains()
        },
    )
    if (isUserPreset) {
        var expanded by remember { mutableStateOf(false) }
        Box {
            TooltipIconButton(
                icon = Icons.Default.MoreVert,
                text = stringResource(R.string.dolby_geq_more_options),
                onClick = { expanded = true },
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.dolby_geq_rename_preset)) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onShowDialog(EqualizerDialog.RENAME_PRESET)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.dolby_geq_delete_preset)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onShowDialog(EqualizerDialog.DELETE_PRESET)
                    },
                )
            }
        }
    }
}

@Composable
private fun EqualizerDialogs(
    dialog: EqualizerDialog?,
    currentPreset: Preset,
    viewModel: EqualizerViewModel,
    onDismiss: () -> Unit,
) {
    when (dialog) {
        EqualizerDialog.NEW_PRESET -> PresetNameDialog(
            title = stringResource(R.string.dolby_geq_new_preset),
            onPresetNameSet = { viewModel.createNewPreset(name = it) },
            onDismissDialog = onDismiss,
        )

        EqualizerDialog.RENAME_PRESET -> PresetNameDialog(
            title = stringResource(R.string.dolby_geq_rename_preset),
            presetName = currentPreset.name,
            onPresetNameSet = { viewModel.renamePreset(preset = currentPreset, name = it) },
            onDismissDialog = onDismiss,
        )

        EqualizerDialog.DELETE_PRESET -> ConfirmationDialog(
            text = stringResource(R.string.dolby_geq_delete_preset_prompt),
            onConfirm = { viewModel.deletePreset(currentPreset) },
            onDismiss = onDismiss,
        )

        EqualizerDialog.RESET_GAINS -> ConfirmationDialog(
            text = stringResource(R.string.dolby_geq_reset_gains_prompt),
            onConfirm = { viewModel.reset() },
            onDismiss = onDismiss,
        )

        null -> Unit
    }
}
