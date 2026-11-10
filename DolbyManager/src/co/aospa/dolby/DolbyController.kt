/*
 * Copyright (C) 2023-24 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioManager.AudioPlaybackCallback
import android.media.AudioPlaybackConfiguration
import android.media.AudioRoutesInfo
import android.media.IAudioRoutesObserver
import android.media.IAudioService
import android.os.Handler
import android.os.RemoteException
import android.os.ServiceManager
import android.util.Log
import androidx.preference.PreferenceManager
import co.aospa.dolby.DolbyConstants.Companion.dlog
import co.aospa.dolby.DolbyConstants.DsParam
import co.aospa.dolby.DolbyConstants.Output
import co.aospa.dolby.R

internal class DolbyController private constructor(
    private val context: Context
) {
    private var dolbyEffect = createEffect()
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(context.mainLooper)
    private val dmsClient = DolbyDmsClient(handler)

    // The speaker virtualizer is not supported on mono speakers.
    val isMonoSpeaker: Boolean by lazy {
        runCatching { dolbyEffect.isMonoSpeaker }
            .onFailure { Log.e(TAG, "Failed to query mono speaker", it) }
            .getOrDefault(false)
            .also { dlog(TAG, "isMonoSpeaker: $it") }
    }

    // Tell the Dolby media service which output is active, like Sony's daxService.
    private val audioRoutesObserver = object : IAudioRoutesObserver.Stub() {
        override fun dispatchAudioRoutesChanged(info: AudioRoutesInfo) {
            handler.post { updateActiveDevice(info) }
        }
    }

    // The effect is gone after audioserver restarts, recreate it and restore our settings.
    private val audioServerStateCallback = object : AudioManager.AudioServerStateCallback() {
        override fun onAudioServerUp() {
            Log.i(TAG, "audioserver restarted, restoring settings")
            handler.post {
                dolbyEffect.release()
                dolbyEffect = createEffect()
                restoreAllSettings()
            }
        }
    }

    // Restore current profile on every media session
    private val playbackCallback = object : AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
            val isPlaying = configs.any {
                it.playerState == AudioPlaybackConfiguration.PLAYER_STATE_STARTED
            }
            dlog(TAG, "onPlaybackConfigChanged: isPlaying=$isPlaying")
            if (isPlaying)
                setCurrentProfile()
        }
    }

    // Restore current profile on audio device change
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            dlog(TAG, "onAudioDevicesAdded")
            setCurrentProfile()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            dlog(TAG, "onAudioDevicesRemoved")
            setCurrentProfile()
        }
    }

    private var registerCallbacks = false
        set(value) {
            if (field == value) return
            field = value
            dlog(TAG, "setRegisterCallbacks($value)")
            if (value) {
                audioManager!!.registerAudioPlaybackCallback(playbackCallback, handler)
                audioManager.registerAudioDeviceCallback(audioDeviceCallback, handler)
            } else {
                audioManager!!.unregisterAudioPlaybackCallback(playbackCallback)
                audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
            }
        }

    var dsOn: Boolean
        get() =
            dolbyEffect.dsOn.also {
                dlog(TAG, "getDsOn: $it")
            }
        set(value) {
            dlog(TAG, "setDsOn: $value")
            checkEffect()
            dolbyEffect.dsOn = value
            registerCallbacks = value
            if (value)
                setCurrentProfile()
        }

    var profile: Int
        get() =
            dolbyEffect.profile.also {
                dlog(TAG, "getProfile: $it")
            }
        set(value) {
            dlog(TAG, "setProfile: $value")
            checkEffect()
            dolbyEffect.profile = value
        }

    init {
        dlog(TAG, "initialized")
    }

    fun onBootCompleted() {
        dlog(TAG, "onBootCompleted")
        migrateCustomProfile()
        restoreAllSettings()
        audioManager!!.setAudioServerStateCallback(context.mainExecutor, audioServerStateCallback)
        startWatchingRoutes()
    }

    private fun restoreAllSettings() {
        dlog(TAG, "restoreAllSettings")

        // Restore our main settings
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        dsOn = prefs.getBoolean(DolbyConstants.PREF_ENABLE, true)

        context.resources.getStringArray(R.array.dolby_profile_values)
            .map { it.toInt() }
            .forEach { profile ->
                // Reset dolby first to prevent it from loading bad settings
                dolbyEffect.resetProfileSpecificSettings(profile)
                // Now restore our profile-specific settings
                restoreSettings(profile)
            }

        // Finally restore the current profile.
        setCurrentProfile()
    }

    // Profile 3 used to be Custom, Sony's tuning makes it Voice and adds Custom as profile 8.
    // Move what was customized on the old profile 3 over to the new Custom profile, once.
    private fun migrateCustomProfile() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (prefs.getBoolean(PREF_CUSTOM_PROFILE_MIGRATED, false)) return
        dlog(TAG, "migrateCustomProfile")

        val oldPrefs =
            context.getSharedPreferences("profile_$OLD_CUSTOM_PROFILE", Context.MODE_PRIVATE)
        val newPrefs =
            context.getSharedPreferences("profile_$CUSTOM_PROFILE", Context.MODE_PRIVATE)
        newPrefs.edit().apply {
            oldPrefs.all.forEach { (key, value) ->
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is String -> putString(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
        }.commit()
        oldPrefs.edit().clear().commit()

        prefs.edit().apply {
            if (prefs.getString(DolbyConstants.PREF_PROFILE, null) == "$OLD_CUSTOM_PROFILE") {
                putString(DolbyConstants.PREF_PROFILE, "$CUSTOM_PROFILE")
            }
            putBoolean(PREF_CUSTOM_PROFILE_MIGRATED, true)
        }.commit()
    }

    private fun restoreSettings(profile: Int) {
        dlog(TAG, "restoreSettings(profile=$profile)")
        val prefs = context.getSharedPreferences("profile_$profile", Context.MODE_PRIVATE)
        setPreset(
            prefs.getString(DolbyConstants.PREF_PRESET, getPreset(profile))!!,
            profile
        )
        setHeadphoneVirtEnabled(
            prefs.getBoolean(DolbyConstants.PREF_HP_VIRTUALIZER, getHeadphoneVirtEnabled(profile)),
            profile
        )
        if (!isMonoSpeaker) {
            setSpeakerVirtEnabled(
                prefs.getBoolean(DolbyConstants.PREF_SPK_VIRTUALIZER, getSpeakerVirtEnabled(profile)),
                profile
            )
        }
        setStereoWideningAmount(
            prefs.getString(
                DolbyConstants.PREF_STEREO,
                getStereoWideningAmount(profile).toString()
            )!!.toInt(),
            profile
        )
        setReverbReductionEnabled(
            prefs.getBoolean(DolbyConstants.PREF_REVERB, getReverbReductionEnabled(profile)),
            profile
        )
        setReverbReductionAmount(
            prefs.getInt(DolbyConstants.PREF_REVERB_AMOUNT, getReverbReductionAmount(profile)),
            profile
        )
        Output.values().forEach { restoreOutputSettings(prefs, profile, it) }
    }

    private fun restoreOutputSettings(prefs: SharedPreferences, profile: Int, output: Output) {
        dlog(TAG, "restoreOutputSettings(profile=$profile, output=$output)")

        // Settings saved before they were split per output apply to both outputs.
        fun getString(key: String, default: Int, mapLegacy: (Int) -> Int = { it }) =
            prefs.getString(DolbyConstants.outputPrefKey(key, output), null)?.toIntOrNull()
                ?: prefs.getString(key, null)?.toIntOrNull()?.let(mapLegacy)
                ?: default

        fun getBoolean(key: String, default: Boolean): Boolean {
            val outputKey = DolbyConstants.outputPrefKey(key, output)
            return when {
                prefs.contains(outputKey) -> prefs.getBoolean(outputKey, default)
                else -> prefs.getBoolean(key, default)
            }
        }

        fun getInt(key: String, default: Int) =
            prefs.getInt(DolbyConstants.outputPrefKey(key, output), default)

        setGeqEnabled(
            getBoolean(DolbyConstants.PREF_GEQ, getGeqEnabled(output, profile)),
            output, profile
        )
        setIeqPreset(
            getString(DolbyConstants.PREF_IEQ, getIeqPreset(output, profile)) {
                legacyIeqPreset(it)
            },
            output, profile
        )
        setIeqAmount(
            getInt(DolbyConstants.PREF_IEQ_AMOUNT, getIeqAmount(output, profile)),
            output, profile
        )
        setDialogueEnhancerAmount(
            getString(DolbyConstants.PREF_DIALOGUE, getDialogueEnhancerAmount(output, profile)),
            output, profile
        )
        setDialogueEnhancerDucking(
            getInt(
                DolbyConstants.PREF_DIALOGUE_DUCKING,
                getDialogueEnhancerDucking(output, profile)
            ),
            output, profile
        )
        setBassEnhancerEnabled(
            getBoolean(DolbyConstants.PREF_BASS, getBassEnhancerEnabled(output, profile)),
            output, profile
        )
        setVirtualBassEnabled(
            getBoolean(DolbyConstants.PREF_VIRTUAL_BASS, getVirtualBassEnabled(output, profile)),
            output, profile
        )
        setVolumeLevelerEnabled(
            getBoolean(DolbyConstants.PREF_VOLUME, getVolumeLevelerEnabled(output, profile)),
            output, profile
        )
        setVolumeLevelerAmount(
            getInt(DolbyConstants.PREF_VOLUME_AMOUNT, getVolumeLevelerAmount(output, profile)),
            output, profile
        )
    }

    private fun createEffect() =
        DolbyAudioEffect(EFFECT_PRIORITY, audioSession = 0).apply {
            setDapParameterListener { param, value ->
                dlog(TAG, "onDapParameterChanged: param=$param value=$value")
            }
        }

    private fun checkEffect() {
        if (!dolbyEffect.hasControl()) {
            Log.w(TAG, "lost control, recreating effect")
            dolbyEffect.release()
            dolbyEffect = createEffect()
        }
    }

    private fun startWatchingRoutes() {
        val audioService = IAudioService.Stub.asInterface(
            ServiceManager.getService(Context.AUDIO_SERVICE)
        )
        try {
            audioService.startWatchingRoutes(audioRoutesObserver)?.let { updateActiveDevice(it) }
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to watch audio routes", e)
        }
    }

    // Same mapping as Sony's daxService (71.2): any A2DP output is reported as generic A2DP.
    private fun updateActiveDevice(info: AudioRoutesInfo) {
        val device = if (info.bluetoothName != null && audioManager!!.isBluetoothA2dpOn) {
            DolbyDmsClient.DEVICE_BLUETOOTH_A2DP
        } else {
            when (info.mainType) {
                AudioRoutesInfo.MAIN_HEADSET -> DolbyDmsClient.DEVICE_WIRED_HEADSET
                AudioRoutesInfo.MAIN_HEADPHONES -> DolbyDmsClient.DEVICE_WIRED_HEADPHONE
                AudioRoutesInfo.MAIN_DOCK_SPEAKERS -> DolbyDmsClient.DEVICE_DOCK
                AudioRoutesInfo.MAIN_HDMI -> DolbyDmsClient.DEVICE_HDMI
                AudioRoutesInfo.MAIN_USB -> DolbyDmsClient.DEVICE_USB
                else -> DolbyDmsClient.DEVICE_SPEAKER
            }
        }
        dlog(TAG, "updateActiveDevice: routes=$info device=0x${Integer.toHexString(device)}")
        dmsClient.setActiveDevice(device)
    }

    private fun setCurrentProfile() {
        dlog(TAG, "setCurrentProfile")
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        profile = prefs.getString(DolbyConstants.PREF_PROFILE, "0" /*dynamic*/)!!.toInt()
    }

    fun setDsOnAndPersist(dsOn: Boolean) {
        this.dsOn = dsOn
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(DolbyConstants.PREF_ENABLE, dsOn)
            .apply()
    }

    fun getProfileName(): String? {
        val profile = dolbyEffect.profile.toString()
        val profiles = context.resources.getStringArray(R.array.dolby_profile_values)
        val profileIndex = profiles.indexOf(profile)
        dlog(TAG, "getProfileName: profile=$profile index=$profileIndex")
        return if (profileIndex == -1) null else context.resources.getStringArray(
            R.array.dolby_profile_entries
        )[profileIndex]
    }

    fun resetProfileSpecificSettings() {
        dlog(TAG, "resetProfileSpecificSettings")
        checkEffect()
        dolbyEffect.resetProfileSpecificSettings()
        context.deleteSharedPreferences("profile_$profile")
    }

    fun getPreset(profile: Int = this.profile): String {
        val gains = dolbyEffect.getDapParameter(DsParam.GEQ_BAND_GAINS, profile)
        return gains.joinToString(separator = ",").also {
            dlog(TAG, "getPreset: $it")
        }
    }

    fun setPreset(value: String, profile: Int = this.profile) {
        dlog(TAG, "setPreset: $value")
        checkEffect()
        val gains = value.split(",")
            .map { it.toInt() }
            .toIntArray()
        dolbyEffect.setDapParameter(DsParam.GEQ_BAND_GAINS, gains, profile)
    }

    fun getPresetName(): String {
        val presets = context.resources.getStringArray(R.array.dolby_preset_values)
        val presetIndex = presets.indexOf(getPreset())
        return if (presetIndex == -1) {
            "Custom"
        } else {
            context.resources.getStringArray(
                R.array.dolby_preset_entries
            )[presetIndex]
        }
    }

    // Most settings belong to an endpoint of the tuning (speaker or headphone), so they are
    // set through the tuning ports of that output instead of whatever output is active.

    private fun getOutputParameter(param: DsParam, output: Output, profile: Int): IntArray =
        dolbyEffect.getDapParameter(param, profile, output.ports.first())

    private fun setOutputParameter(param: DsParam, values: IntArray, output: Output, profile: Int) {
        checkEffect()
        output.ports.forEach { port ->
            runCatching { dolbyEffect.setDapParameter(param, values, profile, port) }
                .onFailure { Log.e(TAG, "Failed to set $param on $port", it) }
        }
    }

    private fun getOutputBool(param: DsParam, output: Output, profile: Int) =
        getOutputParameter(param, output, profile)[0] == 1

    private fun getOutputInt(param: DsParam, output: Output, profile: Int) =
        getOutputParameter(param, output, profile)[0]

    private fun setOutputParameter(param: DsParam, value: Boolean, output: Output, profile: Int) =
        setOutputParameter(param, intArrayOf(if (value) 1 else 0), output, profile)

    private fun setOutputParameter(param: DsParam, value: Int, output: Output, profile: Int) =
        setOutputParameter(param, intArrayOf(value), output, profile)

    // The virtualizers are the virtualizer of the speaker and headphone endpoints.

    fun getHeadphoneVirtEnabled(profile: Int = this.profile) =
        getOutputBool(DsParam.HEADPHONE_VIRTUALIZER, Output.HEADPHONE, profile).also {
            dlog(TAG, "getHeadphoneVirtEnabled: $it")
        }

    fun setHeadphoneVirtEnabled(value: Boolean, profile: Int = this.profile) {
        dlog(TAG, "setHeadphoneVirtEnabled: $value")
        setOutputParameter(DsParam.HEADPHONE_VIRTUALIZER, value, Output.HEADPHONE, profile)
    }

    fun getSpeakerVirtEnabled(profile: Int = this.profile) =
        getOutputBool(DsParam.SPEAKER_VIRTUALIZER, Output.SPEAKER, profile).also {
            dlog(TAG, "getSpeakerVirtEnabled: $it")
        }

    fun setSpeakerVirtEnabled(value: Boolean, profile: Int = this.profile) {
        dlog(TAG, "setSpeakerVirtEnabled: $value")
        if (isMonoSpeaker) return
        setOutputParameter(DsParam.SPEAKER_VIRTUALIZER, value, Output.SPEAKER, profile)
    }

    // Settings of the whole profile.

    fun getStereoWideningAmount(profile: Int = this.profile) =
        dolbyEffect.getDapParameterInt(DsParam.STEREO_WIDENING_AMOUNT, profile).also {
            dlog(TAG, "getStereoWideningAmount: $it")
        }

    fun setStereoWideningAmount(value: Int, profile: Int = this.profile) {
        dlog(TAG, "setStereoWideningAmount: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.STEREO_WIDENING_AMOUNT, value, profile)
    }

    fun getReverbReductionEnabled(profile: Int = this.profile) =
        dolbyEffect.getDapParameterBool(DsParam.REVERB_REDUCTION_ENABLE, profile).also {
            dlog(TAG, "getReverbReductionEnabled: $it")
        }

    fun setReverbReductionEnabled(value: Boolean, profile: Int = this.profile) {
        dlog(TAG, "setReverbReductionEnabled: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.REVERB_REDUCTION_ENABLE, value, profile)
    }

    fun getReverbReductionAmount(profile: Int = this.profile) =
        dolbyEffect.getDapParameterInt(DsParam.REVERB_REDUCTION_AMOUNT, profile).also {
            dlog(TAG, "getReverbReductionAmount: $it")
        }

    fun setReverbReductionAmount(value: Int, profile: Int = this.profile) {
        dlog(TAG, "setReverbReductionAmount: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.REVERB_REDUCTION_AMOUNT, value, profile)
    }

    // Settings of each output.

    fun getGeqEnabled(output: Output, profile: Int = this.profile) =
        getOutputBool(DsParam.GEQ_ENABLE, output, profile).also {
            dlog(TAG, "getGeqEnabled($output): $it")
        }

    fun setGeqEnabled(value: Boolean, output: Output, profile: Int = this.profile) {
        dlog(TAG, "setGeqEnabled($output): $value")
        setOutputParameter(DsParam.GEQ_ENABLE, value, output, profile)
    }

    fun getIeqPreset(output: Output, profile: Int = this.profile) =
        getOutputInt(DsParam.IEQ_PRESET, output, profile).also {
            dlog(TAG, "getIeqPreset($output): $it")
        }

    fun setIeqPreset(value: Int, output: Output, profile: Int = this.profile) {
        dlog(TAG, "setIeqPreset($output): $value")
        setOutputParameter(DsParam.IEQ_PRESET, value, output, profile)
    }

    fun getIeqAmount(output: Output, profile: Int = this.profile) =
        getOutputInt(DsParam.IEQ_AMOUNT, output, profile).also {
            dlog(TAG, "getIeqAmount($output): $it")
        }

    fun setIeqAmount(value: Int, output: Output, profile: Int = this.profile) {
        dlog(TAG, "setIeqAmount($output): $value")
        setOutputParameter(DsParam.IEQ_AMOUNT, value, output, profile)
    }

    fun getDialogueEnhancerAmount(output: Output, profile: Int = this.profile): Int {
        val enabled = getOutputBool(DsParam.DIALOGUE_ENHANCER_ENABLE, output, profile)
        val amount = if (enabled) {
            getOutputInt(DsParam.DIALOGUE_ENHANCER_AMOUNT, output, profile)
        } else 0
        dlog(TAG, "getDialogueEnhancerAmount($output): enabled=$enabled amount=$amount")
        return amount
    }

    fun setDialogueEnhancerAmount(value: Int, output: Output, profile: Int = this.profile) {
        dlog(TAG, "setDialogueEnhancerAmount($output): $value")
        setOutputParameter(DsParam.DIALOGUE_ENHANCER_ENABLE, (value > 0), output, profile)
        setOutputParameter(DsParam.DIALOGUE_ENHANCER_AMOUNT, value, output, profile)
    }

    fun getDialogueEnhancerDucking(output: Output, profile: Int = this.profile) =
        getOutputInt(DsParam.DIALOGUE_ENHANCER_DUCKING, output, profile).also {
            dlog(TAG, "getDialogueEnhancerDucking($output): $it")
        }

    fun setDialogueEnhancerDucking(value: Int, output: Output, profile: Int = this.profile) {
        dlog(TAG, "setDialogueEnhancerDucking($output): $value")
        setOutputParameter(DsParam.DIALOGUE_ENHANCER_DUCKING, value, output, profile)
    }

    fun getBassEnhancerEnabled(output: Output, profile: Int = this.profile) =
        getOutputBool(DsParam.BASS_ENHANCER_ENABLE, output, profile).also {
            dlog(TAG, "getBassEnhancerEnabled($output): $it")
        }

    fun setBassEnhancerEnabled(value: Boolean, output: Output, profile: Int = this.profile) {
        dlog(TAG, "setBassEnhancerEnabled($output): $value")
        setOutputParameter(DsParam.BASS_ENHANCER_ENABLE, value, output, profile)
    }

    fun getVirtualBassEnabled(output: Output, profile: Int = this.profile) =
        getOutputBool(DsParam.VIRTUAL_BASS_ENABLE, output, profile).also {
            dlog(TAG, "getVirtualBassEnabled($output): $it")
        }

    fun setVirtualBassEnabled(value: Boolean, output: Output, profile: Int = this.profile) {
        dlog(TAG, "setVirtualBassEnabled($output): $value")
        setOutputParameter(DsParam.VIRTUAL_BASS_ENABLE, value, output, profile)
    }

    fun getVolumeLevelerEnabled(output: Output, profile: Int = this.profile) =
        getOutputBool(DsParam.VOLUME_LEVELER_ENABLE, output, profile).also {
            dlog(TAG, "getVolumeLevelerEnabled($output): $it")
        }

    fun setVolumeLevelerEnabled(value: Boolean, output: Output, profile: Int = this.profile) {
        dlog(TAG, "setVolumeLevelerEnabled($output): $value")
        setOutputParameter(DsParam.VOLUME_LEVELER_ENABLE, value, output, profile)
    }

    fun getVolumeLevelerAmount(output: Output, profile: Int = this.profile) =
        getOutputInt(DsParam.VOLUME_LEVELER_AMOUNT, output, profile).also {
            dlog(TAG, "getVolumeLevelerAmount($output): $it")
        }

    fun setVolumeLevelerAmount(value: Int, output: Output, profile: Int = this.profile) {
        dlog(TAG, "setVolumeLevelerAmount($output): $value")
        setOutputParameter(DsParam.VOLUME_LEVELER_AMOUNT, value, output, profile)
    }

    companion object {
        private const val TAG = "DolbyController"
        private const val EFFECT_PRIORITY = 100
        private const val OLD_CUSTOM_PROFILE = 3
        private const val CUSTOM_PROFILE = 8
        private const val PREF_CUSTOM_PROFILE_MIGRATED = "custom_profile_migrated"

        @Volatile
        private var instance: DolbyController? = null

        // The old tuning numbered the IEQ presets balanced, warm, detailed. Sony's tuning
        // numbers them detailed, balanced, warm.
        private fun legacyIeqPreset(preset: Int) =
            when (preset) {
                1 -> 2
                2 -> 3
                3 -> 1
                else -> preset
            }

        fun getInstance(context: Context) =
            instance ?: synchronized(this) {
                instance ?: DolbyController(context).also { instance = it }
            }
    }
}
