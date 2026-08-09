package com.example.btreceivecall.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.btreceivecall.R
import com.example.btreceivecall.databinding.ActivityMainBinding
import com.example.btreceivecall.service.BluetoothMonitoringService
import com.example.btreceivecall.utils.LocaleHelper
import com.example.btreceivecall.utils.PermissionUtils

/**
 * Single-Page Main Dashboard Activity for BT Receive Call.
 * Provides accessible controls, Master On/Off toggle, live status indicators, and settings shortcuts.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "bt_receive_call_prefs"
        private const val KEY_MASTER_SWITCH = "master_switch_state"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applySavedLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if language selection was completed on initial launch
        if (!LocaleHelper.isLanguageSet(this)) {
            val intent = Intent(this, LanguageSelectionActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        setupMasterSwitch()
        setupButtons()
    }

    override fun onResume() {
        super.onResume()
        updateDashboardStatus()
    }

    private fun setupMasterSwitch() {
        val masterState = prefs.getBoolean(KEY_MASTER_SWITCH, true)
        binding.switchMasterToggle.isChecked = masterState
        updateMasterSwitchDescription(masterState)

        binding.switchMasterToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_MASTER_SWITCH, isChecked).apply()
            updateMasterSwitchDescription(isChecked)

            if (isChecked) {
                Log.d(TAG, "Master Switch toggled ON")
                if (isBluetoothDeviceConnected()) {
                    BluetoothMonitoringService.startService(this)
                }
            } else {
                Log.d(TAG, "Master Switch toggled OFF. Stopping background activities.")
                BluetoothMonitoringService.stopService(this)
            }

            updateDashboardStatus()
        }
    }

    private fun updateMasterSwitchDescription(isOn: Boolean) {
        if (isOn) {
            binding.tvMasterSwitchDesc.text = getString(R.string.main_master_switch_on)
        } else {
            binding.tvMasterSwitchDesc.text = getString(R.string.main_master_switch_off)
        }
    }

    private fun setupButtons() {
        // Change Language Button
        binding.btnMainChangeLanguage.setOnClickListener {
            val intent = Intent(this, LanguageSelectionActivity::class.java).apply {
                putExtra(LanguageSelectionActivity.EXTRA_FROM_SETTINGS, true)
            }
            startActivity(intent)
        }

        // Re-run Setup Wizard Button
        binding.btnMainRerunSetup.setOnClickListener {
            val intent = Intent(this, OnboardingActivity::class.java)
            startActivity(intent)
        }

        // Privacy Policy & Terms Button
        binding.btnMainPrivacyTerms.setOnClickListener {
            val intent = Intent(this, PrivacyPolicyActivity::class.java)
            startActivity(intent)
        }
    }

    private fun updateDashboardStatus() {
        val masterOn = prefs.getBoolean(KEY_MASTER_SWITCH, true)

        // 1. Bluetooth Connection Status
        val btConnected = isBluetoothDeviceConnected()
        if (btConnected && masterOn) {
            binding.tvBtStatusCardValue.text = getString(R.string.main_bt_connected)
            binding.tvBtStatusCardValue.setTextColor(ContextCompat.getColor(this, R.color.status_green))
        } else {
            binding.tvBtStatusCardValue.text = getString(R.string.main_bt_disconnected)
            binding.tvBtStatusCardValue.setTextColor(ContextCompat.getColor(this, R.color.status_red))
        }

        // 2. Accessibility Service Status
        val accEnabled = PermissionUtils.isAccessibilityServiceEnabled(this)
        if (accEnabled) {
            binding.tvAccStatusCardValue.text = getString(R.string.status_enabled)
            binding.tvAccStatusCardValue.setTextColor(ContextCompat.getColor(this, R.color.status_green))
        } else {
            binding.tvAccStatusCardValue.text = getString(R.string.status_disabled)
            binding.tvAccStatusCardValue.setTextColor(ContextCompat.getColor(this, R.color.status_red))
        }

        // 3. Battery Optimization Status
        val battIgnored = PermissionUtils.isBatteryOptimizationIgnored(this)
        if (battIgnored) {
            binding.tvBattStatusCardValue.text = getString(R.string.status_enabled)
            binding.tvBattStatusCardValue.setTextColor(ContextCompat.getColor(this, R.color.status_green))
        } else {
            binding.tvBattStatusCardValue.text = getString(R.string.status_disabled)
            binding.tvBattStatusCardValue.setTextColor(ContextCompat.getColor(this, R.color.status_red))
        }
    }

    private fun isBluetoothDeviceConnected(): Boolean {
        if (!PermissionUtils.hasBluetoothPermission(this)) return false

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return false
        val adapter = bluetoothManager.adapter ?: return false

        if (!adapter.isEnabled) return false

        val headsetConnected = adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
        val a2dpConnected = adapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED

        return headsetConnected || a2dpConnected || BluetoothMonitoringService.isServiceRunning
    }
}
