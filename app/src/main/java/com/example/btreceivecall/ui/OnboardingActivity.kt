package com.example.btreceivecall.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.btreceivecall.R
import com.example.btreceivecall.databinding.ActivityOnboardingBinding
import com.example.btreceivecall.utils.LocaleHelper
import com.example.btreceivecall.utils.PermissionUtils

/**
 * Step-by-step onboarding activity guiding first-time users through granting essential permissions.
 * Fully compatible with TalkBack screen reader accessibility.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        updateStatusIndicators()
    }

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
        // Step 1: Bluetooth Connect, Notification & Phone Call Permissions
        binding.btnGrantBt.setOnClickListener {
            val perms = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            // Phone call permissions for TelecomManager.acceptRingingCall()
            perms.add(Manifest.permission.ANSWER_PHONE_CALLS)
            perms.add(Manifest.permission.READ_PHONE_STATE)
            if (perms.isNotEmpty()) {
                permissionsLauncher.launch(perms.toTypedArray())
            } else {
                updateStatusIndicators()
            }
        }

        // Step 2: Accessibility Settings
        binding.btnGrantAcc.setOnClickListener {
            PermissionUtils.openAccessibilitySettings(this)
        }

        // Step 3: Battery Optimization Exemption
        binding.btnGrantBatt.setOnClickListener {
            PermissionUtils.requestIgnoreBatteryOptimization(this)
        }

        // Privacy Policy & Terms Button in Setup
        binding.btnOnboardingPrivacy.setOnClickListener {
            val intent = Intent(this, PrivacyPolicyActivity::class.java)
            startActivity(intent)
        }

        // Finish Setup Button
        binding.btnFinishSetup.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            finish()
        }
    }

    private fun updateStatusIndicators() {
        // 1. Bluetooth Permission Status
        val hasBt = PermissionUtils.hasBluetoothPermission(this)
        if (hasBt) {
            binding.tvBtStatus.text = getString(R.string.status_granted)
            binding.tvBtStatus.setTextColor(ContextCompat.getColor(this, R.color.status_green))
            binding.btnGrantBt.isEnabled = false
            binding.btnGrantBt.alpha = 0.6f
        } else {
            binding.tvBtStatus.text = getString(R.string.status_not_granted)
            binding.tvBtStatus.setTextColor(ContextCompat.getColor(this, R.color.status_red))
            binding.btnGrantBt.isEnabled = true
            binding.btnGrantBt.alpha = 1.0f
        }

        // 2. Accessibility Service Status
        val hasAcc = PermissionUtils.isAccessibilityServiceEnabled(this)
        if (hasAcc) {
            binding.tvAccStatus.text = getString(R.string.status_enabled)
            binding.tvAccStatus.setTextColor(ContextCompat.getColor(this, R.color.status_green))
            binding.btnGrantAcc.isEnabled = false
            binding.btnGrantAcc.alpha = 0.6f
        } else {
            binding.tvAccStatus.text = getString(R.string.status_disabled)
            binding.tvAccStatus.setTextColor(ContextCompat.getColor(this, R.color.status_red))
            binding.btnGrantAcc.isEnabled = true
            binding.btnGrantAcc.alpha = 1.0f
        }

        // 3. Battery Optimization Status
        val hasBatt = PermissionUtils.isBatteryOptimizationIgnored(this)
        if (hasBatt) {
            binding.tvBattStatus.text = getString(R.string.status_enabled)
            binding.tvBattStatus.setTextColor(ContextCompat.getColor(this, R.color.status_green))
            binding.btnGrantBatt.isEnabled = false
            binding.btnGrantBatt.alpha = 0.6f
        } else {
            binding.tvBattStatus.text = getString(R.string.status_disabled)
            binding.tvBattStatus.setTextColor(ContextCompat.getColor(this, R.color.status_red))
            binding.btnGrantBatt.isEnabled = true
            binding.btnGrantBatt.alpha = 1.0f
        }
    }
}
