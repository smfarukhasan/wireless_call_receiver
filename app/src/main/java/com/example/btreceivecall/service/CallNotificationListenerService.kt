package com.example.btreceivecall.service

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * High-priority NotificationListenerService that captures native VoIP call answer PendingIntents.
 * Directly answers WhatsApp, Messenger, and IMO calls at the notification level, completely
 * bypassing any fullscreen UI bugs or hidden buttons.
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
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "CallNotificationListenerService created.")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        latestAnswerPendingIntent = null
        Log.d(TAG, "CallNotificationListenerService destroyed.")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName ?: ""
        val isCallApp = pkg.contains("whatsapp") || pkg.contains("facebook.orca") ||
                pkg.contains("telegram") || pkg.contains("imo") ||
                pkg.contains("dialer") || pkg.contains("phone") || pkg.contains("incallui")

        if (!isCallApp) return

        val notif = sbn.notification ?: return
        val actions = notif.actions ?: return

        for (action in actions) {
            val title = action.title?.toString()?.lowercase() ?: ""
            val isAnswer = title.contains("answer") || title.contains("accept") || title.contains("join") ||
                    title.contains("উত্তর") || title.contains("গ্রহণ") || title.contains("কল")

            val isDecline = title.contains("decline") || title.contains("reject") || title.contains("dismiss") ||
                    title.contains("বাতিল") || title.contains("কাটুন")

            if (isAnswer && !isDecline) {
                val intent = action.actionIntent
                if (intent != null) {
                    Log.i(TAG, ">>> CAPTURED NATIVE ANSWER INTENT for pkg: $pkg, action title: '$title' <<<")
                    latestAnswerPendingIntent = intent
                    CallAccessibilityService.instance?.onNotificationAnswerIntentCaptured(intent, pkg)
                    break
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        val pkg = sbn?.packageName ?: ""
        if (pkg.contains("whatsapp") || pkg.contains("facebook.orca") || pkg.contains("imo")) {
            latestAnswerPendingIntent = null
        }
    }
}
