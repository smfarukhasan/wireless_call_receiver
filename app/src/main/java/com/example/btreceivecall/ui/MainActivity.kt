package com.example.btreceivecall.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
 * Provides accessible controls, Master On/Off toggle, live status indicators, pull-to-refresh, and settings shortcuts.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "bt_receive_call_prefs"
        private const val KEY_MASTER_SWITCH = "master_switch_state"
        const val KEY_AUTO_ANSWER = "auto_answer_state"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    private val liveUpdateHandler = Handler(Looper.getMainLooper())
    private val liveUpdateRunnable = object : Runnable {
        override fun run() {
            updateDashboardStatus()
            liveUpdateHandler.postDelayed(this, 1500)
        }
    }

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

        setupSwipeRefresh()
        setupMasterSwitch()
        setupAutoAnswerSwitch()
        setupButtons()
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeColors(
            ContextCompat.getColor(this, R.color.accent_blue)
        )
        binding.swipeRefreshLayout.setOnRefreshListener {
            updateDashboardStatus()
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    override fun onResume() {
        super.onResume()
        updateDashboardStatus()
        liveUpdateHandler.removeCallbacks(liveUpdateRunnable)
        liveUpdateHandler.post(liveUpdateRunnable)
    }

    override fun onPause() {
        super.onPause()
        liveUpdateHandler.removeCallbacks(liveUpdateRunnable)
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
                if (getBluetoothConnectionInfo().first) {
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

    private fun setupAutoAnswerSwitch() {
        val autoAnswerState = prefs.getBoolean(KEY_AUTO_ANSWER, false)
        binding.switchAutoAnswer.isChecked = autoAnswerState
        updateAutoAnswerDescription(autoAnswerState)

        binding.switchAutoAnswer.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_AUTO_ANSWER, isChecked).apply()
            updateAutoAnswerDescription(isChecked)
            Log.d(TAG, "Auto-Answer Switch toggled: $isChecked")
        }
    }

    private fun updateAutoAnswerDescription(isOn: Boolean) {
        if (isOn) {
            binding.tvAutoAnswerDesc.text = getString(R.string.main_auto_answer_on)
        } else {
            binding.tvAutoAnswerDesc.text = getString(R.string.main_auto_answer_off)
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

        // 1. Bluetooth Connection Status & Device Name
        val (btConnected, deviceName) = getBluetoothConnectionInfo()
        if (btConnected && masterOn) {
            val statusText = if (!deviceName.isNullOrBlank()) {
                "${getString(R.string.main_bt_connected)}: $deviceName"
            } else {
                getString(R.string.main_bt_connected)
            }
            binding.tvBtStatusCardValue.text = statusText
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

    private fun getBluetoothConnectionInfo(): Pair<Boolean, String?> {
        if (!PermissionUtils.hasBluetoothPermission(this)) return Pair(false, null)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return Pair(false, null)
        val adapter = bluetoothManager.adapter ?: return Pair(false, null)

        if (!adapter.isEnabled) return Pair(false, null)

        try {
            val bonded = try { adapter.bondedDevices ?: emptySet() } catch (e: SecurityException) { emptySet() }
            if (bonded.isNotEmpty()) {
                val connectedDev = bonded.firstOrNull { dev ->
                    try {
                        val stateHeadset = adapter.getProfileConnectionState(BluetoothProfile.HEADSET)
                        val stateA2dp = adapter.getProfileConnectionState(BluetoothProfile.A2DP)
                        (stateHeadset == BluetoothProfile.STATE_CONNECTED || stateA2dp == BluetoothProfile.STATE_CONNECTED)
                    } catch (e: Exception) {
                        false
                    }
                } ?: bonded.firstOrNull()

                if (connectedDev != null) {
                    val headsetConn = try { adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED } catch (e: Exception) { false }
                    val a2dpConn = try { adapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED } catch (e: Exception) { false }
                    val isConn = headsetConn || a2dpConn || BluetoothMonitoringService.isServiceRunning

                    val name = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !connectedDev.alias.isNullOrBlank()) {
                            connectedDev.alias
                        } else {
                            connectedDev.name
                        }
                    } catch (e: SecurityException) {
                        null
                    }
                    return Pair(isConn, name)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching connected Bluetooth device info", e)
        }

        return Pair(false, null)
    }
}
