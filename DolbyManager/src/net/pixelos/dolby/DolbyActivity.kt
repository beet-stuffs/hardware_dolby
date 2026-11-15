/*
 * Copyright (C) 2023-24 Paranoid Android
 *           (C) 2026 PixelOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package net.pixelos.dolby

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.compose.rememberNavController
import net.pixelos.dolby.settings.DolbySettingsScreen
import net.pixelos.dolby.settings.DolbySettingsViewModel
import com.android.settingslib.spa.framework.compose.localNavController
import com.android.settingslib.spa.framework.theme.SettingsTheme

class DolbyActivity : ComponentActivity() {

    private val viewModel: DolbySettingsViewModel by viewModels { DolbySettingsViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SettingsTheme {
                // SPA's back button goes through the nav controller to onBackPressed.
                val navController = rememberNavController()
                CompositionLocalProvider(navController.localNavController()) {
                    DolbySettingsScreen(viewModel)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.startTracking()
    }

    override fun onResume() {
        super.onResume()
        // The QS tile and the equalizer may have changed things meanwhile.
        viewModel.refresh()
    }

    override fun onStop() {
        viewModel.stopTracking()
        super.onStop()
    }
}
