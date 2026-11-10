/*
 * Copyright (C) 2023-24 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby

import android.util.Log

class DolbyConstants {

    // Matches com.dolby.dax.DsParams in daxService 71.2.A.2.35.
    enum class DsParam(val id: Int, val length: Int = 1) {
        HEADPHONE_VIRTUALIZER(101),
        SPEAKER_VIRTUALIZER(102),
        VOLUME_LEVELER_ENABLE(103),
        IEQ_PRESET(104),
        DIALOGUE_ENHANCER_ENABLE(105),
        GEQ_ENABLE(106),
        IEQ_AMOUNT(107),
        DIALOGUE_ENHANCER_AMOUNT(108),
        DIALOGUE_ENHANCER_DUCKING(109),
        GEQ_BAND_GAINS(110, 20),
        BASS_ENHANCER_ENABLE(111),
        VIRTUAL_BASS_ENABLE(112),
        STEREO_WIDENING_AMOUNT(113),
        REVERB_REDUCTION_ENABLE(114),
        REVERB_REDUCTION_AMOUNT(115),
        VOLUME_LEVELER_AMOUNT(116);

        override fun toString(): String {
            return "${name}(${id})"
        }
    }

    // Tuning ports, matches com.dolby.dax.DsTuning.
    enum class DsPort(val id: Int) {
        INTERNAL_SPEAKER(0),
        HDMI(1),
        MIRACAST(2),
        HEADPHONE(3),
        BLUETOOTH(4),
        USB(5);

        override fun toString(): String {
            return "${name}(${id})"
        }
    }

    // Outputs with their own settings, matching the speaker and headphone endpoints of the
    // tuning. Headphones, Bluetooth and USB all use the headphone endpoint.
    enum class Output(val ports: List<DsPort>) {
        SPEAKER(listOf(DsPort.INTERNAL_SPEAKER)),
        HEADPHONE(listOf(DsPort.HEADPHONE, DsPort.BLUETOOTH, DsPort.USB));

        val prefSuffix = name.lowercase()
    }

    companion object {
        const val TAG = "Dolby"
        const val PREF_ENABLE = "dolby_enable"
        const val PREF_PROFILE = "dolby_profile"
        const val PREF_CATEGORY_SETTINGS = "dolby_category_settings"
        const val PREF_GEQ = "dolby_geq"
        const val PREF_PRESET = "dolby_preset"
        const val PREF_IEQ = "dolby_ieq"
        const val PREF_IEQ_AMOUNT = "dolby_ieq_amount"
        const val PREF_HP_VIRTUALIZER = "dolby_virtualizer"
        const val PREF_SPK_VIRTUALIZER = "dolby_spk_virtualizer"
        const val PREF_STEREO = "dolby_stereo"
        const val PREF_DIALOGUE = "dolby_dialogue"
        const val PREF_DIALOGUE_DUCKING = "dolby_dialogue_ducking"
        const val PREF_BASS = "dolby_bass"
        const val PREF_VIRTUAL_BASS = "dolby_virtual_bass"
        const val PREF_VOLUME = "dolby_volume"
        const val PREF_VOLUME_AMOUNT = "dolby_volume_amount"
        const val PREF_REVERB = "dolby_reverb"
        const val PREF_REVERB_AMOUNT = "dolby_reverb_amount"
        const val PREF_RESET = "dolby_reset"

        // Saved separately for each output, see outputPrefKey.
        val OUTPUT_SPECIFIC_PREFS = setOf(
            PREF_GEQ,
            PREF_IEQ,
            PREF_IEQ_AMOUNT,
            PREF_DIALOGUE,
            PREF_DIALOGUE_DUCKING,
            PREF_BASS,
            PREF_VIRTUAL_BASS,
            PREF_VOLUME,
            PREF_VOLUME_AMOUNT
        )

        val PROFILE_SPECIFIC_PREFS = setOf(
            PREF_PRESET,
            PREF_HP_VIRTUALIZER,
            PREF_SPK_VIRTUALIZER,
            PREF_STEREO,
            PREF_REVERB,
            PREF_REVERB_AMOUNT
        ) + OUTPUT_SPECIFIC_PREFS

        fun outputPrefKey(key: String, output: Output) = "${key}_${output.prefSuffix}"

        fun dlog(tag: String, msg: String) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(tag, msg)
            }
        }
    }
}
