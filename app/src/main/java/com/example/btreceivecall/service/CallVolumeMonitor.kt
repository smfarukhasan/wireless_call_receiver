package com.example.btreceivecall.service

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.provider.Settings
import android.util.Log
import com.example.btreceivecall.receiver.BluetoothReceiver

/**
 * Detects real hardware volume button adjustments on STREAM_MUSIC (Bluetooth headset volume key).
 * Uses a settling period upon incoming call startup to prevent false triggers from VoIP audio routing.
 */
internal class CallVolumeMonitor(
    private val context: Context,
    private val handler: Handler,
    private val audioManager: () -> AudioManager?,
    private val onButtonPress: () -> Unit
) {
    companion object {
        private const val TAG = "CallVolumeMonitor"
    }

    private var musicVolume = -1
    private var readyAtMs = 0L
    var isRegistered = false
        private set

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            if (System.currentTimeMillis() < readyAtMs) {
                Log.d(TAG, "Volume change ignored: settling period active.")
                return
            }
            if (!BluetoothReceiver.isMasterSwitchOn(context)) return

            val manager = audioManager() ?: return
            val currentMusic = manager.getStreamVolume(AudioManager.STREAM_MUSIC)

            if (musicVolume != -1 && currentMusic != musicVolume) {
                Log.i(TAG, "Real volume button adjustment detected: music $musicVolume -> $currentMusic")
                musicVolume = currentMusic
                onButtonPress()
            } else if (musicVolume == -1) {
                musicVolume = currentMusic
            }
        }
    }

    fun register(settleMs: Long = 1200L) {
        if (isRegistered) return
        val manager = audioManager() ?: return
        readyAtMs = System.currentTimeMillis() + settleMs
        musicVolume = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        try {
            context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
            isRegistered = true
            handler.postDelayed({
                if (isRegistered) {
                    musicVolume = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    Log.d(TAG, "Volume baseline established after settling: musicVolume=$musicVolume")
                }
            }, settleMs)
            Log.d(TAG, "CallVolumeMonitor registered; settling for ${settleMs}ms")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register CallVolumeMonitor", e)
        }
    }

    fun settleAndResnapshot(delayMs: Long) {
        val manager = audioManager() ?: return
        readyAtMs = System.currentTimeMillis() + delayMs
        handler.postDelayed({
            if (isRegistered) {
                musicVolume = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
        }, delayMs)
    }

    fun unregister() {
        if (!isRegistered) return
        try {
            context.contentResolver.unregisterContentObserver(observer)
        } catch (_: Exception) { }
        isRegistered = false
        musicVolume = -1
        Log.d(TAG, "CallVolumeMonitor unregistered.")
    }
}
