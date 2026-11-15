/*
 * Copyright (C) 2023-24 Paranoid Android
 *           (C) 2026 PixelOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package net.pixelos.dolby.settings

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import androidx.preference.PreferenceManager
import net.pixelos.dolby.DolbyConstants
import net.pixelos.dolby.DolbyConstants.Companion.PREF_BASS
import net.pixelos.dolby.DolbyConstants.Companion.PREF_DIALOGUE
import net.pixelos.dolby.DolbyConstants.Companion.PREF_DIALOGUE_DUCKING
import net.pixelos.dolby.DolbyConstants.Companion.PREF_GEQ
import net.pixelos.dolby.DolbyConstants.Companion.PREF_HP_VIRTUALIZER
import net.pixelos.dolby.DolbyConstants.Companion.PREF_IEQ
import net.pixelos.dolby.DolbyConstants.Companion.PREF_IEQ_AMOUNT
import net.pixelos.dolby.DolbyConstants.Companion.PREF_PROFILE
import net.pixelos.dolby.DolbyConstants.Companion.PREF_REVERB
import net.pixelos.dolby.DolbyConstants.Companion.PREF_REVERB_AMOUNT
import net.pixelos.dolby.DolbyConstants.Companion.PREF_SPK_VIRTUALIZER
import net.pixelos.dolby.DolbyConstants.Companion.PREF_STEREO
import net.pixelos.dolby.DolbyConstants.Companion.PREF_VIRTUAL_BASS
import net.pixelos.dolby.DolbyConstants.Companion.PREF_VOLUME
import net.pixelos.dolby.DolbyConstants.Companion.PREF_VOLUME_AMOUNT
import net.pixelos.dolby.DolbyConstants.Companion.dlog
import net.pixelos.dolby.DolbyConstants.Output
import net.pixelos.dolby.DolbyController
import net.pixelos.dolby.R

/**
 * Applies settings through [DolbyController] and saves them where
 * [DolbyController.restoreAllSettings] reads them after a reboot or an audioserver restart:
 *
 * - Main settings live in the default SharedPreferences.
 * - Profile specific settings live in "profile_<n>", output specific ones with the
 *   key suffixed by [DolbyConstants.outputPrefKey].
 * - List values are saved as strings, sliders as ints and switches as booleans.
 */
internal class DolbySettingsRepository(private val context: Context) {

    private val controller by lazy { DolbyController.getInstance(context) }
    private val audioManager by lazy { context.getSystemService(AudioManager::class.java)!! }
    private val defaultPrefs by lazy { PreferenceManager.getDefaultSharedPreferences(context) }

    val profileValues: List<Int> by lazy {
        context.resources.getStringArray(R.array.dolby_profile_values).map { it.toInt() }
    }

    val isMonoSpeaker: Boolean
        get() = controller.isMonoSpeaker

    fun getCurrentOutput(): Output {
        val device = audioManager.getDevicesForAttributes(ATTRIBUTES_MEDIA).firstOrNull()
        return if (device == null || device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
            Output.SPEAKER
        } else {
            Output.HEADPHONE
        }
    }

    fun load(output: Output): DolbyUiState {
        val dsOn = controller.dsOn
        val profile = controller.profile
        dlog(TAG, "load: dsOn=$dsOn profile=$profile output=$output")
        val base = DolbyUiState(
            loaded = true,
            dsOn = dsOn,
            profile = profile,
            isProfileKnown = profile in profileValues,
            output = output,
            isMonoSpeaker = isMonoSpeaker,
        )
        // Don't query the DSP with an invalid profile.
        if (profile == -1) return base
        return runCatching {
            base.copy(
                geqEnabled = controller.getGeqEnabled(output, profile),
                presetName = controller.getPresetName(),
                ieqPreset = controller.getIeqPreset(output, profile),
                ieqAmount = controller.getIeqAmount(output, profile),
                speakerVirtualizer = controller.getSpeakerVirtEnabled(profile),
                headphoneVirtualizer = controller.getHeadphoneVirtEnabled(profile),
                stereoWidening = controller.getStereoWideningAmount(profile),
                dialogueEnhancer = controller.getDialogueEnhancerAmount(output, profile),
                dialogueDucking = controller.getDialogueEnhancerDucking(output, profile),
                bassEnhancer = controller.getBassEnhancerEnabled(output, profile),
                virtualBass = controller.getVirtualBassEnabled(output, profile),
                volumeLeveler = controller.getVolumeLevelerEnabled(output, profile),
                volumeLevelerAmount = controller.getVolumeLevelerAmount(output, profile),
                reverbReduction = controller.getReverbReductionEnabled(profile),
                reverbReductionAmount = controller.getReverbReductionAmount(profile),
            )
        }.onFailure {
            Log.e(TAG, "Failed to load settings", it)
        }.getOrDefault(base)
    }

    // Main settings

    fun setDsOn(value: Boolean) {
        // Also saves PREF_ENABLE, so it is restored on boot.
        controller.setDsOnAndPersist(value)
    }

    fun setProfile(value: Int) {
        controller.profile = value
        defaultPrefs.edit().putString(PREF_PROFILE, value.toString()).apply()
    }

    fun resetProfile() {
        controller.resetProfileSpecificSettings()
    }

    // Settings of the whole profile

    fun setSpeakerVirtualizer(profile: Int, value: Boolean) {
        controller.setSpeakerVirtEnabled(value)
        profilePrefs(profile).edit().putBoolean(PREF_SPK_VIRTUALIZER, value).apply()
    }

    fun setHeadphoneVirtualizer(profile: Int, value: Boolean) {
        controller.setHeadphoneVirtEnabled(value)
        profilePrefs(profile).edit().putBoolean(PREF_HP_VIRTUALIZER, value).apply()
    }

    fun setStereoWidening(profile: Int, value: Int) {
        controller.setStereoWideningAmount(value)
        profilePrefs(profile).edit().putString(PREF_STEREO, value.toString()).apply()
    }

    fun setReverbReduction(profile: Int, value: Boolean) {
        controller.setReverbReductionEnabled(value)
        profilePrefs(profile).edit().putBoolean(PREF_REVERB, value).apply()
    }

    fun setReverbReductionAmount(profile: Int, value: Int) {
        controller.setReverbReductionAmount(value)
        profilePrefs(profile).edit().putInt(PREF_REVERB_AMOUNT, value).apply()
    }

    // Settings of each output

    fun setGeqEnabled(profile: Int, output: Output, value: Boolean) {
        controller.setGeqEnabled(value, output)
        profilePrefs(profile).edit().putBoolean(outputKey(PREF_GEQ, output), value).apply()
    }

    fun setIeqPreset(profile: Int, output: Output, value: Int) {
        controller.setIeqPreset(value, output)
        profilePrefs(profile).edit()
            .putString(outputKey(PREF_IEQ, output), value.toString()).apply()
    }

    fun setIeqAmount(profile: Int, output: Output, value: Int) {
        controller.setIeqAmount(value, output)
        profilePrefs(profile).edit().putInt(outputKey(PREF_IEQ_AMOUNT, output), value).apply()
    }

    fun setDialogueEnhancer(profile: Int, output: Output, value: Int) {
        controller.setDialogueEnhancerAmount(value, output)
        profilePrefs(profile).edit()
            .putString(outputKey(PREF_DIALOGUE, output), value.toString()).apply()
    }

    fun setDialogueDucking(profile: Int, output: Output, value: Int) {
        controller.setDialogueEnhancerDucking(value, output)
        profilePrefs(profile).edit()
            .putInt(outputKey(PREF_DIALOGUE_DUCKING, output), value).apply()
    }

    fun setBassEnhancer(profile: Int, output: Output, value: Boolean, virtualBass: Boolean) {
        controller.setBassEnhancerEnabled(value, output)
        // Turning the bass enhancer off also turns virtual bass off in the DSP, so put
        // it back to what is shown.
        controller.setVirtualBassEnabled(virtualBass, output)
        profilePrefs(profile).edit().putBoolean(outputKey(PREF_BASS, output), value).apply()
    }

    fun setVirtualBass(profile: Int, output: Output, value: Boolean) {
        controller.setVirtualBassEnabled(value, output)
        profilePrefs(profile).edit()
            .putBoolean(outputKey(PREF_VIRTUAL_BASS, output), value).apply()
    }

    fun setVolumeLeveler(profile: Int, output: Output, value: Boolean) {
        controller.setVolumeLevelerEnabled(value, output)
        profilePrefs(profile).edit().putBoolean(outputKey(PREF_VOLUME, output), value).apply()
    }

    fun setVolumeLevelerAmount(profile: Int, output: Output, value: Int) {
        controller.setVolumeLevelerAmount(value, output)
        profilePrefs(profile).edit()
            .putInt(outputKey(PREF_VOLUME_AMOUNT, output), value).apply()
    }

    private fun profilePrefs(profile: Int): SharedPreferences =
        context.getSharedPreferences("profile_$profile", Context.MODE_PRIVATE)

    private fun outputKey(key: String, output: Output) = DolbyConstants.outputPrefKey(key, output)

    private companion object {
        const val TAG = "DolbySettingsRepo"
        val ATTRIBUTES_MEDIA: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
    }
}
