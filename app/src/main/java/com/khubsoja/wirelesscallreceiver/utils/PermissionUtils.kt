package com.khubsoja.wirelesscallreceiver.utils

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.khubsoja.wirelesscallreceiver.service.CallAccessibilityService
import com.khubsoja.wirelesscallreceiver.service.CallNotificationListenerService

/**
 * Utility functions for checking and requesting system permissions and settings states.
 */
object PermissionUtils {

    /**
     * Checks if Bluetooth Connect permission is granted.
     * Required on Android 12 (API 31) and higher.
     */
    fun hasBluetoothPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Checks if Post Notifications permission is granted.
     * Required on Android 13 (API 33) and higher.
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Checks whether Android may answer a cellular call through TelecomManager.
     */
    fun hasAnswerPhoneCallsPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ANSWER_PHONE_CALLS
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * Checks whether CallAccessibilityService is enabled in Android Accessibility Settings.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false

        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC or AccessibilityServiceInfo.FEEDBACK_SPOKEN)
        val expectedComponentName = "${context.packageName}/${CallAccessibilityService::class.java.canonicalName}"

        for (service in enabledServices) {
            if (service.id == expectedComponentName) {
                return true
            }
        }

        // Alternative check via Settings.Secure
        val accessibilityEnabled = try {
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
        } catch (e: Settings.SettingNotFoundException) {
            0
        }

        if (accessibilityEnabled == 1) {
            val settingValue = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            if (settingValue.contains(context.packageName)) {
                return true
            }
        }

        return false
    }

    /**
     * Opens system Accessibility Settings screen so user can turn on CallAccessibilityService.
     */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Checks whether the user granted notification-listener access to this app.
     */
    fun isNotificationListenerEnabled(context: Context): Boolean {
        val component = ComponentName(context, CallNotificationListenerService::class.java)
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(component.packageName)
    }

    /**
     * Opens Android's notification access screen after the in-app disclosure and consent step.
     */
    fun openNotificationListenerSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
