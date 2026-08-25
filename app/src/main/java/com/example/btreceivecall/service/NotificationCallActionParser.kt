package com.example.btreceivecall.service

import android.app.Notification
import android.app.PendingIntent
import android.view.accessibility.AccessibilityEvent

internal data class NotificationAnswerAction(val packageName: String, val intent: PendingIntent)

/** Parses only call-answer actions strictly from verified VoIP packages. */
internal object NotificationCallActionParser {
    private val answerWords = listOf(
        "answer", "accept", "join", "pick up", "উত্তর দিন", "কল গ্রহণ করুন", "গ্রহণ করুন", "কল ধরুন",
        "responder", "aceptar", "répondre", "annehmen", "jawab", "terima"
    )
    private val declineWords = listOf(
        "decline", "reject", "dismiss", "বাতিল", "কাটুন", "rechazar", "hang", "end"
    )

    fun answerAction(event: AccessibilityEvent): NotificationAnswerAction? {
        val notification = event.parcelableData as? Notification ?: return null
        val packageName = event.packageName?.toString() ?: return null
        if (packageName !in CallAppRegistry.callPackages) return null

        return notification.actions.orEmpty().firstNotNullOfOrNull { action ->
            val title = action.title?.toString()?.lowercase().orEmpty()
            val isAnswer = answerWords.any { title.contains(it) }
            val isDecline = declineWords.any { title.contains(it) }
            val intent = action.actionIntent
            if (isAnswer && !isDecline && intent != null) {
                NotificationAnswerAction(packageName, intent)
            } else {
                null
            }
        }
    }
}
