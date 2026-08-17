package com.example.btreceivecall.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.database.ContentObserver
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.btreceivecall.R
import com.example.btreceivecall.receiver.BluetoothReceiver

/**
 * Hardened AccessibilityService for visually impaired and hands-free users.
 * Supports standard Phone calls, WhatsApp, Facebook Messenger, Telegram, IMO, and other VoIP calls
 * in all states: Locked, Unlocked, In-App, and Heads-Up Notification Popups.
 */
class CallAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CallAccessibilitySvc"

        @Volatile
        var instance: CallAccessibilityService? = null
            private set

        // Whitelisted call interface packages including SystemUI for heads-up notifications
        private val CALL_PACKAGES = setOf(
            "com.google.android.dialer",
            "com.samsung.android.incallui",
            "com.samsung.android.dialer",
            "com.android.dialer",
            "com.android.incallui",
            "com.miui.incallui",
            "com.coloros.incallui",
            "com.vivo.incallui",
            "com.android.systemui",
            "com.android.phone",
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.facebook.orca",
            "com.facebook.katana",
            "com.facebook.mlite",
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "com.viber.voip",
            "com.imo.android.imoim",
            "com.imo.android.imoimhd",
            "com.imo.android.imoimbeta",
            "com.imo.android.imoimlite"
        )

        // System cellular dialer packages — TelecomManager.acceptRingingCall() ONLY works for these
        private val SYSTEM_DIALER_PACKAGES = setOf(
            "com.google.android.dialer",
            "com.samsung.android.incallui",
            "com.samsung.android.dialer",
            "com.android.dialer",
            "com.android.incallui",
            "com.miui.incallui",
            "com.coloros.incallui",
            "com.vivo.incallui",
            "com.android.phone"
        )

        // Our own package name — MUST be excluded from node matching to prevent false positives
        private const val OWN_PACKAGE = "com.example.btreceivecall"

        // Strict answer keywords — covers Full-screen, Heads-up Notification buttons, and localized UIs
        private val STRICT_ANSWER_KEYWORDS = listOf(
            "answer", "accept", "join", "pick up",
            "answer call", "accept call", "join call", "answer the call",
            "swipe to answer", "drag to answer", "swipe up to answer", "slide to answer",
            "কল ধরুন", "উত্তর দিন", "কল গ্রহণ করুন", "গ্রহণ করুন", "কল জয়েন করুন", "কল উত্তর দিন",
            "responder", "aceptar", "aceptar llamada", "répondre", "décrocher",
            "कॉल स्वीकार करें", "कॉल उठाएं", "উত্তর दें", "swipen zum antworten", "annehmen"
        )

        // Decline/Reject keywords to ALWAYS exclude from answer matching
        private val EXCLUDE_KEYWORDS = listOf(
            "decline", "reject", "dismiss", "ignore", "hangup", "hang up", "end call", "cancel",
            "message", "reply", "remind",
            "বাতিল", "রিমুভ", "প্রত্যাখ্যান", "কাটুন", "বাতিল করুন"
        )

        // Resource ID patterns — covers full-screen buttons, heads-up action buttons, and vendor VoIP layouts
        private val ANSWER_RESOURCE_IDS = listOf(
            "btn_answer", "answer_button", "call_card_answer",
            "swipe_to_answer", "incoming_call_answer", "pickup", "call_accept",
            "voip_answer_button", "voip_accept_button", "call_answer",
            "voice_call_answer", "video_call_answer", "accept_incoming_call",
            "answer_btn", "accept_btn", "call_accept_btn", "call_control_answer_btn",
            "iv_accept", "btn_accept", "audio_call_accept", "video_call_accept",
            "imo_answer", "imo_accept", "call_action", "rtc_incoming_call_answer_button"
        )
    }

    @Volatile
    var isCallActive = false
        private set

    @Volatile
    private var activeAnswerPendingIntent: PendingIntent? = null

    private var currentCallingPackage: String? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var autoAnswerRunnable: Runnable? = null
    private var callEndedResetRunnable: Runnable? = null

    private var callStartTimeMs = 0L
    private var isVolumeObserverRegistered = false
    private val volumeObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            val elapsed = System.currentTimeMillis() - callStartTimeMs
            // Ignore volume changes during first 800ms to allow ringtone initialization to settle
            if (elapsed < 800L) {
                Log.d(TAG, "Volume change ignored during ringtone startup settling ($elapsed ms).")
                return
            }
            if (isCallActive && BluetoothReceiver.isMasterSwitchOn(applicationContext)) {
                Log.i(TAG, ">>> HARDWARE VOLUME BUTTON CLICK INTERCEPTED! ($elapsed ms after ring) Answering call... <<<")
                val answered = performAnswerCallAction()
                if (answered) {
                    announceCallAnswered()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "CallAccessibilityService created.")

        try {
            BluetoothMonitoringService.startService(applicationContext)
            Log.i(TAG, "BluetoothMonitoringService auto-started from AccessibilityService.")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to auto-start BluetoothMonitoringService", e)
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
            info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC or AccessibilityServiceInfo.FEEDBACK_SPOKEN
            info.notificationTimeout = 50
            serviceInfo = info
            Log.i(TAG, "onServiceConnected: AccessibilityServiceInfo configured with interactive windows & key filtering flags.")
        } catch (e: Exception) {
            Log.w(TAG, "Error configuring serviceInfo in onServiceConnected", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelCallEndedReset()
        unregisterVolumeObserver()
        cancelAutoAnswer()
        activeAnswerPendingIntent = null
        if (instance == this) {
            instance = null
        }
        Log.d(TAG, "CallAccessibilityService destroyed.")
    }

    fun onNotificationAnswerIntentCaptured(intent: PendingIntent, pkg: String) {
        Log.i(TAG, ">>> Received Native Call Answer Intent for package: $pkg <<<")
        activeAnswerPendingIntent = intent
        currentCallingPackage = pkg
        isCallActive = true
        cancelCallEndedReset()
        registerVolumeObserver()
        BluetoothMonitoringService.activateCallFocus()
        if (BluetoothReceiver.isAutoAnswerOn(applicationContext)) {
            scheduleAutoAnswer()
        }
    }

    fun onNotificationAnswerIntentRemoved(pkg: String) {
        if (currentCallingPackage == pkg || activeAnswerPendingIntent != null) {
            Log.i(TAG, "Notification for calling package $pkg was removed. Resetting call state.")
            isCallActive = false
            cancelCallEndedReset()
            cancelAutoAnswer()
            unregisterVolumeObserver()
            BluetoothMonitoringService.deactivateCallFocus()
            currentCallingPackage = null
            activeAnswerPendingIntent = null
        }
    }

    private fun registerVolumeObserver() {
        if (!isVolumeObserverRegistered) {
            try {
                callStartTimeMs = System.currentTimeMillis()
                applicationContext.contentResolver.registerContentObserver(
                    Settings.System.CONTENT_URI,
                    true,
                    volumeObserver
                )
                isVolumeObserverRegistered = true
                Log.d(TAG, "VolumeObserver registered for hardware volume button detection (callStartTimeMs=$callStartTimeMs).")
            } catch (e: Exception) {
                Log.w(TAG, "Error registering VolumeObserver", e)
            }
        }
    }

    private fun unregisterVolumeObserver() {
        if (isVolumeObserverRegistered) {
            try {
                applicationContext.contentResolver.unregisterContentObserver(volumeObserver)
                isVolumeObserverRegistered = false
                Log.d(TAG, "VolumeObserver unregistered.")
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering VolumeObserver", e)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (!BluetoothReceiver.isMasterSwitchOn(applicationContext)) {
            isCallActive = false
            cancelCallEndedReset()
            cancelAutoAnswer()
            unregisterVolumeObserver()
            activeAnswerPendingIntent = null
            return
        }

        val eventPkg = event.packageName?.toString() ?: ""
        if (eventPkg == OWN_PACKAGE) return

        // 1. Extract Notification Action PendingIntent if event carries notification data
        if (event.parcelableData is Notification) {
            extractNotificationAnswerIntent(event)
        }

        // Only scan windows if event is from a verified call app, or we have an active notification intent, or call is currently active
        if (!CALL_PACKAGES.contains(eventPkg) && activeAnswerPendingIntent == null && !isCallActive) {
            return
        }

        // 2. Scan call-specific windows for incoming call UI
        val callFound = scanAllWindowsForIncomingCall() || (activeAnswerPendingIntent != null)

        if (callFound) {
            cancelCallEndedReset()
            if (!isCallActive) {
                isCallActive = true
                registerVolumeObserver()
                BluetoothMonitoringService.activateCallFocus()
                val autoAnswer = BluetoothReceiver.isAutoAnswerOn(applicationContext)
                Log.i(TAG, ">>> INCOMING CALL DETECTED! Current Pkg: $currentCallingPackage, Event Pkg: $eventPkg. Auto-Answer: $autoAnswer <<<")
                if (autoAnswer) {
                    scheduleAutoAnswer()
                }
            }
        } else {
            if (isCallActive) {
                scheduleCallEndedReset()
            }
        }
    }

    private fun extractNotificationAnswerIntent(event: AccessibilityEvent) {
        try {
            val parcelable = event.parcelableData
            if (parcelable is Notification) {
                val pkg = event.packageName?.toString() ?: ""
                if (!CALL_PACKAGES.contains(pkg) && pkg != "com.android.systemui") return

                val actions = parcelable.actions ?: return
                for (action in actions) {
                    val title = action.title?.toString()?.lowercase() ?: ""
                    val isAnswer = title.contains("answer") || title.contains("accept") || title.contains("join") ||
                            title.contains("উত্তর") || title.contains("গ্রহণ") || title.contains("কল")
                    val isDecline = title.contains("decline") || title.contains("reject") || title.contains("dismiss") ||
                            title.contains("বাতিল") || title.contains("কাটুন")

                    if (isAnswer && !isDecline) {
                        action.actionIntent?.let {
                            Log.i(TAG, "Extracted native Answer PendingIntent from AccessibilityEvent for pkg: $pkg (title: '$title')")
                            activeAnswerPendingIntent = it
                            currentCallingPackage = pkg
                        }
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error extracting notification answer intent", e)
        }
    }

    private fun scheduleCallEndedReset() {
        if (callEndedResetRunnable == null) {
            callEndedResetRunnable = Runnable {
                val stillActive = scanAllWindowsForIncomingCall()
                if (!stillActive && activeAnswerPendingIntent == null) {
                    isCallActive = false
                    cancelAutoAnswer()
                    unregisterVolumeObserver()
                    BluetoothMonitoringService.deactivateCallFocus()
                    currentCallingPackage = null
                    activeAnswerPendingIntent = null
                    CallNotificationListenerService.clearPendingIntent()
                    Log.d(TAG, "Call ended confirmed after 2500ms grace period. Resetting state.")
                }
                callEndedResetRunnable = null
            }
            handler.postDelayed(callEndedResetRunnable!!, 2500L)
        }
    }

    private fun cancelCallEndedReset() {
        callEndedResetRunnable?.let { handler.removeCallbacks(it) }
        callEndedResetRunnable = null
    }

    fun scheduleAutoAnswer() {
        cancelAutoAnswer()
        autoAnswerRunnable = Runnable {
            if (isCallActive && BluetoothReceiver.isAutoAnswerOn(applicationContext)) {
                Log.i(TAG, "Triggering automatic call answer action...")
                val success = performAnswerCallAction()
                if (success) {
                    announceCallAnswered()
                } else {
                    handler.postDelayed({
                        if (isCallActive && BluetoothReceiver.isAutoAnswerOn(applicationContext)) {
                            Log.i(TAG, "Auto-answer retry...")
                            val retrySuccess = performAnswerCallAction()
                            if (retrySuccess) announceCallAnswered()
                        }
                    }, 600L)
                }
            }
        }
        handler.postDelayed(autoAnswerRunnable!!, 1000L)
    }

    fun cancelAutoAnswer() {
        autoAnswerRunnable?.let { handler.removeCallbacks(it) }
        autoAnswerRunnable = null
    }

    private fun scanAllWindowsForIncomingCall(): Boolean {
        try {
            val rootNodes = getActiveRootNodes()
            for (root in rootNodes) {
                val answerNode = findAnswerButtonNode(root)
                if (answerNode != null) {
                    currentCallingPackage = root.packageName?.toString() ?: currentCallingPackage
                    answerNode.recycle()
                    root.recycle()
                    return true
                }
                root.recycle()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error scanning windows for incoming call", e)
        }
        return false
    }

    private fun getActiveRootNodes(): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        rootInActiveWindow?.let { root ->
            val pkg = root.packageName?.toString() ?: ""
            if (CALL_PACKAGES.contains(pkg) && pkg != OWN_PACKAGE) {
                nodes.add(root)
            } else {
                root.recycle()
            }
        }

        try {
            windows?.forEach { window ->
                window.root?.let { root ->
                    val pkg = root.packageName?.toString() ?: ""
                    if (CALL_PACKAGES.contains(pkg) && pkg != OWN_PACKAGE && nodes.none { it == root }) {
                        nodes.add(root)
                    } else {
                        root.recycle()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching active window roots", e)
        }
        return nodes
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyEvent(event)

        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) {
            return super.onKeyEvent(event)
        }

        // STRICT GUARD: If Master Switch is OFF or NO call is ringing/active, pass through without intercepting!
        if (!BluetoothReceiver.isMasterSwitchOn(applicationContext) || !isCallActive) {
            return super.onKeyEvent(event)
        }

        val keyCode = event.keyCode
        val keyName = KeyEvent.keyCodeToString(keyCode)
        Log.i(TAG, ">>> KEY EVENT INTERCEPTED during active call: keyCode=$keyCode ($keyName), action=${event.action} <<<")

        val isMediaKey = keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PLAY ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
                keyCode == KeyEvent.KEYCODE_CALL ||
                keyCode == KeyEvent.KEYCODE_MEDIA_STOP ||
                keyCode == KeyEvent.KEYCODE_MEDIA_NEXT ||
                keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS ||
                keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                keyCode == KeyEvent.KEYCODE_VOLUME_DOWN

        if (isMediaKey) {
            Log.i(TAG, "Media/Volume key matches ($keyName). Triggering call answer action...")
            val answered = performAnswerCallAction()
            if (answered) {
                Log.i(TAG, "Call Answer Action reported SUCCESS! Announcing...")
                announceCallAnswered()
                return true
            }
        }

        return super.onKeyEvent(event)
    }

    /**
     * Executes call answering across full screen, heads-up notifications, and lock screen.
     * Strategy:
     * 1. Accessibility Tree Node Search + Click / Gesture (Primary & Most Reliable)
     * 2. Direct Native Notification Action PendingIntent execution
     * 3. Cellular SIM Calls: TelecomManager
     * 4. Multi-Zone Fallback Gesture (Only when call package is confirmed)
     */
    fun performAnswerCallAction(): Boolean {
        if (!isCallActive && activeAnswerPendingIntent == null && CallNotificationListenerService.latestAnswerPendingIntent == null) {
            Log.d(TAG, "performAnswerCallAction called but no active incoming call. Aborting.")
            return false
        }

        val pkg = currentCallingPackage
        Log.i(TAG, "=== START ANSWER CALL ACTION === (Current Pkg: $pkg)")

        var answered = false

        // 1. Accessibility Tree Node Search + Click / Gesture (Primary: Clicks WhatsApp/Messenger/Dialer buttons)
        val rootNodes = getActiveRootNodes()
        Log.i(TAG, "Accessibility node search. Active root windows: ${rootNodes.size}")
        for ((index, rootNode) in rootNodes.withIndex()) {
            val targetNode = findAnswerButtonNode(rootNode)
            if (targetNode != null) {
                val bounds = Rect()
                targetNode.getBoundsInScreen(bounds)
                Log.i(TAG, "MATCHED ANSWER BUTTON in root #$index -> ViewID: ${targetNode.viewIdResourceName}, Text: '${targetNode.text}', Desc: '${targetNode.contentDescription}', Bounds: $bounds, Clickable: ${targetNode.isClickable}")
                val success = executeAnswerNodeClick(targetNode)
                targetNode.recycle()
                rootNode.recycle()
                if (success) {
                    Log.i(TAG, "Call successfully answered via node click/gesture!")
                    answered = true
                    break
                }
            } else {
                rootNode.recycle()
            }
        }

        // 2. Direct Native Notification Action PendingIntent execution
        val pendingIntent = activeAnswerPendingIntent ?: CallNotificationListenerService.latestAnswerPendingIntent
        if (pendingIntent != null) {
            try {
                Log.i(TAG, "Executing Native Answer PendingIntent for $pkg...")
                pendingIntent.send()
                answered = true
            } catch (e: Exception) {
                Log.w(TAG, "Error executing Native Answer PendingIntent", e)
            }
        }

        // 3. Cellular SIM Calls: TelecomManager
        if (pkg != null && SYSTEM_DIALER_PACKAGES.contains(pkg)) {
            try {
                val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                telecomManager?.acceptRingingCall()
                answered = true
            } catch (e: Exception) {}
        }

        // 4. Multi-Zone Blind Gestures (ONLY if call is actively verified in a supported call app)
        if (!answered && isCallActive && pkg != null && CALL_PACKAGES.contains(pkg)) {
            Log.i(TAG, "No explicit answer button node found for known call package $pkg. Dispatching fallback answer gesture...")
            val fallbackSuccess = dispatchBlindCallAnswerGestures()
            if (fallbackSuccess) answered = true
        }

        if (answered) {
            isCallActive = false
            cancelCallEndedReset()
            cancelAutoAnswer()
            unregisterVolumeObserver()
            BluetoothMonitoringService.deactivateCallFocus()
            activeAnswerPendingIntent = null
            CallNotificationListenerService.clearPendingIntent()
            currentCallingPackage = null
            return true
        }

        Log.w(TAG, "=== NO ANSWER METHOD SUCCEEDED ===")
        return false
    }

    /**
     * Executes robust multi-mode click and atomic gesture on the found answer button node.
     */
    private fun executeAnswerNodeClick(targetNode: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        targetNode.getBoundsInScreen(rect)
        val pkg = currentCallingPackage ?: ""
        Log.d(TAG, "executeAnswerNodeClick: bounds=$rect, pkg=$pkg, text=${targetNode.text}, id=${targetNode.viewIdResourceName}")

        // 1. Accessibility ACTION_CLICK and ACTION_SELECT on target node
        var actionClicked = targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        targetNode.performAction(AccessibilityNodeInfo.ACTION_SELECT)
        Log.d(TAG, "  ACTION_CLICK on node: $actionClicked")

        // 2. Accessibility ACTION_CLICK on parents (for wrapped notification buttons)
        try {
            var parent = targetNode.parent
            var depth = 0
            while (parent != null && depth < 4) {
                if (parent.isClickable) {
                    val pClicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "  ACTION_CLICK on parent depth $depth: $pClicked")
                    if (pClicked) actionClicked = true
                }
                val nextParent = parent.parent
                parent.recycle()
                parent = nextParent
                depth++
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error during parent traversal", e)
        }

        // 3. Atomic Multi-Stage Gesture (Single Tap + Double Tap + Swipe Up)
        if (!rect.isEmpty && rect.width() > 0 && rect.height() > 0) {
            val centerX = rect.centerX().toFloat()
            val centerY = rect.centerY().toFloat()

            val gestureDispatched = dispatchAtomicCallAnswerGesture(centerX, centerY)
            Log.d(TAG, "  dispatchAtomicCallAnswerGesture result: $gestureDispatched at ($centerX, $centerY)")
            return true
        }

        return actionClicked
    }

    /**
     * Dispatches an atomic gesture covering:
     * - Tap 1 (0ms-40ms): Answers notifications and standard click buttons
     * - Tap 2 (100ms-140ms): Answers WhatsApp double-tap accessibility buttons
     * - Swipe Up (200ms-450ms): Answers slide-to-answer screens
     */
    private fun dispatchAtomicCallAnswerGesture(centerX: Float, centerY: Float): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val tapPath = Path().apply { moveTo(centerX, centerY) }
                val swipePath = Path().apply {
                    moveTo(centerX, centerY)
                    lineTo(centerX, (centerY - 800f).coerceAtLeast(60f))
                }

                val strokeTap1 = GestureDescription.StrokeDescription(tapPath, 0, 40)
                val strokeTap2 = GestureDescription.StrokeDescription(tapPath, 100, 40)
                val strokeSwipe = GestureDescription.StrokeDescription(swipePath, 200, 250)

                val gesture = GestureDescription.Builder()
                    .addStroke(strokeTap1)
                    .addStroke(strokeTap2)
                    .addStroke(strokeSwipe)
                    .build()

                return dispatchGesture(gesture, null, null)
            } catch (e: Exception) {
                Log.w(TAG, "Error dispatching atomic call answer gesture", e)
            }
        }
        return false
    }

    /**
     * Dispatches multi-zone gestures for hidden call UIs:
     * 1. Bottom-Center (50% w, 82% h) -> Swipe up to 20% h (WhatsApp fullscreen slide-to-answer)
     * 2. Bottom-Right (75% w, 82% h) -> Tap (Two-button layout Accept button)
     * 3. Top-Right (78% w, 12% h) -> Tap (Heads-up notification banner Answer button)
     * 4. Center (50% w, 50% h) -> Tap (Unhides controls)
     */
    private fun dispatchBlindCallAnswerGestures(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                val dm = resources.displayMetrics
                val w = dm.widthPixels.toFloat()
                val h = dm.heightPixels.toFloat()

                val swipePath = Path().apply {
                    moveTo(w * 0.5f, h * 0.82f)
                    lineTo(w * 0.5f, h * 0.20f)
                }
                val tapBottomCenterPath = Path().apply { moveTo(w * 0.5f, h * 0.82f) }
                val tapNotificationPath = Path().apply { moveTo(w * 0.78f, h * 0.12f) }
                val tapBottomRightPath = Path().apply { moveTo(w * 0.75f, h * 0.82f) }

                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(tapNotificationPath, 0, 50))
                    .addStroke(GestureDescription.StrokeDescription(tapBottomCenterPath, 80, 50))
                    .addStroke(GestureDescription.StrokeDescription(tapBottomRightPath, 150, 50))
                    .addStroke(GestureDescription.StrokeDescription(swipePath, 220, 300))
                    .build()

                val res = dispatchGesture(gesture, null, null)
                Log.i(TAG, "Multi-zone blind answer gesture dispatched result: $res")
                return res
            } catch (e: Exception) {
                Log.w(TAG, "Error in dispatchBlindCallAnswerGestures", e)
            }
        }
        return false
    }

    private fun isDeclineNode(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        for (exclude in EXCLUDE_KEYWORDS) {
            if (text == exclude || desc == exclude ||
                text.contains(exclude) || desc.contains(exclude) ||
                viewId.contains(exclude)) {
                return true
            }
        }
        return false
    }

    private fun isAnswerButton(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase()?.trim() ?: ""
        val viewId = node.viewIdResourceName?.lowercase()?.trim() ?: ""
        val pkg = node.packageName?.toString()?.lowercase() ?: ""

        // Skip our own app nodes
        if (pkg.contains(OWN_PACKAGE) || viewId.contains(OWN_PACKAGE)) return false

        // Skip settings toggles or auto-answer labels
        if (text.contains("auto") || text.contains("setting") ||
            desc.contains("auto") || desc.contains("setting")) return false

        // Skip decline/reject buttons
        if (isDeclineNode(node)) return false

        // 1. Check strict answer keywords (exact match or clean phrase match)
        for (keyword in STRICT_ANSWER_KEYWORDS) {
            if (text == keyword || desc == keyword) {
                Log.d(TAG, "  isAnswerButton EXACT match '$keyword' -> text='$text', desc='$desc', id='$viewId'")
                return true
            }
            if ((text.contains(keyword) || desc.contains(keyword)) &&
                !text.contains("auto") && !desc.contains("auto")) {
                Log.d(TAG, "  isAnswerButton SUBSTRING match '$keyword' -> text='$text', desc='$desc', id='$viewId'")
                return true
            }
        }

        // 2. Check call-specific Resource IDs
        if (viewId.isNotEmpty()) {
            for (resId in ANSWER_RESOURCE_IDS) {
                if (viewId.contains(resId)) {
                    Log.d(TAG, "  isAnswerButton RES_ID match '$resId' -> id='$viewId', text='$text'")
                    return true
                }
            }
        }

        return false
    }

    private fun findAnswerButtonNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        // Check if this specific node is an answer button
        if (isAnswerButton(node)) {
            return AccessibilityNodeInfo.obtain(node)
        }

        // ALWAYS traverse child nodes even if parent container contains mixed text (e.g. "Decline Answer")
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findAnswerButtonNode(child)
            child.recycle()
            if (result != null) {
                return result
            }
        }

        return null
    }

    fun announceCallAnswered() {
        try {
            val announcement = getString(R.string.call_answered_announcement)
            @Suppress("deprecation")
            val event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_ANNOUNCEMENT).apply {
                text.add(announcement)
                className = CallAccessibilityService::class.java.name
                packageName = applicationContext.packageName
            }
            val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
            am?.sendAccessibilityEvent(event)
        } catch (e: Exception) {
            Log.w(TAG, "Error sending announcement", e)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "CallAccessibilityService interrupted.")
        isCallActive = false
        cancelCallEndedReset()
        unregisterVolumeObserver()
        BluetoothMonitoringService.deactivateCallFocus()
        activeAnswerPendingIntent = null
    }
}
