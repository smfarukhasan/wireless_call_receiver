package com.khubsoja.wirelesscallreceiver.service

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat

/**
 * Handles answering incoming calls directly via:
 * 1. Native Notification PendingIntent (WhatsApp, Messenger, IMO, Telegram)
 * 2. Exact Answer Node Click or exact button bounds tap in Accessibility Tree
 * 3. TelecomManager (Cellular calls)
 */
class CallAnswerExecutor(private val service: AccessibilityService) {

    companion object {
        private const val TAG = "CallAnswerExecutor"

        private val ANSWER_KEYWORDS = listOf(
            "answer", "accept", "join", "pick up", "উত্তর দিন", "কল গ্রহণ করুন",
            "গ্রহণ করুন", "যুক্ত হন", "responder", "aceptar", "répondre", "annehmen",
            "receive", "talk", "contestar", "atender", "jawab", "terima", "উত্তর",
            "swipe to answer", "swipe up to answer"
        )

        private val EXCLUDED_KEYWORDS = listOf(
            "decline", "reject", "dismiss", "ignore", "hangup", "hang up", "end call",
            "cancel", "message", "reply", "remind", "read", "বাতিল", "কাটুন", "rechazar"
        )

        private val ANSWER_VIEW_IDS = listOf(
            "btn_answer", "answer_button", "call_card_answer", "incoming_call_answer",
            "pickup", "call_accept", "voip_answer_button", "voip_accept_button",
            "call_answer", "voice_call_answer", "video_call_answer", "accept_incoming_call",
            "answer_btn", "accept_btn", "call_accept_btn", "action_accept", "action_answer",
            "action0", "action1", "button_answer", "button_accept", "fab_answer",
            "swipe_to_answer", "swipe_up"
        )
    }

    fun answerCall(callingPackage: String?, pendingIntent: PendingIntent?): Boolean {
        Log.i(TAG, "=== [STEP 3] Executing Direct Answer for pkg: $callingPackage ===")
        var success = false

        // 1. Send native Notification PendingIntent
        if (pendingIntent != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val options = ActivityOptions.makeBasic().apply {
                        pendingIntentBackgroundActivityStartMode = ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    }
                    pendingIntent.send(service, 0, null, null, null, null, options.toBundle())
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    pendingIntent.send(service, 0, null)
                } else {
                    pendingIntent.send()
                }
                Log.i(TAG, "Answer Method 1: Sent native PendingIntent successfully.")
                success = true
            } catch (e: Exception) {
                Log.w(TAG, "PendingIntent send failed: ${e.message}")
            }
        }

        // 2. Exact Answer Node Click in Accessibility Tree (if visible)
        if (clickAnswerNode()) {
            Log.i(TAG, "Answer Method 2: Clicked exact Answer node in Accessibility Tree.")
            success = true
        }

        // 3. TelecomManager for cellular phone calls
        if (callingPackage != null && callingPackage in CallAppRegistry.systemDialerPackages) {
            if (acceptTelecomCall()) {
                Log.i(TAG, "Answer Method 3: TelecomManager accept call executed.")
                success = true
            }
        }

        return success
    }

    fun clickAnswerNode(): Boolean {
        val roots = getAllActiveRoots()
        for (root in roots) {
            val answerNode = findAnswerNodeInTree(root)
            if (answerNode != null) {
                Log.i(TAG, "Found a target Answer control in a supported call interface.")
                val clicked = performNodeClickOrParent(answerNode)
                answerNode.recycle()
                root.recycle()
                if (clicked) return true
            }
            root.recycle()
        }
        return false
    }

    private fun findAnswerNodeInTree(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null

        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        val isExcluded = EXCLUDED_KEYWORDS.any { text.contains(it) || desc.contains(it) || viewId.contains(it) }
        if (!isExcluded) {
            val matchesKeyword = ANSWER_KEYWORDS.any { (it.isNotEmpty() && text.contains(it)) || (it.isNotEmpty() && desc.contains(it)) }
            val matchesViewId = ANSWER_VIEW_IDS.any { it.isNotEmpty() && viewId.contains(it) }

            if (matchesKeyword || matchesViewId) {
                return node
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val match = findAnswerNodeInTree(child)
            if (match != null) {
                if (match != child) child?.recycle()
                return match
            }
            child?.recycle()
        }
        return null
    }

    private fun performNodeClickOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                val clicked = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    Log.i(TAG, "Successfully clicked Answer node directly via ACTION_CLICK")
                    return true
                }
            }
            val parent = current.parent
            if (current != node) current.recycle()
            current = parent
        }

        // Exact button center tap fallback (targets ONLY the Answer button's real screen bounds)
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!bounds.isEmpty && bounds.width() > 0 && bounds.height() > 0 && bounds.width() < 1200) {
            Log.i(TAG, "Tapping exact Answer button bounds at (${bounds.centerX()}, ${bounds.centerY()})")
            return dispatchExactTap(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        }
        return false
    }

    private fun dispatchExactTap(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            val path = Path().apply { moveTo(x, y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 50)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gesture, null, null)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Exact tap failed at ($x, $y)", e)
            false
        }
    }

    private fun acceptTelecomCall(): Boolean {
        return try {
            if (ContextCompat.checkSelfPermission(service, Manifest.permission.ANSWER_PHONE_CALLS) == PackageManager.PERMISSION_GRANTED) {
                @Suppress("DEPRECATION")
                val tm = service.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                tm?.acceptRingingCall()
                true
            } else false
        } catch (e: Exception) {
            Log.w(TAG, "TelecomManager acceptRingingCall failed", e)
            false
        }
    }

    private fun getAllActiveRoots(): List<AccessibilityNodeInfo> {
        val roots = mutableListOf<AccessibilityNodeInfo>()
        service.rootInActiveWindow?.let {
            if (CallAppRegistry.isSupportedCallPackage(it.packageName)) roots.add(it)
        }
        try {
            service.windows?.forEach { window ->
                window.root?.let { root ->
                    if (CallAppRegistry.isSupportedCallPackage(root.packageName) && roots.none { it == root }) {
                        roots.add(root)
                    }
                }
            }
        } catch (_: Exception) { }
        return roots
    }
}
