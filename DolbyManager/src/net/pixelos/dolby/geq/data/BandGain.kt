/*
 * Copyright (C) 2024 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package net.pixelos.dolby.geq.data

data class BandGain(
    val band: Int,
    var gain: Int = 0
)
