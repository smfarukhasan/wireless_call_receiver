package com.khubsoja.wirelesscallreceiver.service

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.provider.Settings
import android.util.Log
import com.khubsoja.wirelesscallreceiver.receiver.BluetoothReceiver

/**
 * Accurately detects physical volume button presses on Bluetooth headsets (STREAM_MUSIC / STREAM_VOICE_CALL).
 * Excludes STREAM_RING and STREAM_NOTIFICATION to prevent false triggers from Android's automatic ringtone volume routing.
 */
internal class CallVolumeMonitor(
    private val context: Context,
    private val handler: Handler,
    private val audioManager: () -> AudioManager?,
    private val onButtonPress: () -> Unit
) {
    companion object {
        private const val TAG = "CallVolumeMonitor"
        // ONLY monitor STREAM_MUSIC - the actual stream changed by Bluetooth headset volume buttons.
        // STREAM_VOICE_CALL is excluded because Android's VoIP audio routing automatically
        // changes it when a call arrives, which would cause false auto-answer triggers.
        private val MONITORED_STREAMS = intArrayOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.STREAM_RING
        )
        private const val INITIAL_SETTLE_MS = 200L
    }

    private val streamVolumes = mutableMapOf<Int, Int>()
    private var readyAtMs = 0L
    var isRegistered = false
        private set

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: android.net.Uri?) {
            super.onChange(selfChange, uri)
            Log.d(TAG, "ContentObserver onChange: uri=$uri, isRegistered=$isRegistered, readyAtMs=$readyAtMs")
            if (!BluetoothReceiver.isMasterSwitchOn(context)) return

            val manager = audioManager() ?: return
            var hasRealVolumeChanged = false

            for (stream in MONITORED_STREAMS) {
                val currentVol = try {
                    manager.getStreamVolume(stream)
                } catch (_: Exception) {
                    continue
                }

                val baselineVol = streamVolumes[stream]
                Log.d(TAG, "Checking stream $stream: baseline=$baselineVol, current=$currentVol")
                if (baselineVol != null && currentVol != baselineVol) {
                    Log.i(TAG, ">>> HARDWARE VOLUME BUTTON PRESSED on stream $stream: $baselineVol -> $currentVol <<<")
                    streamVolumes[stream] = currentVol
                    if (System.currentTimeMillis() >= readyAtMs) {
                        hasRealVolumeChanged = true
                    } else {
                        Log.d(TAG, "Volume changed during initial settle window -> updated baseline to $currentVol without auto-answer")
                    }
                } else if (baselineVol == null) {
                    streamVolumes[stream] = currentVol
                }
            }

            if (hasRealVolumeChanged) {
                onButtonPress()
            }
        }

        override fun onChange(selfChange: Boolean) {
            onChange(selfChange, null)
        }
    }

    private fun updateBaselines() {
        val manager = audioManager() ?: return
        for (stream in MONITORED_STREAMS) {
            try {
                streamVolumes[stream] = manager.getStreamVolume(stream)
            } catch (_: Exception) { }
        }
    }

    fun register() {
        if (isRegistered) return
        readyAtMs = System.currentTimeMillis() + INITIAL_SETTLE_MS
        updateBaselines()

        try {
            context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
            isRegistered = true
            Log.d(TAG, "CallVolumeMonitor registered (monitored streams: MUSIC only, baseline=$streamVolumes)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register CallVolumeMonitor", e)
        }
    }

    fun settleAndResnapshot(delayMs: Long) {
        readyAtMs = System.currentTimeMillis() + delayMs
        handler.postDelayed({
            if (isRegistered) {
                updateBaselines()
            }
        }, delayMs)
    }

    fun unregister() {
        if (!isRegistered) return
        try {
            context.contentResolver.unregisterContentObserver(observer)
        } catch (_: Exception) { }
        isRegistered = false
        streamVolumes.clear()
        Log.d(TAG, "CallVolumeMonitor unregistered.")
    }
}
