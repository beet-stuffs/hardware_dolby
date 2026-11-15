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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.pixelos.dolby.R
import net.pixelos.dolby.geq.EqualizerActivity
import net.pixelos.dolby.ui.AmountSliderPreference
import net.pixelos.dolby.ui.CenteredContent
import com.android.settingslib.spa.widget.dialog.AlertDialogButton
import com.android.settingslib.spa.widget.dialog.rememberAlertDialogPresenter
import com.android.settingslib.spa.widget.preference.ListPreference
import com.android.settingslib.spa.widget.preference.ListPreferenceModel
import com.android.settingslib.spa.widget.preference.ListPreferenceOption
import com.android.settingslib.spa.widget.preference.MainSwitchPreference
import com.android.settingslib.spa.widget.preference.Preference
import com.android.settingslib.spa.widget.preference.PreferenceModel
import com.android.settingslib.spa.widget.preference.RadioPreferences
import com.android.settingslib.spa.widget.preference.SwitchPreference
import com.android.settingslib.spa.widget.preference.SwitchPreferenceModel
import com.android.settingslib.spa.widget.preference.TwoTargetSwitchPreference
import com.android.settingslib.spa.widget.scaffold.SettingsScaffold
import com.android.settingslib.spa.widget.ui.Category
import com.android.settingslib.spa.widget.ui.Footer
import com.android.settingslib.spa.widget.ui.SettingsIcon

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
    val profileTitle = stringResource(R.string.dolby_category_profile)
    val profileIcons = profileValues.map { ImageVector.vectorResource(profileIcon(it)) }
    val profileOptions = profileValues.mapIndexed { index, value ->
        ListPreferenceOption(
            id = value,
            text = profileNames[index],
            icon = { SettingsIcon(profileIcons[index]) },
        )
    }
    RadioPreferences(
        object : ListPreferenceModel {
            override val title = profileTitle
            override val enabled = { state.dsOn }
            override val options = profileOptions
            override val selectedId = mutableIntStateOf(state.profile)
            override val onIdSelected: (Int) -> Unit = {
                if (it != state.profile) viewModel.setProfile(it)
            }
        }
    )
    val profileName = profileValues.indexOf(state.profile)
        .takeIf { it >= 0 }
        ?.let { profileNames[it] }
        ?: stringResource(R.string.dolby_unknown)

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
        ValueListPreference(
            title = stringResource(R.string.dolby_ieq),
            entries = stringArrayResource(R.array.dolby_ieq_entries),
            values = stringArrayResource(R.array.dolby_ieq_values).map { it.toInt() },
            selected = state.ieqPreset,
            enabled = enabled,
            onSelect = viewModel::setIeqPreset,
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
        ValueListPreference(
            title = stringResource(R.string.dolby_stereo_widening),
            entries = stringArrayResource(R.array.dolby_stereo_entries),
            values = stringArrayResource(R.array.dolby_stereo_values).map { it.toInt() },
            selected = state.stereoWidening,
            enabled = enabled && !state.isOnSpeaker && state.headphoneVirtualizer,
            onSelect = viewModel::setStereoWidening,
        )
    }

    // Dialogue
    Category(title = stringResource(R.string.dolby_category_dialogue)) {
        ValueListPreference(
            title = stringResource(R.string.dolby_dialogue_enhancer),
            entries = stringArrayResource(R.array.dolby_dialogue_entries),
            values = stringArrayResource(R.array.dolby_dialogue_values).map { it.toInt() },
            selected = state.dialogueEnhancer,
            enabled = enabled,
            onSelect = viewModel::setDialogueEnhancer,
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

    ResetPreference(profileName = profileName, enabled = enabled, onReset = viewModel::resetProfile)

    // The speaker and headphones have their own settings, say which ones are shown.
    Footer(
        stringResource(
            if (state.isOnSpeaker) R.string.dolby_output_footer_speaker
            else R.string.dolby_output_footer_headphone
        )
    )
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

/**
 * A list setting showing its current value, tapping it opens a dialog to choose another.
 * A value that isn't one of [values] is shown as "Unknown".
 */
@Composable
private fun ValueListPreference(
    title: String,
    entries: Array<String>,
    values: List<Int>,
    selected: Int,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
) {
    val unknown = stringResource(R.string.dolby_unknown)
    val known = values.mapIndexed { index, value ->
        ListPreferenceOption(id = value, text = entries[index])
    }
    ListPreference(
        object : ListPreferenceModel {
            override val title = title
            override val enabled = { enabled }
            override val options = if (selected in values) {
                known
            } else {
                listOf(ListPreferenceOption(id = selected, text = unknown)) + known
            }
            override val selectedId = mutableIntStateOf(selected)
            override val onIdSelected: (Int) -> Unit = { if (it != selected) onSelect(it) }
        }
    )
}

@Composable
private fun ResetPreference(profileName: String, enabled: Boolean, onReset: () -> Unit) {
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
    val resetTitle = stringResource(R.string.dolby_reset_profile)
    Category {
        Preference(
            object : PreferenceModel {
                override val title = resetTitle
                override val enabled = { enabled }
                override val onClick: () -> Unit = { dialog.open() }
            }
        )
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
