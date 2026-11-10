/*
 * Copyright (C) 2023-24 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.content.Context
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

    private fun restoreSettings(profile: Int) {
        dlog(TAG, "restoreSettings(profile=$profile)")
        val prefs = context.getSharedPreferences("profile_$profile", Context.MODE_PRIVATE)
        setPreset(
            prefs.getString(DolbyConstants.PREF_PRESET, getPreset(profile))!!,
            profile
        )
        setIeqPreset(
            prefs.getString(
                DolbyConstants.PREF_IEQ,
                getIeqPreset(profile).toString()
            )!!.toInt(),
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
        setDialogueEnhancerAmount(
            prefs.getString(
                DolbyConstants.PREF_DIALOGUE,
                getDialogueEnhancerAmount(profile).toString()
            )!!.toInt(),
            profile
        )
        setBassEnhancerEnabled(
            prefs.getBoolean(DolbyConstants.PREF_BASS, getBassEnhancerEnabled(profile)),
            profile
        )
        setVolumeLevelerEnabled(
            prefs.getBoolean(DolbyConstants.PREF_VOLUME, getVolumeLevelerEnabled(profile)),
            profile
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

    // Same mapping as Sony's daxService.
    private fun updateActiveDevice(info: AudioRoutesInfo) {
        val device = if (info.bluetoothName != null && audioManager!!.isBluetoothA2dpOn) {
            when (getBluetoothDeviceClass(info.bluetoothName)) {
                BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
                BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET ->
                    DolbyDmsClient.DEVICE_BLUETOOTH_A2DP_HEADPHONES
                BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER,
                BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER ->
                    DolbyDmsClient.DEVICE_BLUETOOTH_A2DP_SPEAKER
                else -> DolbyDmsClient.DEVICE_BLUETOOTH_A2DP
            }
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

    private fun getBluetoothDeviceClass(name: CharSequence): Int? =
        try {
            BluetoothAdapter.getDefaultAdapter()?.bondedDevices
                ?.firstOrNull { it.name == name.toString() }
                ?.bluetoothClass?.deviceClass
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to look up bluetooth device", e)
            null
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

    fun getHeadphoneVirtEnabled(profile: Int = this.profile) =
        dolbyEffect.getDapParameterBool(DsParam.HEADPHONE_VIRTUALIZER, profile).also {
            dlog(TAG, "getHeadphoneVirtEnabled: $it")
        }

    fun setHeadphoneVirtEnabled(value: Boolean, profile: Int = this.profile) {
        dlog(TAG, "setHeadphoneVirtEnabled: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.HEADPHONE_VIRTUALIZER, value, profile)
    }

    fun getSpeakerVirtEnabled(profile: Int = this.profile) =
        dolbyEffect.getDapParameterBool(DsParam.SPEAKER_VIRTUALIZER, profile).also {
            dlog(TAG, "getSpeakerVirtEnabled: $it")
        }

    fun setSpeakerVirtEnabled(value: Boolean, profile: Int = this.profile) {
        dlog(TAG, "setSpeakerVirtEnabled: $value")
        if (isMonoSpeaker) return
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.SPEAKER_VIRTUALIZER, value, profile)
    }

    fun getBassEnhancerEnabled(profile: Int = this.profile) =
        dolbyEffect.getDapParameterBool(DsParam.BASS_ENHANCER_ENABLE, profile).also {
            dlog(TAG, "getBassEnhancerEnabled: $it")
        }

    fun setBassEnhancerEnabled(value: Boolean, profile: Int = this.profile) {
        dlog(TAG, "setBassEnhancerEnabled: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.BASS_ENHANCER_ENABLE, value, profile)
    }

    fun getVolumeLevelerEnabled(profile: Int = this.profile) =
        dolbyEffect.getDapParameterBool(DsParam.VOLUME_LEVELER_ENABLE, profile).also {
            dlog(TAG, "getVolumeLevelerEnabled: $it")
        }

    fun setVolumeLevelerEnabled(value: Boolean, profile: Int = this.profile) {
        dlog(TAG, "setVolumeLevelerEnabled: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.VOLUME_LEVELER_ENABLE, value, profile)
    }

    fun getStereoWideningAmount(profile: Int = this.profile) =
        dolbyEffect.getDapParameterInt(DsParam.STEREO_WIDENING_AMOUNT, profile).also {
            dlog(TAG, "getStereoWideningAmount: $it")
        }

    fun setStereoWideningAmount(value: Int, profile: Int = this.profile) {
        dlog(TAG, "setStereoWideningAmount: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.STEREO_WIDENING_AMOUNT, value, profile)
    }

    fun getDialogueEnhancerAmount(profile: Int = this.profile): Int {
        val enabled = dolbyEffect.getDapParameterBool(DsParam.DIALOGUE_ENHANCER_ENABLE, profile)
        val amount = if (enabled) {
            dolbyEffect.getDapParameterInt(DsParam.DIALOGUE_ENHANCER_AMOUNT, profile)
        } else 0
        dlog(TAG, "getDialogueEnhancerAmount: enabled=$enabled amount=$amount")
        return amount
    }

    fun setDialogueEnhancerAmount(value: Int, profile: Int = this.profile) {
        dlog(TAG, "setDialogueEnhancerAmount: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.DIALOGUE_ENHANCER_ENABLE, (value > 0), profile)
        dolbyEffect.setDapParameter(DsParam.DIALOGUE_ENHANCER_AMOUNT, value, profile)
    }

    fun getIeqPreset(profile: Int = this.profile) =
        dolbyEffect.getDapParameterInt(DsParam.IEQ_PRESET, profile).also {
            dlog(TAG, "getIeqPreset: $it")
        }

    fun setIeqPreset(value: Int, profile: Int = this.profile) {
        dlog(TAG, "setIeqPreset: $value")
        checkEffect()
        dolbyEffect.setDapParameter(DsParam.IEQ_PRESET, value, profile)
    }

    companion object {
        private const val TAG = "DolbyController"
        private const val EFFECT_PRIORITY = 100

        @Volatile
        private var instance: DolbyController? = null

        fun getInstance(context: Context) =
            instance ?: synchronized(this) {
                instance ?: DolbyController(context).also { instance = it }
            }
    }
}
