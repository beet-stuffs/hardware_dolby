/*
 * Copyright (C) 2024 Paranoid Android
 *           (C) 2026 PixelOS
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package net.pixelos.dolby.geq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.compose.rememberNavController
import net.pixelos.dolby.geq.ui.EqualizerScreen
import net.pixelos.dolby.geq.ui.EqualizerViewModel
import com.android.settingslib.spa.framework.compose.localNavController
import com.android.settingslib.spa.framework.theme.SettingsTheme

class EqualizerActivity : ComponentActivity() {

    private val viewModel: EqualizerViewModel by viewModels { EqualizerViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            SettingsTheme {
                // SPA's back button goes through the nav controller to onBackPressed.
                val navController = rememberNavController()
                CompositionLocalProvider(navController.localNavController()) {
                    EqualizerScreen(viewModel = viewModel)
                }
            }
        }
    }
}
