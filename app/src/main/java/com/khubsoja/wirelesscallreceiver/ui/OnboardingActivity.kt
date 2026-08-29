package com.khubsoja.wirelesscallreceiver.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.khubsoja.wirelesscallreceiver.R
import com.khubsoja.wirelesscallreceiver.databinding.ActivityOnboardingBinding
import com.khubsoja.wirelesscallreceiver.utils.LocaleHelper
import com.khubsoja.wirelesscallreceiver.utils.PermissionUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Requests each capability separately and in context. Special-access settings are opened only
 * after a standalone, affirmative in-app disclosure and consent step.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { updateStatusIndicators() }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { updateStatusIndicators() }

    private val phonePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { updateStatusIndicators() }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applySavedLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateStatusIndicators()
    }

    private fun setupListeners() {
        binding.btnGrantBt.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        binding.btnGrantNotifications.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        binding.btnGrantPhone.setOnClickListener {
            phonePermissionLauncher.launch(Manifest.permission.ANSWER_PHONE_CALLS)
        }

        binding.btnGrantAcc.setOnClickListener { showAccessibilityDisclosure() }
        binding.btnGrantNotificationAccess.setOnClickListener { showNotificationAccessDisclosure() }

        binding.btnOnboardingPrivacy.setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }

        binding.btnFinishSetup.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            })
            finish()
        }
    }

    private fun showAccessibilityDisclosure() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.disclosure_accessibility_title)
            .setMessage(R.string.disclosure_accessibility_message)
            .setPositiveButton(R.string.consent_agree_continue) { _, _ ->
                PermissionUtils.openAccessibilitySettings(this)
            }
            .setNegativeButton(R.string.consent_not_now, null)
            .show()
    }

    private fun showNotificationAccessDisclosure() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.disclosure_notification_access_title)
            .setMessage(R.string.disclosure_notification_access_message)
            .setPositiveButton(R.string.consent_agree_continue) { _, _ ->
                PermissionUtils.openNotificationListenerSettings(this)
            }
            .setNegativeButton(R.string.consent_not_now, null)
            .show()
    }

    private fun updateStatusIndicators() {
        updatePermissionStatus(
            binding.tvBtStatus,
            binding.btnGrantBt,
            PermissionUtils.hasBluetoothPermission(this),
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        )
        updatePermissionStatus(
            binding.tvNotificationsStatus,
            binding.btnGrantNotifications,
            PermissionUtils.hasNotificationPermission(this),
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
        )
        updatePermissionStatus(
            binding.tvPhoneStatus,
            binding.btnGrantPhone,
            PermissionUtils.hasAnswerPhoneCallsPermission(this)
        )
        updateSpecialAccessStatus(
            binding.tvAccStatus,
            binding.btnGrantAcc,
            PermissionUtils.isAccessibilityServiceEnabled(this)
        )
        updateSpecialAccessStatus(
            binding.tvNotificationAccessStatus,
            binding.btnGrantNotificationAccess,
            PermissionUtils.isNotificationListenerEnabled(this)
        )
    }

    private fun updatePermissionStatus(
        statusView: TextView,
        button: View,
        granted: Boolean,
        notRequired: Boolean = false
    ) {
        val complete = granted || notRequired
        statusView.text = getString(
            when {
                notRequired -> R.string.status_not_required
                granted -> R.string.status_granted
                else -> R.string.status_not_granted_v2
            }
        )
        applyStatusStyle(statusView, button, complete)
    }

    private fun updateSpecialAccessStatus(statusView: TextView, button: View, enabled: Boolean) {
        statusView.text = getString(if (enabled) R.string.status_enabled else R.string.status_disabled_v2)
        applyStatusStyle(statusView, button, enabled)
    }

    private fun applyStatusStyle(statusView: TextView, button: View, complete: Boolean) {
        statusView.setTextColor(
            ContextCompat.getColor(this, if (complete) R.color.status_green else R.color.status_red)
        )
        button.isEnabled = !complete
        button.alpha = if (complete) 0.6f else 1.0f
    }
}
