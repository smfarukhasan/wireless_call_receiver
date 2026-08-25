package com.example.btreceivecall.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.PendingIntent
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.example.btreceivecall.R
import com.example.btreceivecall.receiver.BluetoothReceiver

/**
 * Lightweight coordinator AccessibilityService.
 * Purely event-driven: Only activates on verified call notifications from CallNotificationListenerService.
 * (NO window scanning, NO automatic clicks, NO phantom gestures)
 */
class CallAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CallAccessibilitySvc"

        @Volatile
        var instance: CallAccessibilityService? = null
            private set
    }

    enum class CallState { IDLE, RINGING, ANSWERED }

    @Volatile
    var callState = CallState.IDLE
        private set

    val isCallActive: Boolean get() = callState != CallState.IDLE

    private var currentCallingPackage: String? = null
    private var activeAnswerPendingIntent: PendingIntent? = null

    private lateinit var answerExecutor: CallAnswerExecutor
    private lateinit var endExecutor: CallEndExecutor
    private lateinit var buttonManager: HeadsetButtonManager

    private val handler = Handler(Looper.getMainLooper())
    private var autoAnswerRunnable: Runnable? = null
    private var callEndedResetRunnable: Runnable? = null
    private var ringingTimeoutRunnable: Runnable? = null
    private var answerAttemptInProgress = false
    private var answerAttemptStartedElapsedMs = 0L

    // Double-press hang-up tracking
    private var lastButtonPressTimeMs = 0L

    override fun onCreate() {
        super.onCreate()
        instance = this
        answerExecutor = CallAnswerExecutor(this)
        endExecutor = CallEndExecutor(this)
        buttonManager = HeadsetButtonManager { source, eventTimeMs ->
            routePhysicalButtonPress(source, eventTimeMs)
        }

        Log.d(TAG, "CallAccessibilityService created.")
        try { BluetoothMonitoringService.startService(applicationContext) } catch (_: Exception) { }
        setupAudioModeListener()
    }

    private fun setupAudioModeListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            try {
                audioManager?.addOnModeChangedListener(ContextCompat.getMainExecutor(this)) { mode ->
                    Log.d(TAG, "Audio mode changed: $mode (state=$callState)")
                    if (mode == AudioManager.MODE_NORMAL && callState == CallState.ANSWERED) {
                        scheduleCallEndedReset()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error registering AudioModeChangedListener", e)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.flags = info.flags or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            info.eventTypes = AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.notificationTimeout = 50
            serviceInfo = info
            Log.i(TAG, "onServiceConnected: successfully configured in clean event mode.")
        } catch (e: Exception) {
            Log.w(TAG, "serviceInfo config error", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        transitionToIdle("onDestroy")
        if (instance == this) instance = null
        Log.d(TAG, "CallAccessibilityService destroyed.")
    }

    override fun onInterrupt() {
        transitionToIdle("onInterrupt")
    }

    // ─── STATE TRANSITIONS ───────────────────────────────────────────────────

    fun transitionToRinging(pkg: String, intent: PendingIntent?) {
        if (!BluetoothReceiver.isMasterSwitchOn(applicationContext)) return

        if (callState == CallState.RINGING) {
            currentCallingPackage = pkg
            if (intent != null) activeAnswerPendingIntent = intent
            cancelCallEndedReset()
            return
        }

        callState = CallState.RINGING
        currentCallingPackage = pkg
        activeAnswerPendingIntent = intent
        answerAttemptInProgress = false
        lastButtonPressTimeMs = 0L

        cancelCallEndedReset()
        cancelAutoAnswer()
        cancelRingingTimeout()
        BluetoothMonitoringService.activateCallFocus()

        // 35-second ringing timeout safety net
        ringingTimeoutRunnable = Runnable {
            if (callState == CallState.RINGING) {
                Log.d(TAG, "Ringing timeout (35s) reached. Resetting to IDLE.")
                transitionToIdle("ringing timeout")
            }
        }
        handler.postDelayed(ringingTimeoutRunnable!!, 35000L)

        val autoAnswer = BluetoothReceiver.isAutoAnswerOn(applicationContext)
        Log.i(TAG, ">>> [STEP 1 DETECTED] CALL RINGING: pkg=$pkg | Auto-Answer=$autoAnswer <<<")

        // ─── AUTO-ANSWER SCHEDULE (ONLY IF AUTO-ANSWER SWITCH IS ON) ──────────
        if (CallFilterPolicy.shouldAutoAnswer(applicationContext)) {
            val delay = CallFilterPolicy.getAutoAnswerDelayMs(applicationContext)
            Log.i(TAG, "Auto-answer scheduled in ${delay}ms")
            autoAnswerRunnable = Runnable {
                if (callState == CallState.RINGING && CallFilterPolicy.shouldAutoAnswer(applicationContext)) {
                    Log.i(TAG, "Auto-answer timer fired. Answering call...")
                    doAnswerCall()
                }
            }
            handler.postDelayed(autoAnswerRunnable!!, delay)
        } else {
            Log.i(TAG, "Auto-Answer is OFF. Waiting for user headphone button press.")
        }
    }

    private fun transitionToAnswered() {
        callState = CallState.ANSWERED
        answerAttemptInProgress = false
        lastButtonPressTimeMs = 0L
        cancelAutoAnswer()
        cancelCallEndedReset()
        cancelRingingTimeout()
        BluetoothMonitoringService.keepAnsweredCallButtonCapture()
        activeAnswerPendingIntent = null
        CallNotificationListenerService.clearAnswerPendingIntent()
        Log.i(TAG, ">>> STATE: ANSWERED (Call Active) <<<")
    }

    fun transitionToIdle(reason: String) {
        Log.i(TAG, ">>> STATE: IDLE (reason: $reason) <<<")
        callState = CallState.IDLE
        currentCallingPackage = null
        activeAnswerPendingIntent = null
        lastButtonPressTimeMs = 0L
        answerAttemptInProgress = false
        cancelAutoAnswer()
        cancelCallEndedReset()
        cancelRingingTimeout()
        BluetoothMonitoringService.deactivateCallFocus()
        CallNotificationListenerService.clearPendingIntents()
    }

    // ─── NOTIFICATION LISTENER HOOKS ─────────────────────────────────────────

    fun onNotificationAnswerIntentCaptured(intent: PendingIntent, pkg: String) {
        if (!BluetoothReceiver.isMasterSwitchOn(applicationContext)) return
        Log.i(TAG, ">>> [STEP 1] Verified call notification captured for pkg: $pkg <<<")
        transitionToRinging(pkg, intent)
    }

    fun onCallNotificationPosted(
        pkg: String,
        incomingCandidate: Boolean = false,
        ongoingCandidate: Boolean = false
    ) {
        if (!BluetoothReceiver.isMasterSwitchOn(applicationContext)) return
        when {
            callState == CallState.RINGING && answerAttemptInProgress && ongoingCandidate -> {
                Log.i(TAG, "Answer confirmed by ongoing-call notification: $pkg")
                transitionToAnswered()
                announceCallAnswered()
            }
            callState == CallState.IDLE && incomingCandidate -> {
                Log.i(TAG, ">>> [STEP 1] Incoming call inferred from notification: $pkg <<<")
                val intent = CallNotificationListenerService.latestAnswerPendingIntent
                transitionToRinging(pkg, intent)
            }
            callState == CallState.ANSWERED && currentCallingPackage == pkg -> {
                cancelCallEndedReset()
            }
        }
    }

    fun onNotificationAnswerIntentRemoved(pkg: String, allowReplacementGrace: Boolean = true) {
        if (callState != CallState.IDLE && currentCallingPackage == pkg) {
            if (callState == CallState.RINGING) {
                if (!answerAttemptInProgress) {
                    transitionToIdle("ringing notification removed")
                }
            } else if (allowReplacementGrace) {
                scheduleCallEndedReset()
            } else {
                transitionToIdle("call notification ended")
            }
        }
    }

    // ─── ACCESSIBILITY EVENTS (PASSIVE NOTIFICATION ONLY) ─────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!BluetoothReceiver.isMasterSwitchOn(applicationContext)) {
            if (callState != CallState.IDLE) transitionToIdle("master switch off")
            return
        }

        val eventPkg = event.packageName?.toString() ?: ""
        if (eventPkg !in CallAppRegistry.callPackages) return

        // Extract notification answer intent only if notification arrived via accessibility
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            extractNotificationAnswerIntent(event)
        }
    }

    private fun extractNotificationAnswerIntent(event: AccessibilityEvent) {
        try {
            val answer = NotificationCallActionParser.answerAction(event) ?: return
            Log.i(TAG, "Answer intent from accessibility notification: pkg=${answer.packageName}")
            if (callState == CallState.RINGING) {
                currentCallingPackage = answer.packageName
                activeAnswerPendingIntent = answer.intent
            } else {
                transitionToRinging(answer.packageName, answer.intent)
            }
        } catch (e: Exception) {
            Log.w(TAG, "extractNotificationAnswerIntent error", e)
        }
    }

    // ─── HARDWARE BUTTON & KEY EVENTS ─────────────────────────────────────────

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyEvent(event)
        if (event.action != KeyEvent.ACTION_DOWN || event.repeatCount != 0) return super.onKeyEvent(event)
        if (!BluetoothReceiver.isMasterSwitchOn(applicationContext)) return super.onKeyEvent(event)

        val keyCode = event.keyCode
        if (!buttonManager.isInterceptableKeyEvent(keyCode)) return super.onKeyEvent(event)

        return when (callState) {
            CallState.RINGING -> {
                Log.i(TAG, ">>> [STEP 2] Hardware key ${KeyEvent.keyCodeToString(keyCode)} pressed during RINGING <<<")
                buttonManager.onButtonEvent("Accessibility:${KeyEvent.keyCodeToString(keyCode)}", event.eventTime)
                true
            }
            CallState.ANSWERED -> {
                buttonManager.onButtonEvent("Accessibility:${KeyEvent.keyCodeToString(keyCode)}", event.eventTime)
                true
            }
            CallState.IDLE -> super.onKeyEvent(event)
        }
    }

    fun handleButtonPressFromMediaSession(eventTimeMs: Long = SystemClock.uptimeMillis()) {
        buttonManager.onButtonEvent("MediaSession", eventTimeMs)
    }

    fun handleVolumeButtonPress() {
        buttonManager.onButtonEvent("VolumeObserver", SystemClock.uptimeMillis())
    }

    private fun routePhysicalButtonPress(source: String, eventTimeMs: Long) {
        if (callState == CallState.IDLE) {
            Log.d(TAG, "Button pressed while IDLE ($source); ignored.")
            return
        }

        when (callState) {
            CallState.RINGING -> {
                if (!CallFilterPolicy.isReceivePermittedForScreenState(applicationContext)) {
                    Log.i(TAG, "Answer trigger ignored because receive switch for current screen state is OFF.")
                    return
                }
                Log.i(TAG, ">>> [STEP 2] Processing manual answer button from $source <<<")
                doAnswerCall()
            }
            CallState.ANSWERED -> handleDoublePressHangup(source)
            CallState.IDLE -> { }
        }
    }

    private fun handleDoublePressHangup(source: String) {
        val now = System.currentTimeMillis()
        val elapsed = now - lastButtonPressTimeMs
        Log.i(TAG, "Button press while ANSWERED ($source), elapsed since last=${elapsed}ms")
        if (CallControlPolicy.isDoublePress(lastButtonPressTimeMs, now)) {
            Log.i(TAG, ">>> DOUBLE-PRESS DETECTED → HANGING UP CALL <<<")
            val endIntent = CallNotificationListenerService.latestEndCallPendingIntent
            endExecutor.endCall(endIntent)
            transitionToIdle("double-press hangup")
        } else {
            lastButtonPressTimeMs = now
        }
    }

    // ─── ANSWER CALL EXECUTION ────────────────────────────────────────────────

    private fun doAnswerCall() {
        if (callState != CallState.RINGING) return

        val elapsed = SystemClock.elapsedRealtime() - answerAttemptStartedElapsedMs
        if (answerAttemptInProgress && elapsed < 300L) {
            Log.d(TAG, "Answer attempt already in progress; duplicate ignored.")
            return
        }

        answerAttemptInProgress = true
        answerAttemptStartedElapsedMs = SystemClock.elapsedRealtime()

        val pi = activeAnswerPendingIntent ?: CallNotificationListenerService.latestAnswerPendingIntent
        Log.i(TAG, ">>> [STEP 3] Executing answerCall for pkg: $currentCallingPackage (hasPendingIntent=${pi != null}) <<<")
        val success = answerExecutor.answerCall(currentCallingPackage, pi)

        if (success) {
            transitionToAnswered()
            announceCallAnswered()
        } else {
            answerAttemptInProgress = false
            Log.w(TAG, "Failed to dispatch answer action.")
        }
    }

    private fun scheduleCallEndedReset() {
        if (callEndedResetRunnable != null) return
        callEndedResetRunnable = Runnable {
            callEndedResetRunnable = null
            if (callState != CallState.IDLE) {
                Log.d(TAG, "Call ended grace period expired. Transitioning to IDLE.")
                transitionToIdle("grace period expired")
            }
        }
        handler.postDelayed(callEndedResetRunnable!!, 2000L)
    }

    private fun cancelCallEndedReset() {
        callEndedResetRunnable?.let { handler.removeCallbacks(it) }
        callEndedResetRunnable = null
    }

    private fun cancelAutoAnswer() {
        autoAnswerRunnable?.let { handler.removeCallbacks(it) }
        autoAnswerRunnable = null
    }

    private fun cancelRingingTimeout() {
        ringingTimeoutRunnable?.let { handler.removeCallbacks(it) }
        ringingTimeoutRunnable = null
    }

    fun announceCallAnswered() {
        try {
            val msg = getString(R.string.call_answered_announcement)
            @Suppress("deprecation")
            val ev = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT).apply {
                text.add(msg)
                className = CallAccessibilityService::class.java.name
                packageName = applicationContext.packageName
            }
            (getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager)
                ?.sendAccessibilityEvent(ev)
        } catch (_: Exception) { }
    }

    fun performAnswerCallAction(): Boolean {
        doAnswerCall()
        return callState == CallState.ANSWERED
    }
}
