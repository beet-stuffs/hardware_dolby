/*
 * Copyright (C) 2023-24 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.preference

import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.widget.CompoundButton
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.Preference.OnPreferenceChangeListener
import androidx.preference.PreferenceCategory
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.android.settingslib.widget.SettingsBasePreferenceFragment
import co.aospa.dolby.DolbyConstants.Companion.PREF_BASS
import co.aospa.dolby.DolbyConstants.Companion.PREF_CATEGORY_SETTINGS
import co.aospa.dolby.DolbyConstants.Companion.PREF_DIALOGUE
import co.aospa.dolby.DolbyConstants.Companion.PREF_DIALOGUE_DUCKING
import co.aospa.dolby.DolbyConstants.Companion.PREF_ENABLE
import co.aospa.dolby.DolbyConstants.Companion.PREF_GEQ
import co.aospa.dolby.DolbyConstants.Companion.PREF_HP_VIRTUALIZER
import co.aospa.dolby.DolbyConstants.Companion.PREF_IEQ
import co.aospa.dolby.DolbyConstants.Companion.PREF_IEQ_AMOUNT
import co.aospa.dolby.DolbyConstants.Companion.PREF_PRESET
import co.aospa.dolby.DolbyConstants.Companion.PREF_PROFILE
import co.aospa.dolby.DolbyConstants.Companion.PREF_RESET
import co.aospa.dolby.DolbyConstants.Companion.PREF_REVERB
import co.aospa.dolby.DolbyConstants.Companion.PREF_REVERB_AMOUNT
import co.aospa.dolby.DolbyConstants.Companion.PREF_SPK_VIRTUALIZER
import co.aospa.dolby.DolbyConstants.Companion.PREF_STEREO
import co.aospa.dolby.DolbyConstants.Companion.PREF_VIRTUAL_BASS
import co.aospa.dolby.DolbyConstants.Companion.PREF_VOLUME
import co.aospa.dolby.DolbyConstants.Companion.PREF_VOLUME_AMOUNT
import co.aospa.dolby.DolbyConstants.Companion.dlog
import co.aospa.dolby.DolbyConstants.Output
import co.aospa.dolby.DolbyController
import co.aospa.dolby.R
import com.android.settingslib.widget.MainSwitchPreference

class DolbySettingsFragment : SettingsBasePreferenceFragment(),
    OnPreferenceChangeListener, CompoundButton.OnCheckedChangeListener {

    private val switchBar by lazy {
        findPreference<MainSwitchPreference>(PREF_ENABLE)!!
    }
    private val profilePref by lazy {
        findPreference<ListPreference>(PREF_PROFILE)!!
    }
    private val settingsCategory by lazy {
        findPreference<PreferenceCategory>(PREF_CATEGORY_SETTINGS)!!
    }
    private val geqPref by lazy {
        findPreference<SwitchPreferenceCompat>(PREF_GEQ)!!
    }
    private val presetPref by lazy {
        findPreference<Preference>(PREF_PRESET)!!
    }
    private val ieqPref by lazy {
        findPreference<DolbyIeqPreference>(PREF_IEQ)!!
    }
    private val ieqAmountPref by lazy {
        findPreference<SeekBarPreference>(PREF_IEQ_AMOUNT)!!
    }
    private val stereoPref by lazy {
        findPreference<ListPreference>(PREF_STEREO)!!
    }
    private val dialoguePref by lazy {
        findPreference<ListPreference>(PREF_DIALOGUE)!!
    }
    private val duckingPref by lazy {
        findPreference<SeekBarPreference>(PREF_DIALOGUE_DUCKING)!!
    }
    private val bassPref by lazy {
        findPreference<SwitchPreferenceCompat>(PREF_BASS)!!
    }
    private val virtualBassPref by lazy {
        findPreference<SwitchPreferenceCompat>(PREF_VIRTUAL_BASS)!!
    }
    private val hpVirtPref by lazy {
        findPreference<SwitchPreferenceCompat>(PREF_HP_VIRTUALIZER)!!
    }
    private val spkVirtPref by lazy {
        findPreference<SwitchPreferenceCompat>(PREF_SPK_VIRTUALIZER)!!
    }
    private val volumePref by lazy {
        findPreference<SwitchPreferenceCompat>(PREF_VOLUME)!!
    }
    private val volumeAmountPref by lazy {
        findPreference<SeekBarPreference>(PREF_VOLUME_AMOUNT)!!
    }
    private val reverbPref by lazy {
        findPreference<SwitchPreferenceCompat>(PREF_REVERB)!!
    }
    private val reverbAmountPref by lazy {
        findPreference<SeekBarPreference>(PREF_REVERB_AMOUNT)!!
    }
    private val resetPref by lazy {
        findPreference<Preference>(PREF_RESET)!!
    }

    private val dolbyController by lazy { DolbyController.getInstance(requireContext()) }
    private val audioManager by lazy { requireContext().getSystemService(AudioManager::class.java) }
    private val preferenceStore by lazy { DolbyPreferenceStore(requireContext()) }
    private val handler = Handler()

    // The speaker and headphones have their own settings, show the ones of the current output.
    private var output = Output.SPEAKER
        set(value) {
            if (field == value) return
            field = value
            dlog(TAG, "setOutput($value)")
            preferenceStore.output = value
            updateProfileSpecificPrefs()
        }

    private val isOnSpeaker
        get() = output == Output.SPEAKER

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

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        dlog(TAG, "onCreatePreferences")
        addPreferencesFromResource(R.xml.dolby_settings)

        val profile = dolbyController.profile
        preferenceManager.preferenceDataStore = preferenceStore.also {
            it.profile = profile
        }

        val dsOn = dolbyController.dsOn
        switchBar.addOnSwitchChangeListener(this)
        switchBar.setChecked(dsOn)

        spkVirtPref.isVisible = !dolbyController.isMonoSpeaker

        profilePref.onPreferenceChangeListener = this
        updateProfileIcon(profile)
        profilePref.setEnabled(dsOn)
        profilePref.apply {
            if (entryValues.contains(profile.toString())) {
                summary = "%s"
                value = profile.toString()
            } else {
                summary = context.getString(R.string.dolby_unknown)
            }
        }

        listOf(
            geqPref, ieqPref, ieqAmountPref, hpVirtPref, spkVirtPref, stereoPref, dialoguePref,
            duckingPref, bassPref, virtualBassPref, volumePref, volumeAmountPref, reverbPref,
            reverbAmountPref
        ).forEach { it.onPreferenceChangeListener = this }

        resetPref.setOnPreferenceClickListener {
            dolbyController.resetProfileSpecificSettings()
            updateProfileSpecificPrefs()
            Toast.makeText(
                requireContext(),
                getString(R.string.dolby_reset_profile_toast, profilePref.summary),
                Toast.LENGTH_SHORT
            ).show()
            true
        }

        audioManager!!.registerAudioDeviceCallback(audioDeviceCallback, handler)
        updateOutput()
        updateProfileSpecificPrefs()
    }

    override fun onDestroyView() {
        dlog(TAG, "onDestroyView")
        audioManager!!.unregisterAudioDeviceCallback(audioDeviceCallback)
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        updateProfileSpecificPrefs()
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        dlog(TAG, "onPreferenceChange: key=${preference.key} value=$newValue")
        when (preference.key) {
            PREF_PROFILE -> {
                val profile = newValue.toString().toInt()
                dolbyController.profile = profile
                preferenceStore.profile = profile
                updateProfileIcon(profile)
                updateProfileSpecificPrefs()
            }

            PREF_SPK_VIRTUALIZER -> {
                dolbyController.setSpeakerVirtEnabled(newValue as Boolean)
            }

            PREF_HP_VIRTUALIZER -> {
                dolbyController.setHeadphoneVirtEnabled(newValue as Boolean)
            }

            PREF_STEREO -> {
                dolbyController.setStereoWideningAmount(newValue.toString().toInt())
            }

            PREF_REVERB -> {
                dolbyController.setReverbReductionEnabled(newValue as Boolean)
            }

            PREF_REVERB_AMOUNT -> {
                dolbyController.setReverbReductionAmount(newValue as Int)
            }

            PREF_GEQ -> {
                dolbyController.setGeqEnabled(newValue as Boolean, output)
            }

            PREF_IEQ -> {
                val value = newValue.toString().toInt()
                dolbyController.setIeqPreset(value, output)
                ieqAmountPref.setEnabled(value != 0)
            }

            PREF_IEQ_AMOUNT -> {
                dolbyController.setIeqAmount(newValue as Int, output)
            }

            PREF_DIALOGUE -> {
                val value = newValue.toString().toInt()
                dolbyController.setDialogueEnhancerAmount(value, output)
                duckingPref.setEnabled(value != 0)
            }

            PREF_DIALOGUE_DUCKING -> {
                dolbyController.setDialogueEnhancerDucking(newValue as Int, output)
            }

            PREF_BASS -> {
                dolbyController.setBassEnhancerEnabled(newValue as Boolean, output)
                // Turning the bass enhancer off also turns virtual bass off in the DSP, so put
                // it back to what is shown.
                dolbyController.setVirtualBassEnabled(virtualBassPref.isChecked, output)
            }

            PREF_VIRTUAL_BASS -> {
                dolbyController.setVirtualBassEnabled(newValue as Boolean, output)
            }

            PREF_VOLUME -> {
                dolbyController.setVolumeLevelerEnabled(newValue as Boolean, output)
            }

            PREF_VOLUME_AMOUNT -> {
                dolbyController.setVolumeLevelerAmount(newValue as Int, output)
            }

            else -> return false
        }
        return true
    }

    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        dlog(TAG, "onCheckedChanged($isChecked)")
        dolbyController.dsOn = isChecked
        profilePref.setEnabled(isChecked)
        updateProfileSpecificPrefs()
    }

    private fun getCurrentOutput(): Output {
        val device = audioManager!!.getDevicesForAttributes(ATTRIBUTES_MEDIA).firstOrNull()
        return if (device == null || device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
            Output.SPEAKER
        } else {
            Output.HEADPHONE
        }
    }

    private fun updateOutput() {
        output = getCurrentOutput()
    }

    private fun updateProfileSpecificPrefs() {
        val unknownRes = getString(R.string.dolby_unknown)
        val headphoneRes = getString(R.string.dolby_connect_headphones)
        val speakerRes = getString(R.string.dolby_speaker_only)
        val dsOn = dolbyController.dsOn
        val currentProfile = dolbyController.profile

        dlog(
            TAG, "updateProfileSpecificPrefs: dsOn=$dsOn currentProfile=$currentProfile"
                    + " output=$output"
        )

        settingsCategory.title = getString(
            if (isOnSpeaker) R.string.dolby_category_speaker
            else R.string.dolby_category_headphone
        )

        val enable = dsOn && (currentProfile != -1)
        listOf(
            geqPref, presetPref, ieqPref, ieqAmountPref, dialoguePref, duckingPref,
            volumePref, volumeAmountPref, bassPref, reverbPref, reverbAmountPref, resetPref
        ).forEach { it.setEnabled(enable) }
        // These only do something on the speaker, the headphone virtualizer is the reverse.
        spkVirtPref.setEnabled(enable && isOnSpeaker)
        virtualBassPref.setEnabled(enable && isOnSpeaker)
        hpVirtPref.setEnabled(enable && !isOnSpeaker)
        stereoPref.setEnabled(enable && !isOnSpeaker)

        if (!enable) return

        geqPref.setChecked(dolbyController.getGeqEnabled(output, currentProfile))
        presetPref.summary = dolbyController.getPresetName()

        val ieqValue = dolbyController.getIeqPreset(output, currentProfile)
        ieqPref.apply {
            if (entryValues.contains(ieqValue.toString())) {
                summary = "%s"
                value = ieqValue.toString()
            } else {
                summary = unknownRes
            }
        }
        ieqAmountPref.apply {
            value = dolbyController.getIeqAmount(output, currentProfile)
            setEnabled(ieqValue != 0)
        }

        val deValue = dolbyController.getDialogueEnhancerAmount(output, currentProfile)
        dialoguePref.apply {
            if (entryValues.contains(deValue.toString())) {
                summary = "%s"
                value = deValue.toString()
            } else {
                summary = unknownRes
            }
        }
        duckingPref.apply {
            value = dolbyController.getDialogueEnhancerDucking(output, currentProfile)
            setEnabled(deValue != 0)
        }

        spkVirtPref.setChecked(dolbyController.getSpeakerVirtEnabled(currentProfile))
        volumePref.setChecked(dolbyController.getVolumeLevelerEnabled(output, currentProfile))
        volumeAmountPref.value = dolbyController.getVolumeLevelerAmount(output, currentProfile)
        bassPref.setChecked(dolbyController.getBassEnhancerEnabled(output, currentProfile))
        virtualBassPref.setChecked(
            dolbyController.getVirtualBassEnabled(output, currentProfile)
        )
        reverbPref.setChecked(dolbyController.getReverbReductionEnabled(currentProfile))
        reverbAmountPref.value = dolbyController.getReverbReductionAmount(currentProfile)

        spkVirtPref.summary = if (isOnSpeaker) null else speakerRes
        virtualBassPref.summary = if (isOnSpeaker) {
            getString(R.string.dolby_virtual_bass_summary)
        } else {
            speakerRes
        }

        // below prefs are not enabled on loudspeaker
        if (isOnSpeaker) {
            stereoPref.summary = headphoneRes
            hpVirtPref.summary = headphoneRes
            return
        }

        val swValue = dolbyController.getStereoWideningAmount(currentProfile).toString()
        stereoPref.apply {
            if (entryValues.contains(swValue)) {
                summary = "%s"
                value = swValue
            } else {
                summary = unknownRes
            }
        }

        hpVirtPref.apply {
            setChecked(dolbyController.getHeadphoneVirtEnabled(currentProfile))
            summary = null
        }
    }

    private fun updateProfileIcon(profile: Int) {
        when (profile) {
            0 -> profilePref.setIcon(R.drawable.ic_profile_dynamic)
            1 -> profilePref.setIcon(R.drawable.ic_profile_movie)
            2 -> profilePref.setIcon(R.drawable.ic_profile_music)
            3 -> profilePref.setIcon(R.drawable.ic_profile_custom)
            else -> profilePref.setIcon(R.drawable.ic_dolby)
        }
    }

    companion object {
        private const val TAG = "DolbySettingsFragment"
        private val ATTRIBUTES_MEDIA = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .build()
    }
}
