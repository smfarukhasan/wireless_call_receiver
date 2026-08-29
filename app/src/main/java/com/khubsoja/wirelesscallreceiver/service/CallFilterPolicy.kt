package com.khubsoja.wirelesscallreceiver.service

import android.app.KeyguardManager
import android.content.Context
import android.util.Log
import com.khubsoja.wirelesscallreceiver.receiver.BluetoothReceiver

/**
 * Single source of truth for checking if call receiving is permitted
 * based on master switch, auto-answer, and screen lock/unlock states.
 */
object CallFilterPolicy {
    private const val TAG = "CallFilterPolicy"

    fun isScreenLocked(context: Context): Boolean {
        val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        val isScreenInteractive = pm?.isInteractive ?: true
        val isKeyguardLocked = km?.isKeyguardLocked ?: false
        return !isScreenInteractive || isKeyguardLocked
    }

    /**
     * Checks if receiving calls is allowed for the current screen state (locked vs unlocked)
     * based on user settings in SharedPreferences.
     */
    fun isReceivePermittedForScreenState(context: Context): Boolean {
        if (!BluetoothReceiver.isMasterSwitchOn(context)) {
            Log.d(TAG, "isReceivePermitted: Master switch is OFF")
            return false
        }

        val locked = isScreenLocked(context)
        val permitted = if (locked) {
            BluetoothReceiver.isReceiveLockedOn(context)
        } else {
            BluetoothReceiver.isReceiveUnlockedOn(context)
        }

        Log.d(TAG, "isReceivePermitted: locked=$locked, permitted=$permitted (lockedSwitch=${BluetoothReceiver.isReceiveLockedOn(context)}, unlockedSwitch=${BluetoothReceiver.isReceiveUnlockedOn(context)})")
        return permitted
    }

    /**
     * Determines whether auto-answer should trigger for the incoming call.
     */
    fun shouldAutoAnswer(context: Context): Boolean {
        val autoOn = BluetoothReceiver.isAutoAnswerOn(context)
        val permitted = isReceivePermittedForScreenState(context)
        Log.i(TAG, "shouldAutoAnswer: autoOn=$autoOn, permitted=$permitted")
        return autoOn && permitted
    }

    /**
     * Returns delay before auto-answering in milliseconds.
     */
    fun getAutoAnswerDelayMs(context: Context): Long {
        return if (isScreenLocked(context)) 1000L else 1500L
    }
}
