package com.example.btreceivecall.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.btreceivecall.R
import com.example.btreceivecall.ui.MainActivity

/**
 * Lightweight Foreground Service active while a Bluetooth device is connected and Master Switch is enabled.
 * Coordinates MediaSession and AudioFocus for Bluetooth headphone button interception.
 */
class BluetoothMonitoringService : Service() {

    companion object {
        private const val TAG = "BTMonitoringService"
        private const val CHANNEL_ID = "bt_receive_call_channel"
        private const val NOTIFICATION_ID = 1001

        @Volatile
        var instance: BluetoothMonitoringService? = null
            private set

        var isServiceRunning = false
            private set

        fun startService(context: Context) {
            val intent = Intent(context, BluetoothMonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, BluetoothMonitoringService::class.java)
            context.stopService(intent)
        }

        fun activateCallFocus() {
            instance?.acquireCallFocus()
        }

        fun deactivateCallFocus() {
            instance?.releaseCallFocus()
        }

        fun keepAnsweredCallButtonCapture() {
            instance?.keepAnsweredButtons()
        }
    }

    private lateinit var mediaSessionController: HeadsetMediaSessionController
    private lateinit var volumeMonitor: CallVolumeMonitor
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var silentAudioTrack: AudioTrack? = null
    @Volatile private var isPlayingSilence = false

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        instance = this
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        volumeMonitor = CallVolumeMonitor(this, mainHandler, { audioManager }) {
            CallAccessibilityService.instance?.handleVolumeButtonPress()
        }

        setupMediaSession()
        createNotificationChannel()
        Log.d(TAG, "BluetoothMonitoringService created.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "BluetoothMonitoringService started.")
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        isServiceRunning = true
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BluetoothMonitoringService destroyed.")
        releaseCallFocus()
        try {
            mediaSessionController.setCapturing(false)
            mediaSessionController.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing MediaSession", e)
        }
        if (instance === this) {
            instance = null
        }
        isServiceRunning = false
    }

    private fun setupMediaSession() {
        try {
            mediaSessionController = HeadsetMediaSessionController(this) { eventTimeMs ->
                val service = CallAccessibilityService.instance
                if (service != null && service.isCallActive) {
                    Log.i(TAG, "MediaSession event during active call -> routing to CallAccessibilityService")
                    service.handleButtonPressFromMediaSession(eventTimeMs)
                }
            }
            Log.i(TAG, "MediaSession initialized in standby mode.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize MediaSession", e)
        }
    }

    private fun acquireCallFocus() {
        try {
            Log.i(TAG, "Acquiring Bluetooth media focus for incoming call...")
            val manager = audioManager ?: return

            // Register volume observer
            volumeMonitor.register()

            // Request Audio Focus
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { focusChange ->
                        Log.d(TAG, "Audio focus changed: $focusChange")
                    }
                    .build()

                manager.requestAudioFocus(audioFocusRequest!!)
            } else {
                @Suppress("deprecation")
                manager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }

            // Lock MediaSession to capturing
            mediaSessionController.setCapturing(true)

            // Start silent audio loop to anchor AVRCP routing
            startSilentAudioLoop()
            Log.i(TAG, "Bluetooth headset buttons locked to MediaSession.")
        } catch (e: Exception) {
            Log.w(TAG, "Error acquiring audio focus", e)
        }
    }

    private fun keepAnsweredButtons() {
        try {
            releaseSilentAudioAndFocus()
            volumeMonitor.settleAndResnapshot(700L)
            mediaSessionController.setCapturing(true)
            Log.i(TAG, "Answered-call button capture retained; ringing audio focus released.")
        } catch (e: Exception) {
            Log.w(TAG, "Error switching to answered-call button capture", e)
        }
    }

    private fun releaseCallFocus() {
        try {
            releaseSilentAudioAndFocus()
            volumeMonitor.unregister()
            mediaSessionController.setCapturing(false)
            Log.i(TAG, "Released audio focus & silence track.")
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing audio focus", e)
        }
    }

    private fun releaseSilentAudioAndFocus() {
        isPlayingSilence = false
        silentAudioTrack?.let {
            try {
                it.stop()
                it.release()
            } catch (_: Exception) { }
        }
        silentAudioTrack = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("deprecation")
            audioManager?.abandonAudioFocus(null)
        }
    }

    private fun startSilentAudioLoop() {
        if (isPlayingSilence) return
        isPlayingSilence = true

        Thread({
            var track: AudioTrack? = null
            try {
                val sampleRate = 44100
                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val buffer = ByteArray(minBufferSize) // All zeros = silence

                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                if (!isPlayingSilence) return@Thread
                silentAudioTrack = track
                track.play()

                while (isPlayingSilence && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.write(buffer, 0, buffer.size)
                    Thread.sleep(200)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Silent audio loop ended: ${e.message}")
            } finally {
                track?.let {
                    try { it.stop() } catch (_: Exception) { }
                    try { it.release() } catch (_: Exception) { }
                    if (silentAudioTrack === it) silentAudioTrack = null
                }
            }
        }, "BTSilentAudioThread").start()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val largeIcon = try {
            BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)
        } catch (_: Exception) {
            null
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_desc))
            .setSmallIcon(R.drawable.ic_stat_call)
            .setColor(ContextCompat.getColor(this, R.color.accent_blue))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        largeIcon?.let {
            builder.setLargeIcon(it)
        }

        return builder.build()
    }
}
