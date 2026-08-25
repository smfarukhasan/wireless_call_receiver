package com.example.btreceivecall.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import com.example.btreceivecall.receiver.MediaButtonReceiver

/** Owns Android MediaSession setup and translates headset controls to one callback. */
internal class HeadsetMediaSessionController(
    context: Context,
    private val onButtonPress: (Long) -> Unit
) {
    companion object {
        private const val TAG = "HeadsetMediaSession"
        private const val ACTIONS = PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_STOP or PlaybackState.ACTION_SKIP_TO_NEXT or
            PlaybackState.ACTION_SKIP_TO_PREVIOUS or
            PlaybackState.ACTION_FAST_FORWARD or
            PlaybackState.ACTION_REWIND
    }

    private val session = MediaSession(context, "BTReceiveCallMediaSession")

    init {
        val mediaIntent = Intent(Intent.ACTION_MEDIA_BUTTON, null, context, MediaButtonReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            mediaIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )
        @Suppress("deprecation")
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        session.setMediaButtonReceiver(pendingIntent)
        session.setCallback(object : MediaSession.Callback() {
            override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                }
                Log.i(TAG, "Media button: action=${event?.action}, keyCode=${event?.keyCode}")
                if (event?.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    onButtonPress(event.eventTime)
                    return true
                }
                return super.onMediaButtonEvent(mediaButtonIntent)
            }

            override fun onPlay() = onTransportControl()
            override fun onPause() = onTransportControl()
            override fun onStop() = onTransportControl()
            override fun onSkipToNext() = onTransportControl()
            override fun onSkipToPrevious() = onTransportControl()
            override fun onFastForward() = onTransportControl()
            override fun onRewind() = onTransportControl()
        })
        setCapturing(false)
        Log.i(TAG, "Initialized in standby state")
    }

    fun setCapturing(capturing: Boolean) {
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(ACTIONS)
                .setState(
                    if (capturing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_NONE,
                    0L,
                    if (capturing) 1.0f else 0.0f
                )
                .build()
        )
        session.isActive = capturing
    }

    fun release() = session.release()

    private fun onTransportControl() {
        Log.i(TAG, "Transport control received")
        onButtonPress(SystemClock.uptimeMillis())
    }
}
