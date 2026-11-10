/*
 * Copyright (C) 2026 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dolby

import android.os.Handler
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.os.ServiceManager
import android.util.Log
import co.aospa.dolby.DolbyConstants.Companion.dlog

/**
 * Minimal client for the vendor.dolby.dms.IDms AIDL service.
 *
 * Like Sony's daxService, tells the Dolby media service which output device is active so it can
 * apply the matching tuning. Only setActiveDevice is needed, so the binder call is made directly
 * instead of linking the generated interface:
 *
 *   oneway void sendCommand(int command, in byte[] data);  // transaction 1
 *
 * with command 'SADV' and the device as a marshalled Parcel int.
 */
internal class DolbyDmsClient(private val handler: Handler) {

    private var service: IBinder? = null
    private var activeDevice: Int? = null
    private var sentDevice: Int? = null
    private var retries = 0

    private val deathRecipient = IBinder.DeathRecipient {
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
        val payload = Parcel.obtain()
        val data = Parcel.obtain()
        try {
            payload.writeInt(device)
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(COMMAND_SET_ACTIVE_DEVICE)
            data.writeByteArray(payload.marshall())
            binder.transact(TRANSACTION_SEND_COMMAND, data, null, IBinder.FLAG_ONEWAY)
            sentDevice = device
            dlog(TAG, "setActiveDevice(0x${Integer.toHexString(device)})")
        } catch (e: RemoteException) {
            Log.e(TAG, "setActiveDevice failed", e)
            service = null
            scheduleResend()
        } finally {
            data.recycle()
            payload.recycle()
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
    private fun getService(): IBinder? {
        service?.let { return it }
        return try {
            ServiceManager.checkService(SERVICE_NAME)?.also {
                it.linkToDeath(deathRecipient, 0)
                service = it
            }
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to connect to the Dolby media service", e)
            null
        }
    }

    companion object {
        private const val TAG = "DolbyDmsClient"
        private const val DESCRIPTOR = "vendor.dolby.dms.IDms"
        private const val SERVICE_NAME = "$DESCRIPTOR/default"
        private const val TRANSACTION_SEND_COMMAND = IBinder.FIRST_CALL_TRANSACTION
        private const val COMMAND_SET_ACTIVE_DEVICE = 0x53414456 // 'SADV'
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
