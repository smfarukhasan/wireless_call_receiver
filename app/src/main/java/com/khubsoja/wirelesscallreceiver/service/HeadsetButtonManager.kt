package com.khubsoja.wirelesscallreceiver.service

import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent

/**
 * Coordinates and deduplicates physical button presses from:
 * 1. Bluetooth MediaSession (Play/Pause, Headset Hook, Call, Next/Prev)
 * 2. Volume ContentObserver (Physical volume buttons)
 * 3. Accessibility onKeyEvent (Hardware volume & media keys)
 */
class HeadsetButtonManager(
    private val onValidButtonPressed: (source: String, eventTimeMs: Long) -> Unit
) {
    companion object {
        private const val TAG = "HeadsetButtonManager"
        private const val DEDUPLICATION_WINDOW_MS = 280L
    }

    private var lastButtonEventTimeMs = 0L

    fun onButtonEvent(source: String, eventTimeMs: Long = SystemClock.uptimeMillis()) {
        val delta = eventTimeMs - lastButtonEventTimeMs
        if (delta in 1 until DEDUPLICATION_WINDOW_MS) {
            Log.d(TAG, "Duplicate button ignored from $source (delta=${delta}ms)")
            return
        }
        lastButtonEventTimeMs = eventTimeMs
        Log.i(TAG, ">>> VALID BUTTON PRESSED from $source <<<")
        onValidButtonPressed(source, eventTimeMs)
    }

    fun isInterceptableKeyEvent(keyCode: Int): Boolean {
        return KeyEvent.isMediaSessionKey(keyCode) ||
                keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                keyCode == KeyEvent.KEYCODE_CALL ||
                keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                keyCode == KeyEvent.KEYCODE_VOLUME_MUTE ||
                keyCode == KeyEvent.KEYCODE_MUTE ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
                keyCode == KeyEvent.KEYCODE_MEDIA_STOP ||
                keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS ||
                keyCode == KeyEvent.KEYCODE_VOICE_ASSIST ||
                keyCode == KeyEvent.KEYCODE_ASSIST
    }
}
