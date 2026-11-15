/*
 * Copyright (C) 2023-24 Paranoid Android
 *           (C) 2026 PixelOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package net.pixelos.dolby.settings

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import net.pixelos.dolby.DolbyConstants.Companion.dlog
import net.pixelos.dolby.DolbyConstants.Output
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DolbyUiState(
    val loaded: Boolean = false,
    val dsOn: Boolean = false,
    val profile: Int = -1,
    val isProfileKnown: Boolean = false,
    val output: Output = Output.SPEAKER,
    val isMonoSpeaker: Boolean = false,
    val geqEnabled: Boolean = false,
    val presetName: String = "",
    val ieqPreset: Int = 0,
    val ieqAmount: Int = 0,
    val speakerVirtualizer: Boolean = false,
    val headphoneVirtualizer: Boolean = false,
    val stereoWidening: Int = 0,
    val dialogueEnhancer: Int = 0,
    val dialogueDucking: Int = 0,
    val bassEnhancer: Boolean = false,
    val virtualBass: Boolean = false,
    val volumeLeveler: Boolean = false,
    val volumeLevelerAmount: Int = 0,
    val reverbReduction: Boolean = false,
    val reverbReductionAmount: Int = 0,
) {
    /** Profile specific settings can be changed. */
    val settingsEnabled: Boolean
        get() = dsOn && isProfileKnown

    val isOnSpeaker: Boolean
        get() = output == Output.SPEAKER
}

internal class DolbySettingsViewModel(
    private val repository: DolbySettingsRepository,
    private val audioManager: AudioManager,
) : ViewModel() {

    private val _state = MutableStateFlow(DolbyUiState())
    val state: StateFlow<DolbyUiState> = _state.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var tracking = false

    // The speaker and headphones have their own settings, show the ones of the current output.
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            dlog(TAG, "onAudioDevicesAdded")
            updateOutput()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            dlog(TAG, "onAudioDevicesRemoved")
            updateOutput()
        }
    }

    /** Start following the output device, while the screen is visible. */
    fun startTracking() {
        if (tracking) return
        tracking = true
        // Registering calls onAudioDevicesAdded with the current devices.
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, handler)
        updateOutput()
    }

    fun stopTracking() {
        if (!tracking) return
        tracking = false
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
    }

    /** Reload everything, other entry points (QS tile, equalizer) may have changed it. */
    fun refresh() {
        _state.value = repository.load(repository.getCurrentOutput())
    }

    private fun updateOutput() {
        val output = repository.getCurrentOutput()
        if (_state.value.loaded && _state.value.output == output) return
        dlog(TAG, "updateOutput($output)")
        _state.value = repository.load(output)
    }

    fun setDsOn(value: Boolean) {
        repository.setDsOn(value)
        refresh()
    }

    fun setProfile(value: Int) {
        repository.setProfile(value)
        refresh()
    }

    fun resetProfile() {
        repository.resetProfile()
        refresh()
    }

    // Settings of the whole profile

    fun setSpeakerVirtualizer(value: Boolean) = edit({ copy(speakerVirtualizer = value) }) {
        repository.setSpeakerVirtualizer(it.profile, value)
    }

    fun setHeadphoneVirtualizer(value: Boolean) = edit({ copy(headphoneVirtualizer = value) }) {
        repository.setHeadphoneVirtualizer(it.profile, value)
    }

    fun setStereoWidening(value: Int) = edit({ copy(stereoWidening = value) }) {
        repository.setStereoWidening(it.profile, value)
    }

    fun setReverbReduction(value: Boolean) = edit({ copy(reverbReduction = value) }) {
        repository.setReverbReduction(it.profile, value)
    }

    fun setReverbReductionAmount(value: Int) = edit({ copy(reverbReductionAmount = value) }) {
        repository.setReverbReductionAmount(it.profile, value)
    }

    // Settings of each output

    fun setGeqEnabled(value: Boolean) = edit({ copy(geqEnabled = value) }) {
        repository.setGeqEnabled(it.profile, it.output, value)
    }

    fun setIeqPreset(value: Int) = edit({ copy(ieqPreset = value) }) {
        repository.setIeqPreset(it.profile, it.output, value)
    }

    fun setIeqAmount(value: Int) = edit({ copy(ieqAmount = value) }) {
        repository.setIeqAmount(it.profile, it.output, value)
    }

    fun setDialogueEnhancer(value: Int) = edit({ copy(dialogueEnhancer = value) }) {
        repository.setDialogueEnhancer(it.profile, it.output, value)
    }

    fun setDialogueDucking(value: Int) = edit({ copy(dialogueDucking = value) }) {
        repository.setDialogueDucking(it.profile, it.output, value)
    }

    fun setBassEnhancer(value: Boolean) = edit({ copy(bassEnhancer = value) }) {
        repository.setBassEnhancer(it.profile, it.output, value, virtualBass = it.virtualBass)
    }

    fun setVirtualBass(value: Boolean) = edit({ copy(virtualBass = value) }) {
        repository.setVirtualBass(it.profile, it.output, value)
    }

    fun setVolumeLeveler(value: Boolean) = edit({ copy(volumeLeveler = value) }) {
        repository.setVolumeLeveler(it.profile, it.output, value)
    }

    fun setVolumeLevelerAmount(value: Int) = edit({ copy(volumeLevelerAmount = value) }) {
        repository.setVolumeLevelerAmount(it.profile, it.output, value)
    }

    /** Applies [apply] with the current state, then shows the change. */
    private inline fun edit(
        crossinline change: DolbyUiState.() -> DolbyUiState,
        apply: (DolbyUiState) -> Unit,
    ) {
        val current = _state.value
        if (!current.settingsEnabled) return
        apply(current)
        _state.update { it.change() }
    }

    override fun onCleared() {
        stopTracking()
    }

    companion object {
        private const val TAG = "DolbySettingsVM"

        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                DolbySettingsViewModel(
                    repository = DolbySettingsRepository(app),
                    audioManager = app.getSystemService(AudioManager::class.java)!!,
                )
            }
        }
    }
}
