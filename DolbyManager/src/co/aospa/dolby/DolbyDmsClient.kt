/*
 * Copyright (C) 2026 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby

import android.os.Handler
import android.os.HwBinder
import android.os.HwParcel
import android.os.IHwBinder
import android.os.RemoteException
import android.util.Log
import co.aospa.dolby.DolbyConstants.Companion.dlog

/**
 * Minimal client for vendor.dolby.hardware.dms@2.0::IDms.
 *
 * Like Sony's daxService, tells the Dolby media service which output device is active so
 * it can apply the matching tuning. Only setActiveDevice is needed, so the HIDL call is
 * made directly instead of linking the generated Java interface.
 */
internal class DolbyDmsClient(private val handler: Handler) {

    private var service: IHwBinder? = null
    private var activeDevice: Int? = null
    private var sentDevice: Int? = null

    private val deathRecipient = IHwBinder.DeathRecipient {
        Log.w(TAG, "Dolby media service died")
        handler.post {
            service = null
            sentDevice = null
            handler.postDelayed(::resendActiveDevice, RECONNECT_DELAY_MS)
        }
    }

    fun setActiveDevice(device: Int) {
        activeDevice = device
        if (sentDevice == device) return
        send(device)
    }

    private fun resendActiveDevice() {
        activeDevice?.let { send(it) }
    }

    private fun send(device: Int) {
        val binder = getService() ?: return
        val request = HwParcel()
        val reply = HwParcel()
        try {
            request.writeInterfaceToken(DESCRIPTOR)
            request.writeInt32(device)
            binder.transact(TRANSACTION_SET_ACTIVE_DEVICE, request, reply, 0)
            reply.verifySuccess()
            request.releaseTemporaryStorage()
            sentDevice = device
            dlog(TAG, "setActiveDevice(0x${Integer.toHexString(device)})")
        } catch (e: RemoteException) {
            Log.e(TAG, "setActiveDevice failed", e)
            service = null
        } finally {
            reply.release()
        }
    }

    private fun getService(): IHwBinder? {
        service?.let { return it }
        return try {
            HwBinder.getService(DESCRIPTOR, "default").also {
                it.linkToDeath(deathRecipient, 0)
                service = it
            }
        } catch (e: Exception) {
            // RemoteException, or NoSuchElementException if the service isn't declared.
            Log.e(TAG, "Dolby media service not available", e)
            null
        }
    }

    companion object {
        private const val TAG = "DolbyDmsClient"
        private const val DESCRIPTOR = "vendor.dolby.hardware.dms@2.0::IDms"
        private const val TRANSACTION_SET_ACTIVE_DEVICE = 15
        private const val RECONNECT_DELAY_MS = 1000L

        // AudioSystem.DEVICE_OUT_* values, as sent by Sony's daxService.
        const val DEVICE_SPEAKER = 0x2
        const val DEVICE_WIRED_HEADSET = 0x4
        const val DEVICE_WIRED_HEADPHONE = 0x8
        const val DEVICE_BLUETOOTH_A2DP = 0x80
        const val DEVICE_BLUETOOTH_A2DP_HEADPHONES = 0x100
        const val DEVICE_BLUETOOTH_A2DP_SPEAKER = 0x200
        const val DEVICE_HDMI = 0x400
        const val DEVICE_DOCK = 0x1000
        const val DEVICE_USB = 0x4000
    }
}
