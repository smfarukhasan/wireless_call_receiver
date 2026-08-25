package com.example.btreceivecall.service

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

/**
 * High-priority NotificationListenerService that captures native VoIP call answer AND end PendingIntents.
 * Directly intercepts WhatsApp, Messenger, Telegram, IMO, and cellular calls at the notification level.
 */
class CallNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "CallNotifListener"

        @Volatile
        var instance: CallNotificationListenerService? = null
            private set

        @Volatile
        var latestAnswerPendingIntent: PendingIntent? = null
            private set

        @Volatile
        var latestEndCallPendingIntent: PendingIntent? = null
            private set

        fun clearAnswerPendingIntent() {
            latestAnswerPendingIntent = null
        }

        fun clearPendingIntents() {
            latestAnswerPendingIntent = null
            latestEndCallPendingIntent = null
        }

        private val ANSWER_KEYWORDS = listOf(
            "answer", "accept", "join", "pick up", "উত্তর", "গ্রহণ", "কল ধরুন",
            "responder", "aceptar", "répondre", "annehmen", "receive", "jawab", "terima"
        )

        private val DECLINE_KEYWORDS = listOf(
            "decline", "reject", "dismiss", "hang", "end", "বাতিল", "কাটুন",
            "ignore", "rechazar", "refuser", "auflegen", "batal"
        )
    }

    private val activeCallNotifications = ConcurrentHashMap<String, String>()

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "CallNotificationListenerService created.")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.i(TAG, "Notification listener connected; rescanning active notifications.")
        try {
            activeNotifications?.forEach { onNotificationPosted(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Active notification rescan failed", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        latestAnswerPendingIntent = null
        latestEndCallPendingIntent = null
        activeCallNotifications.clear()
        Log.d(TAG, "CallNotificationListenerService destroyed.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: ""
        val notif = sbn.notification ?: return
        val actions = notif.actions ?: emptyArray()

        val isCallApp = CallAppRegistry.callPackages.any { pkg.contains(it) } ||
                pkg.contains("whatsapp") || pkg.contains("facebook.orca") ||
                pkg.contains("telegram") || pkg.contains("imo") ||
                pkg.contains("dialer") || pkg.contains("phone") || pkg.contains("incallui") ||
                notif.category == Notification.CATEGORY_CALL || notif.fullScreenIntent != null

        if (!isCallApp) return

        Log.d(
            TAG,
            "Call-app notification: pkg=$pkg category=${notif.category} fullScreen=${notif.fullScreenIntent != null} actions=${actions.map { it.title }}"
        )

        var answerFound = false
        var endFound = false

        for (action in actions) {
            val title = action.title?.toString()?.lowercase() ?: ""

            val isAnswer = !answerFound && ANSWER_KEYWORDS.any { title.contains(it) }
            val isDecline = DECLINE_KEYWORDS.any { title.contains(it) }

            if (isAnswer && !isDecline) {
                action.actionIntent?.let { intent ->
                    Log.i(TAG, ">>> CAPTURED NATIVE ANSWER INTENT for pkg: $pkg, action: '$title' <<<")
                    latestAnswerPendingIntent = intent
                    CallAccessibilityService.instance?.onNotificationAnswerIntentCaptured(intent, pkg)
                    answerFound = true
                }
            } else if (isDecline && !endFound) {
                action.actionIntent?.let { intent ->
                    Log.i(TAG, ">>> CAPTURED NATIVE END/DECLINE INTENT for pkg: $pkg, action: '$title' <<<")
                    latestEndCallPendingIntent = intent
                    endFound = true
                }
            }
        }

        val incomingCandidate = answerFound || notif.fullScreenIntent != null || notif.category == Notification.CATEGORY_CALL
        val ongoingCandidate = endFound && !answerFound && notif.fullScreenIntent == null
        val isCallNotification = answerFound || endFound || notif.category == Notification.CATEGORY_CALL || notif.fullScreenIntent != null

        if (isCallNotification) {
            activeCallNotifications[sbn.key] = pkg
            CallAccessibilityService.instance?.onCallNotificationPosted(
                pkg,
                incomingCandidate,
                ongoingCandidate
            )
        } else {
            finishTrackedNotification(sbn.key, allowReplacementGrace = false)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        val removed = sbn ?: return
        finishTrackedNotification(removed.key, allowReplacementGrace = true)
    }

    private fun finishTrackedNotification(key: String, allowReplacementGrace: Boolean) {
        val pkg = activeCallNotifications.remove(key) ?: return
        if (activeCallNotifications.values.none { it == pkg }) {
            CallAccessibilityService.instance?.onNotificationAnswerIntentRemoved(pkg, allowReplacementGrace)
        }
    }
}
