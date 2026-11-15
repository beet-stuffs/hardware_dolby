/*
 * Copyright (C) 2023-24 Paranoid Android
 *           (C) 2026 PixelOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package net.pixelos.dolby.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.pixelos.dolby.R
import net.pixelos.dolby.geq.EqualizerActivity
import net.pixelos.dolby.ui.AmountSliderPreference
import net.pixelos.dolby.ui.CenteredContent
import net.pixelos.dolby.ui.SelectorOption
import net.pixelos.dolby.ui.SelectorPreference
import com.android.settingslib.spa.framework.theme.SettingsShape
import com.android.settingslib.spa.framework.theme.SettingsSpace
import com.android.settingslib.spa.widget.dialog.AlertDialogButton
import com.android.settingslib.spa.widget.dialog.rememberAlertDialogPresenter
import com.android.settingslib.spa.widget.preference.MainSwitchPreference
import com.android.settingslib.spa.widget.preference.SwitchPreference
import com.android.settingslib.spa.widget.preference.SwitchPreferenceModel
import com.android.settingslib.spa.widget.preference.TwoTargetSwitchPreference
import com.android.settingslib.spa.widget.scaffold.SettingsScaffold
import com.android.settingslib.spa.widget.ui.Category

@Composable
internal fun DolbySettingsScreen(viewModel: DolbySettingsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SettingsScaffold(title = stringResource(R.string.dolby_title)) { paddingValues ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(paddingValues.calculateTopPadding()))
            if (state.loaded) {
                CenteredContent {
                    DolbySettingsContent(state, viewModel)
                }
            }
            Spacer(Modifier.height(paddingValues.calculateBottomPadding()))
        }
    }
}

@Composable
private fun DolbySettingsContent(state: DolbyUiState, viewModel: DolbySettingsViewModel) {
    val enabled = state.settingsEnabled
    val speakerOnly = stringResource(R.string.dolby_speaker_only)
    val connectHeadphones = stringResource(R.string.dolby_connect_headphones)

    // Main switch
    val enableTitle = stringResource(R.string.dolby_enable)
    MainSwitchPreference(
        object : SwitchPreferenceModel {
            override val title = enableTitle
            override val checked = { state.dsOn }
            override val onCheckedChange: (Boolean) -> Unit = { viewModel.setDsOn(it) }
        }
    )

    // Profile
    val profileNames = stringArrayResource(R.array.dolby_profile_entries)
    val profileValues = stringArrayResource(R.array.dolby_profile_values).map { it.toInt() }
    val profileOptions = profileValues.mapIndexed { index, value ->
        SelectorOption(
            value = value,
            label = profileNames[index],
            icon = painterResource(profileIcon(value)),
        )
    }
    val profileName = profileValues.indexOf(state.profile)
        .takeIf { it >= 0 }
        ?.let { profileNames[it] }
        ?: stringResource(R.string.dolby_unknown)
    Category {
        SelectorPreference(
            title = stringResource(R.string.dolby_profile_title),
            summary = if (state.isProfileKnown) null else profileName,
            options = profileOptions,
            selectedValue = state.profile.takeIf { state.isProfileKnown },
            onSelect = viewModel::setProfile,
            enabled = state.dsOn,
        )
    }

    OutputIndicator(isOnSpeaker = state.isOnSpeaker, enabled = enabled)

    // Equalizer
    Category(title = stringResource(R.string.dolby_category_equalizer)) {
        val context = LocalContext.current
        val geqTitle = stringResource(R.string.dolby_preset)
        TwoTargetSwitchPreference(
            model = object : SwitchPreferenceModel {
                override val title = geqTitle
                override val summary = { state.presetName }
                override val checked = { state.geqEnabled }
                override val changeable = { enabled }
                override val onCheckedChange: (Boolean) -> Unit = { viewModel.setGeqEnabled(it) }
            },
            primaryEnabled = { enabled && state.geqEnabled },
            primaryOnClick = {
                context.startActivity(Intent(context, EqualizerActivity::class.java))
            },
        )

        val ieqNames = stringArrayResource(R.array.dolby_ieq_entries)
        val ieqValues = stringArrayResource(R.array.dolby_ieq_values).map { it.toInt() }
        val ieqIcons = listOf(
            R.drawable.ic_ieq_off,
            R.drawable.ic_ieq_balanced,
            R.drawable.ic_ieq_warm,
            R.drawable.ic_ieq_detailed,
        )
        val ieqOptions = ieqValues.mapIndexed { index, value ->
            SelectorOption(
                value = value,
                label = ieqNames[index],
                icon = ieqIcons.getOrNull(index)?.let { painterResource(it) },
            )
        }
        val ieqKnown = state.ieqPreset in ieqValues
        SelectorPreference(
            title = stringResource(R.string.dolby_ieq),
            summary = if (ieqKnown) null else stringResource(R.string.dolby_unknown),
            options = ieqOptions,
            selectedValue = state.ieqPreset.takeIf { ieqKnown },
            onSelect = viewModel::setIeqPreset,
            enabled = enabled,
        )
        AnimatedVisibility(visible = state.ieqPreset != 0) {
            AmountSliderPreference(
                title = stringResource(R.string.dolby_ieq_amount),
                value = state.ieqAmount,
                valueRange = 0..16,
                onValueChangeFinished = viewModel::setIeqAmount,
                enabled = enabled,
            )
        }
    }

    // Spatial audio
    Category(title = stringResource(R.string.dolby_category_spatial)) {
        if (!state.isMonoSpeaker) {
            SwitchRow(
                title = stringResource(R.string.dolby_spk_virtualizer),
                summary = if (state.isOnSpeaker) "" else speakerOnly,
                checked = state.speakerVirtualizer,
                changeable = enabled && state.isOnSpeaker,
                onCheckedChange = viewModel::setSpeakerVirtualizer,
            )
        }
        SwitchRow(
            title = stringResource(R.string.dolby_hp_virtualizer),
            summary = if (state.isOnSpeaker) connectHeadphones else "",
            checked = state.headphoneVirtualizer,
            changeable = enabled && !state.isOnSpeaker,
            onCheckedChange = viewModel::setHeadphoneVirtualizer,
        )
        val stereoNames = stringArrayResource(R.array.dolby_stereo_entries)
        val stereoValues = stringArrayResource(R.array.dolby_stereo_values).map { it.toInt() }
        val stereoKnown = state.stereoWidening in stereoValues
        SelectorPreference(
            title = stringResource(R.string.dolby_stereo_widening),
            summary = when {
                state.isOnSpeaker -> connectHeadphones
                !stereoKnown -> stringResource(R.string.dolby_unknown)
                else -> null
            },
            options = stereoValues.mapIndexed { index, value ->
                SelectorOption(value = value, label = stereoNames[index])
            },
            selectedValue = state.stereoWidening.takeIf { stereoKnown },
            onSelect = viewModel::setStereoWidening,
            enabled = enabled && !state.isOnSpeaker && state.headphoneVirtualizer,
        )
    }

    // Dialogue
    Category(title = stringResource(R.string.dolby_category_dialogue)) {
        val dialogueNames = stringArrayResource(R.array.dolby_dialogue_entries)
        val dialogueValues = stringArrayResource(R.array.dolby_dialogue_values).map { it.toInt() }
        val dialogueKnown = state.dialogueEnhancer in dialogueValues
        SelectorPreference(
            title = stringResource(R.string.dolby_dialogue_enhancer),
            summary = if (dialogueKnown) null else stringResource(R.string.dolby_unknown),
            options = dialogueValues.mapIndexed { index, value ->
                SelectorOption(value = value, label = dialogueNames[index])
            },
            selectedValue = state.dialogueEnhancer.takeIf { dialogueKnown },
            onSelect = viewModel::setDialogueEnhancer,
            enabled = enabled,
        )
        AnimatedVisibility(visible = state.dialogueEnhancer != 0) {
            AmountSliderPreference(
                title = stringResource(R.string.dolby_dialogue_ducking),
                summary = stringResource(R.string.dolby_dialogue_ducking_summary),
                value = state.dialogueDucking,
                valueRange = 0..16,
                onValueChangeFinished = viewModel::setDialogueDucking,
                enabled = enabled,
            )
        }
    }

    // Bass
    Category(title = stringResource(R.string.dolby_category_bass)) {
        SwitchRow(
            title = stringResource(R.string.dolby_bass_enhancer),
            checked = state.bassEnhancer,
            changeable = enabled,
            onCheckedChange = viewModel::setBassEnhancer,
        )
        SwitchRow(
            title = stringResource(R.string.dolby_virtual_bass),
            summary = if (state.isOnSpeaker) {
                stringResource(R.string.dolby_virtual_bass_summary)
            } else {
                speakerOnly
            },
            checked = state.virtualBass,
            changeable = enabled && state.isOnSpeaker,
            onCheckedChange = viewModel::setVirtualBass,
        )
    }

    // Volume and room
    Category(title = stringResource(R.string.dolby_category_volume)) {
        SwitchRow(
            title = stringResource(R.string.dolby_volume_leveler),
            checked = state.volumeLeveler,
            changeable = enabled,
            onCheckedChange = viewModel::setVolumeLeveler,
        )
        AnimatedVisibility(visible = state.volumeLeveler) {
            AmountSliderPreference(
                title = stringResource(R.string.dolby_volume_leveler_amount),
                value = state.volumeLevelerAmount,
                valueRange = 0..10,
                onValueChangeFinished = viewModel::setVolumeLevelerAmount,
                enabled = enabled,
            )
        }
        SwitchRow(
            title = stringResource(R.string.dolby_reverb_reduction),
            summary = stringResource(R.string.dolby_reverb_reduction_summary),
            checked = state.reverbReduction,
            changeable = enabled,
            onCheckedChange = viewModel::setReverbReduction,
        )
        AnimatedVisibility(visible = state.reverbReduction) {
            AmountSliderPreference(
                title = stringResource(R.string.dolby_reverb_reduction_amount),
                value = state.reverbReductionAmount,
                valueRange = 0..16,
                onValueChangeFinished = viewModel::setReverbReductionAmount,
                enabled = enabled,
            )
        }
    }

    ResetButton(profileName = profileName, enabled = enabled, onReset = viewModel::resetProfile)
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    changeable: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    summary: String = "",
) {
    SwitchPreference(
        object : SwitchPreferenceModel {
            override val title = title
            override val summary = { summary }
            override val checked = { checked }
            override val changeable = { changeable }
            override val onCheckedChange = onCheckedChange
        }
    )
}

/** Shows which output the settings below belong to. */
@Composable
private fun OutputIndicator(isOnSpeaker: Boolean, enabled: Boolean) {
    Row(
        modifier = Modifier.padding(
            horizontal = SettingsSpace.small1,
            vertical = SettingsSpace.extraSmall4,
        ),
    ) {
        Surface(
            shape = SettingsShape.CornerFull,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = SettingsSpace.small1, vertical = SettingsSpace.extraSmall4)
                    .semantics(mergeDescendants = true) { heading() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SettingsSpace.extraSmall4),
            ) {
                Icon(
                    imageVector = if (isOnSpeaker) Icons.Outlined.Speaker else Icons.Outlined.Headphones,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(
                        if (isOnSpeaker) R.string.dolby_output_speaker
                        else R.string.dolby_output_headphone
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ResetButton(profileName: String, enabled: Boolean, onReset: () -> Unit) {
    val context = LocalContext.current
    val toastText = stringResource(R.string.dolby_reset_profile_toast, profileName)
    val dialog = rememberAlertDialogPresenter(
        confirmButton = AlertDialogButton(stringResource(R.string.dolby_reset)) {
            onReset()
            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
        },
        dismissButton = AlertDialogButton(stringResource(android.R.string.cancel)),
        title = stringResource(R.string.dolby_reset_profile_dialog_title, profileName),
        text = { Text(stringResource(R.string.dolby_reset_profile_dialog_text)) },
    )
    Row(
        modifier = Modifier.padding(
            start = SettingsSpace.small1,
            end = SettingsSpace.small1,
            top = SettingsSpace.extraSmall4,
            bottom = SettingsSpace.small4,
        ),
    ) {
        FilledTonalButton(onClick = dialog::open, enabled = enabled) {
            Icon(
                imageVector = Icons.Outlined.RestartAlt,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.dolby_reset_profile))
        }
    }
}

private fun profileIcon(profile: Int): Int =
    when (profile) {
        0 -> R.drawable.ic_profile_dynamic
        1 -> R.drawable.ic_profile_movie
        2 -> R.drawable.ic_profile_music
        3 -> R.drawable.ic_profile_custom
        else -> R.drawable.ic_dolby
    }
