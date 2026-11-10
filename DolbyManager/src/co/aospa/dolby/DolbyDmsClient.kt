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
import java.util.NoSuchElementException

/**
 * Minimal client for the vendor.dolby.hardware.dms@2.0::IDms HIDL service.
 *
 * Like Sony's daxService, tells the Dolby media service which output device is active so it can
 * apply the matching tuning. Only setActiveDevice is needed, so the hwbinder call is made directly
 * instead of linking the generated interface:
 *
 *   setActiveDevice(uint32_t device);  // transaction 15
 */
internal class DolbyDmsClient(private val handler: Handler) {

    private var service: IHwBinder? = null
    private var activeDevice: Int? = null
    private var sentDevice: Int? = null
    private var retries = 0

    private val deathRecipient = IHwBinder.DeathRecipient {
        Log.w(TAG, "Dolby media service died")
        handler.post {
            service = null
            sentDevice = null
            scheduleResend()
        }
    }

    private val resendRunnable = Runnable { activeDevice?.let { send(it) } }

    fun setActiveDevice(device: Int) {
        activeDevice = device
        if (sentDevice == device) return
        retries = 0
        send(device)
    }

    private fun send(device: Int) {
        val binder = getService()
        if (binder == null) {
            scheduleResend()
            return
        }
        val request = HwParcel()
        val reply = HwParcel()
        try {
            request.writeInterfaceToken(DESCRIPTOR)
            request.writeInt32(device)
            binder.transact(TRANSACTION_SET_ACTIVE_DEVICE, request, reply, 0 /* flags */)
            reply.verifySuccess()
            request.releaseTemporaryStorage()
            sentDevice = device
            dlog(TAG, "setActiveDevice(0x${Integer.toHexString(device)})")
        } catch (e: RemoteException) {
            Log.e(TAG, "setActiveDevice failed", e)
            service = null
            scheduleResend()
        } finally {
            reply.release()
        }
    }

    private fun scheduleResend() {
        if (retries >= MAX_RETRIES) {
            Log.e(TAG, "Dolby media service not available, giving up")
            return
        }
        retries++
        handler.removeCallbacks(resendRunnable)
        handler.postDelayed(resendRunnable, RECONNECT_DELAY_MS)
    }

    // Non-blocking: this runs on the main thread, retried later if the service isn't up yet.
    private fun getService(): IHwBinder? {
        service?.let { return it }
        return try {
            HwBinder.getService(DESCRIPTOR, SERVICE_NAME, false /* retry */)?.also {
                it.linkToDeath(deathRecipient, 0)
                service = it
            }
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to connect to the Dolby media service", e)
            null
        } catch (e: NoSuchElementException) {
            dlog(TAG, "Dolby media service not registered yet")
            null
        }
    }

    companion object {
        private const val TAG = "DolbyDmsClient"
        private const val DESCRIPTOR = "vendor.dolby.hardware.dms@2.0::IDms"
        private const val SERVICE_NAME = "default"
        private const val TRANSACTION_SET_ACTIVE_DEVICE = 15
        private const val RECONNECT_DELAY_MS = 1000L
        private const val MAX_RETRIES = 10

        // AudioSystem.DEVICE_OUT_* values, as sent by Sony's daxService.
        const val DEVICE_SPEAKER = 0x2
        const val DEVICE_WIRED_HEADSET = 0x4
        const val DEVICE_WIRED_HEADPHONE = 0x8
        const val DEVICE_BLUETOOTH_A2DP = 0x80
        const val DEVICE_HDMI = 0x400
        const val DEVICE_DOCK = 0x1000
        const val DEVICE_USB = 0x4000
    }
}
