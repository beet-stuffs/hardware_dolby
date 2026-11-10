/*
 * Copyright (C) 2023-24 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby

import android.media.audiofx.AudioEffect
import co.aospa.dolby.DolbyConstants.Companion.dlog
import co.aospa.dolby.DolbyConstants.DsParam
import co.aospa.dolby.DolbyConstants.DsPort
import java.util.UUID

class DolbyAudioEffect(priority: Int, audioSession: Int) : AudioEffect(
    EFFECT_TYPE_NULL, EFFECT_TYPE_DAP, priority, audioSession
) {

    fun interface DapParameterListener {
        fun onDapParameterChanged(param: Int, value: Int)
    }

    var dsOn: Boolean
        get() = getIntParam(EFFECT_PARAM_ENABLE) == 1
        set(value) {
            setIntParam(EFFECT_PARAM_ENABLE, if (value) 1 else 0)
            enabled = value
        }

    var profile: Int
        get() = getIntParam(EFFECT_PARAM_PROFILE)
        set(value) {
            setIntParam(EFFECT_PARAM_PROFILE, value)
        }

    val numOfProfiles: Int
        get() = getIntParam(EFFECT_PARAM_NUM_PROFILES)

    val numOfIeqPresets: Int
        get() = getIntParam(EFFECT_PARAM_NUM_IEQ_PRESETS)

    // The speaker virtualizer can't be used on mono speakers.
    val isMonoSpeaker: Boolean
        get() = getIntParam(EFFECT_PARAM_MONO_SPEAKER) > 0

    private fun setIntParam(param: Int, value: Int) {
        dlog(TAG, "setIntParam($param, $value)")
        val buf = ByteArray(12)
        int32ToByteArray(param, buf, 0)
        int32ToByteArray(1, buf, 4)
        int32ToByteArray(value, buf, 8)
        checkStatus(setParameter(EFFECT_PARAM_CPDP_VALUES, buf))
    }

    private fun getIntParam(param: Int): Int {
        val buf = ByteArray(12)
        int32ToByteArray(param, buf, 0)
        checkStatus(getParameter(EFFECT_PARAM_CPDP_VALUES + param, buf))
        return byteArrayToInt32(buf).also {
            dlog(TAG, "getIntParam($param): $it")
        }
    }

    fun resetProfileSpecificSettings(profile: Int = this.profile) {
        dlog(TAG, "resetProfileSpecificSettings: profile=$profile")
        setIntParam(EFFECT_PARAM_RESET_PROFILE_SETTINGS, profile)
    }

    fun isProfileSpecificSettingsModified(profile: Int = this.profile): Boolean {
        val buf = ByteArray(12)
        checkStatus(getParameter(EFFECT_PARAM_PROFILE_MODIFIED + (profile shl 16), buf))
        return (byteArrayToInt32(buf) > 0).also {
            dlog(TAG, "isProfileSpecificSettingsModified($profile): $it")
        }
    }

    // Parameters of the current output device.

    fun setDapParameter(param: DsParam, values: IntArray, profile: Int = this.profile) {
        dlog(TAG, "setDapParameter: profile=$profile param=$param")
        val length = values.size
        val buf = ByteArray((length + 4) * 4)
        int32ToByteArray(EFFECT_PARAM_SET_PROFILE_PARAMETER, buf, 0)
        int32ToByteArray(length + 1, buf, 4)
        int32ToByteArray(profile, buf, 8)
        int32ToByteArray(param.id, buf, 12)
        int32ArrayToByteArray(values, buf, 16)
        checkStatus(setParameter(EFFECT_PARAM_CPDP_VALUES, buf))
    }

    fun setDapParameter(param: DsParam, enable: Boolean, profile: Int = this.profile) =
        setDapParameter(param, intArrayOf(if (enable) 1 else 0), profile)

    fun setDapParameter(param: DsParam, value: Int, profile: Int = this.profile) =
        setDapParameter(param, intArrayOf(value), profile)

    fun getDapParameter(param: DsParam, profile: Int = this.profile): IntArray {
        dlog(TAG, "getDapParameter: profile=$profile param=$param")
        val length = param.length
        val buf = ByteArray((length + 2) * 4)
        val p = (param.id shl 16) + (profile shl 8) + EFFECT_PARAM_GET_PROFILE_PARAMETER
        checkStatus(getParameter(p, buf))
        return byteArrayToInt32Array(buf, length)
    }

    fun getDapParameterBool(param: DsParam, profile: Int = this.profile): Boolean =
        getDapParameter(param, profile)[0] == 1

    fun getDapParameterInt(param: DsParam, profile: Int = this.profile): Int =
        getDapParameter(param, profile)[0]

    // Parameters of a specific tuning port, used regardless of the current output device.

    fun setDapParameter(param: DsParam, values: IntArray, profile: Int, port: DsPort) {
        dlog(TAG, "setDapParameter: profile=$profile port=$port param=$param")
        val length = values.size
        val buf = ByteArray((length + 5) * 4)
        int32ToByteArray(EFFECT_PARAM_SET_PORT_PARAMETER, buf, 0)
        int32ToByteArray(length + 1, buf, 4)
        int32ToByteArray(profile, buf, 8)
        int32ToByteArray(port.id, buf, 12)
        int32ToByteArray(param.id, buf, 16)
        int32ArrayToByteArray(values, buf, 20)
        checkStatus(setParameter(EFFECT_PARAM_CPDP_VALUES, buf))
    }

    fun getDapParameter(param: DsParam, profile: Int, port: DsPort): IntArray {
        dlog(TAG, "getDapParameter: profile=$profile port=$port param=$param")
        val length = param.length
        val buf = ByteArray((length + 2) * 4)
        val p = (param.id shl 16) + (profile shl 12) + (port.id shl 8) +
                EFFECT_PARAM_GET_PORT_PARAMETER
        checkStatus(getParameter(p, buf))
        return byteArrayToInt32Array(buf, length)
    }

    // Notifies about parameter changes made by other clients of the effect.
    fun setDapParameterListener(listener: DapParameterListener?) {
        setParameterListener(listener?.let {
            OnParameterChangeListener { _, _, param, value ->
                if (param.size == 4 && value.size == 2) {
                    it.onDapParameterChanged(byteArrayToInt32(param), byteArrayToInt16(value))
                }
            }
        })
    }

    companion object {
        private const val TAG = "DolbyAudioEffect"
        private val EFFECT_TYPE_DAP =
            UUID.fromString("9d4921da-8225-4f29-aefa-39537a04bcaa")

        private const val EFFECT_PARAM_ENABLE = 0
        private const val EFFECT_PARAM_CPDP_VALUES = 5
        private const val EFFECT_PARAM_SET_PROFILE_PARAMETER = 0x1000000
        private const val EFFECT_PARAM_GET_PROFILE_PARAMETER = 0x1000005
        private const val EFFECT_PARAM_SET_PORT_PARAMETER = 0x2000000
        private const val EFFECT_PARAM_GET_PORT_PARAMETER = 0x2000005
        private const val EFFECT_PARAM_NUM_PROFILES = 0x3000000
        private const val EFFECT_PARAM_NUM_IEQ_PRESETS = 0x4000000
        private const val EFFECT_PARAM_PROFILE = 0xA000000
        private const val EFFECT_PARAM_PROFILE_MODIFIED = 0xB000005
        private const val EFFECT_PARAM_RESET_PROFILE_SETTINGS = 0xC000000
        private const val EFFECT_PARAM_MONO_SPEAKER = 0xD000000

        private fun int32ToByteArray(value: Int, dst: ByteArray, index: Int) {
            var idx = index
            dst[idx++] = (value and 0xff).toByte()
            dst[idx++] = ((value ushr 8) and 0xff).toByte()
            dst[idx++] = ((value ushr 16) and 0xff).toByte()
            dst[idx] = ((value ushr 24) and 0xff).toByte()
        }

        private fun byteArrayToInt32(ba: ByteArray): Int {
            return ((ba[3].toInt() and 0xff) shl 24) or
                    ((ba[2].toInt() and 0xff) shl 16) or
                    ((ba[1].toInt() and 0xff) shl 8) or
                    (ba[0].toInt() and 0xff)
        }

        private fun byteArrayToInt16(ba: ByteArray): Int {
            return (((ba[1].toInt() and 0xff) shl 8) or (ba[0].toInt() and 0xff)).toShort().toInt()
        }

        private fun int32ArrayToByteArray(src: IntArray, dst: ByteArray, index: Int) {
            var idx = index
            for (x in src) {
                dst[idx++] = (x and 0xff).toByte()
                dst[idx++] = ((x ushr 8) and 0xff).toByte()
                dst[idx++] = ((x ushr 16) and 0xff).toByte()
                dst[idx++] = ((x ushr 24) and 0xff).toByte()
            }
        }

        private fun byteArrayToInt32Array(ba: ByteArray, dstLength: Int): IntArray {
            val srcLength = ba.size shr 2
            val dst = IntArray(dstLength.coerceAtMost(srcLength))
            for (i in dst.indices) {
                dst[i] = ((ba[i * 4 + 3].toInt() and 0xff) shl 24) or
                        ((ba[i * 4 + 2].toInt() and 0xff) shl 16) or
                        ((ba[i * 4 + 1].toInt() and 0xff) shl 8) or
                        (ba[i * 4].toInt() and 0xff)
            }
            return dst
        }
    }
}
