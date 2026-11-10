/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby.preference

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceManager
import co.aospa.dolby.DolbyConstants
import co.aospa.dolby.DolbyConstants.Output

class DolbyPreferenceStore(
    private val context: Context
) : PreferenceDataStore() {

    private val defaultSharedPrefs by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    private lateinit var profileSharedPrefs: SharedPreferences

    var profile = 0
        set(value) {
            field = value
            profileSharedPrefs = context.getSharedPreferences(
                "profile_$value",
                Context.MODE_PRIVATE
            )
        }

    // Output whose settings are shown, for the output specific prefs.
    var output = Output.SPEAKER

    private fun getSharedPreferences(key: String) =
        if (DolbyConstants.PROFILE_SPECIFIC_PREFS.contains(key)) {
            profileSharedPrefs
        } else {
            defaultSharedPrefs
        }

    private fun getKey(key: String) =
        if (DolbyConstants.OUTPUT_SPECIFIC_PREFS.contains(key)) {
            DolbyConstants.outputPrefKey(key, output)
        } else {
            key
        }

    override fun putBoolean(key: String, value: Boolean) =
        getSharedPreferences(key).edit()
            .putBoolean(getKey(key), value)
            .apply()

    override fun getBoolean(key: String, defValue: Boolean) =
        getSharedPreferences(key).getBoolean(getKey(key), defValue)

    override fun putInt(key: String, value: Int) =
        getSharedPreferences(key).edit()
            .putInt(getKey(key), value)
            .apply()

    override fun getInt(key: String, defValue: Int) =
        getSharedPreferences(key).getInt(getKey(key), defValue)

    override fun putString(key: String, value: String?) =
        getSharedPreferences(key).edit()
            .putString(getKey(key), value)
            .apply()

    override fun getString(key: String, defValue: String?) =
        getSharedPreferences(key).getString(getKey(key), defValue)
}
