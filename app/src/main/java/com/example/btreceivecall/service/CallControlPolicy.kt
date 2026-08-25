package com.example.btreceivecall.service

/** Pure timing rules shared by the call-control service and its unit tests. */
internal object CallControlPolicy {
    // Leave room for notification delivery and PendingIntent dispatch while still
    // satisfying the locked-screen "within one second" requirement.
    const val LOCKED_AUTO_ANSWER_DELAY_MS = 400L
    const val UNLOCKED_AUTO_ANSWER_DELAY_MS = 3_000L

    const val DOUBLE_PRESS_WINDOW_MS = 800L
    const val DUPLICATE_EVENT_WINDOW_MS = 120L

    fun autoAnswerDelayMs(isLocked: Boolean): Long =
        if (isLocked) LOCKED_AUTO_ANSWER_DELAY_MS else UNLOCKED_AUTO_ANSWER_DELAY_MS

    fun isDuplicateEvent(previousEventMs: Long, eventMs: Long): Boolean {
        if (previousEventMs <= 0L || eventMs < previousEventMs) return false
        return eventMs - previousEventMs <= DUPLICATE_EVENT_WINDOW_MS
    }

    fun isDoublePress(previousPressMs: Long, pressMs: Long): Boolean {
        if (previousPressMs <= 0L || pressMs < previousPressMs) return false
        return pressMs - previousPressMs <= DOUBLE_PRESS_WINDOW_MS
    }
}
