package com.example.btreceivecall.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.btreceivecall.R
import com.example.btreceivecall.receiver.MediaButtonReceiver
import com.example.btreceivecall.ui.MainActivity

/**
 * Foreground Service that runs while a Bluetooth device is connected and Master Switch is enabled.
 * Keeps call monitoring active and manages MediaSession + AudioFocus for hardware Bluetooth button interception.
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
            instance?.acquireAudioFocusAndSilenceTrack()
        }

        fun deactivateCallFocus() {
            instance?.releaseAudioFocusAndSilenceTrack()
        }
    }

    private var mediaSession: android.media.session.MediaSession? = null
    private var dynamicMediaButtonReceiver: MediaButtonReceiver? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var silentAudioTrack: AudioTrack? = null
    @Volatile
    private var isPlayingSilence = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "BluetoothMonitoringService created.")
        createNotificationChannel()
        setupMediaSession()
        registerDynamicMediaButtonReceiver()
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
        releaseAudioFocusAndSilenceTrack()
        unregisterDynamicMediaButtonReceiver()
        try {
            mediaSession?.isActive = false
            mediaSession?.release()
            mediaSession = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing MediaSession", e)
        }
        if (instance == this) {
            instance = null
        }
        isServiceRunning = false
    }

    private fun registerDynamicMediaButtonReceiver() {
        try {
            dynamicMediaButtonReceiver = MediaButtonReceiver()
            val filter = IntentFilter(Intent.ACTION_MEDIA_BUTTON).apply {
                priority = IntentFilter.SYSTEM_HIGH_PRIORITY
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(dynamicMediaButtonReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(dynamicMediaButtonReceiver, filter)
            }
            Log.i(TAG, "Dynamically registered MediaButtonReceiver with SYSTEM_HIGH_PRIORITY.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register dynamic MediaButtonReceiver", e)
        }
    }

    private fun unregisterDynamicMediaButtonReceiver() {
        try {
            dynamicMediaButtonReceiver?.let {
                unregisterReceiver(it)
                dynamicMediaButtonReceiver = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering dynamic MediaButtonReceiver", e)
        }
    }

    private fun setupMediaSession() {
        try {
            audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager

            val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON, null, this, MediaButtonReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                mediaButtonIntent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            )

            mediaSession = android.media.session.MediaSession(this, "BTReceiveCallMediaSession").apply {
                @Suppress("deprecation")
                setFlags(
                    android.media.session.MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                    android.media.session.MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
                )

                setMediaButtonReceiver(pendingIntent)

                val stateBuilder = android.media.session.PlaybackState.Builder()
                    .setActions(
                        android.media.session.PlaybackState.ACTION_PLAY_PAUSE or
                        android.media.session.PlaybackState.ACTION_PLAY or
                        android.media.session.PlaybackState.ACTION_PAUSE or
                        android.media.session.PlaybackState.ACTION_STOP or
                        android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT or
                        android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS
                    )
                    .setState(android.media.session.PlaybackState.STATE_PLAYING, 0L, 1.0f)
                setPlaybackState(stateBuilder.build())

                setCallback(object : android.media.session.MediaSession.Callback() {
                    override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                        val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, android.view.KeyEvent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                        }
                        Log.i(TAG, ">>> MediaSession onMediaButtonEvent received: action=${event?.action}, keyCode=${event?.keyCode} <<<")
                        if (event != null && (event.action == android.view.KeyEvent.ACTION_DOWN || event.action == android.view.KeyEvent.ACTION_UP)) {
                            handleCallAnswerTrigger()
                            return true
                        }
                        return super.onMediaButtonEvent(mediaButtonIntent)
                    }

                    override fun onPlay() {
                        Log.i(TAG, ">>> MediaSession.onPlay() triggered! <<<")
                        handleCallAnswerTrigger()
                    }

                    override fun onPause() {
                        Log.i(TAG, ">>> MediaSession.onPause() triggered! <<<")
                        handleCallAnswerTrigger()
                    }

                    override fun onStop() {
                        Log.i(TAG, ">>> MediaSession.onStop() triggered! <<<")
                        handleCallAnswerTrigger()
                    }

                    override fun onSkipToNext() {
                        Log.i(TAG, ">>> MediaSession.onSkipToNext() triggered! <<<")
                        handleCallAnswerTrigger()
                    }

                    override fun onSkipToPrevious() {
                        Log.i(TAG, ">>> MediaSession.onSkipToPrevious() triggered! <<<")
                        handleCallAnswerTrigger()
                    }
                })

                isActive = true
            }
            Log.i(TAG, "MediaSession setup complete and isActive=true.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize MediaSession", e)
        }
    }

    private fun handleCallAnswerTrigger() {
        val service = CallAccessibilityService.instance
        if (service != null) {
            val answered = service.performAnswerCallAction()
            if (answered) {
                service.announceCallAnswered()
                deactivateCallFocus()
            }
        }
    }

    fun acquireAudioFocusAndSilenceTrack() {
        try {
            Log.i(TAG, "acquireAudioFocusAndSilenceTrack: Activating Bluetooth media focus for incoming call...")
            audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (audioManager == null) return

            // 1. Request Audio Focus
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

                audioManager?.requestAudioFocus(audioFocusRequest!!)
            } else {
                @Suppress("deprecation")
                audioManager?.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                )
            }

            // 2. Set MediaSession to STATE_PLAYING
            val stateBuilder = android.media.session.PlaybackState.Builder()
                .setActions(
                    android.media.session.PlaybackState.ACTION_PLAY_PAUSE or
                    android.media.session.PlaybackState.ACTION_PLAY or
                    android.media.session.PlaybackState.ACTION_PAUSE or
                    android.media.session.PlaybackState.ACTION_STOP or
                    android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT or
                    android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(android.media.session.PlaybackState.STATE_PLAYING, 0L, 1.0f)
            mediaSession?.setPlaybackState(stateBuilder.build())
            mediaSession?.isActive = true

            // 3. Play a silent inaudible AudioTrack loop to make Android AVRCP set us as the Active Player
            startSilentAudioLoop()

            Log.i(TAG, "acquireAudioFocusAndSilenceTrack: Success. Bluetooth headset buttons are now locked to our MediaSession.")
        } catch (e: Exception) {
            Log.w(TAG, "Error acquiring audio focus", e)
        }
    }

    private fun startSilentAudioLoop() {
        if (isPlayingSilence) return
        isPlayingSilence = true
        Thread {
            try {
                val sampleRate = 44100
                val minBufferSize = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val buffer = ByteArray(minBufferSize) // All zeros = pure silence

                val track = AudioTrack.Builder()
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

                silentAudioTrack = track
                track.play()

                while (isPlayingSilence && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.write(buffer, 0, buffer.size)
                    Thread.sleep(150)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Silent audio loop ended.")
            }
        }.start()
    }

    fun releaseAudioFocusAndSilenceTrack() {
        try {
            isPlayingSilence = false
            silentAudioTrack?.let {
                try {
                    it.stop()
                    it.release()
                } catch (e: Exception) {
                    // Ignore
                }
                silentAudioTrack = null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("deprecation")
                audioManager?.abandonAudioFocus(null)
            }

            val stateBuilder = android.media.session.PlaybackState.Builder()
                .setState(android.media.session.PlaybackState.STATE_PAUSED, 0L, 0.0f)
            mediaSession?.setPlaybackState(stateBuilder.build())
            Log.i(TAG, "releaseAudioFocusAndSilenceTrack: Released audio focus & silence track.")
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing audio focus", e)
        }
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

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_desc))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
