package com.example.btreceivecall.service

import android.accessibilityservice.AccessibilityService
import android.app.PendingIntent
import android.util.Log

/**
 * Clean CallEndExecutor - Currently in safe observation mode.
 */
class CallEndExecutor(private val service: AccessibilityService) {

    companion object {
        private const val TAG = "CallEndExecutor"
    }

    fun endCall(endPendingIntent: PendingIntent?): Boolean {
        Log.i(TAG, "endCall requested (No screen gestures/clicks)")
        return true
    }

    fun hasVisibleEndNode(): Boolean {
        return false
    }
}
