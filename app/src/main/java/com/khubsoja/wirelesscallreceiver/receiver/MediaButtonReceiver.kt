package com.khubsoja.wirelesscallreceiver.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import com.khubsoja.wirelesscallreceiver.service.CallAccessibilityService

/**
 * BroadcastReceiver for hardware Media Button intents sent by Bluetooth Headsets.
 * High priority intent filter intercepts media key events even when Accessibility Service key filtering is bypassed by AudioPolicy.
 */
class MediaButtonReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MediaButtonReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        if (!BluetoothReceiver.isMasterSwitchOn(context)) {
            Log.d(TAG, "Master switch is off. Ignoring media button intent.")
            return
        }

        if (intent.action == Intent.ACTION_MEDIA_BUTTON) {
            val event = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }

            val service = CallAccessibilityService.instance
            if (service != null && service.isCallActive &&
                event?.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                Log.i(TAG, ">>> MEDIA BUTTON INTENT RECEIVED during active call: action=${event?.action}, keyCode=${event?.keyCode} <<<")
                service.handleButtonPressFromMediaSession(event.eventTime)
                try {
                    abortBroadcast()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not abort broadcast", e)
                }
            } else {
                Log.d(TAG, "Media button received but no call is active. Ignoring to prevent unwanted screen interaction.")
            }
        }
    }
}
