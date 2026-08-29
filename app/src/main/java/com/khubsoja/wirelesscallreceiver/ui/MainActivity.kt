package com.khubsoja.wirelesscallreceiver.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.khubsoja.wirelesscallreceiver.R
import com.khubsoja.wirelesscallreceiver.databinding.ActivityMainBinding
import com.khubsoja.wirelesscallreceiver.receiver.BluetoothReceiver
import com.khubsoja.wirelesscallreceiver.service.BluetoothMonitoringService
import com.khubsoja.wirelesscallreceiver.utils.LocaleHelper
import com.khubsoja.wirelesscallreceiver.utils.PermissionUtils
import com.google.android.material.snackbar.Snackbar

/**
 * Single-Page Main Dashboard Activity for Wireless Call Receiver.
 * Master switch and background service automatically sync with Bluetooth device connection status:
 * - When a device is connected: Master Service turns ON automatically and restores saved preferences.
 * - When no device is connected: Master Service turns OFF automatically and saves 100% battery.
 * - If user tries to manually turn on Master Switch without a connected Bluetooth device, a centered glass Snackbar explains why.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PREFS_NAME = "wireless_call_receiver_prefs"
        const val KEY_MASTER_SWITCH = "master_switch_state"
        const val KEY_AUTO_ANSWER = "auto_answer_state"
        const val KEY_RECEIVE_LOCKED = "receive_locked_state"
        const val KEY_RECEIVE_UNLOCKED = "receive_unlocked_state"
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private var isProgrammaticSwitchChange = false

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
        setupReceiveLockedSwitch()
        setupReceiveUnlockedSwitch()
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

    private fun updateSubSwitchesVisibility(isMasterOn: Boolean) {
        binding.layoutSubSwitchesContainer.visibility = if (isMasterOn) View.VISIBLE else View.GONE
    }

    private fun setupMasterSwitch() {
        val masterState = prefs.getBoolean(KEY_MASTER_SWITCH, false)
        binding.tvMasterSwitchTitle.text = getString(R.string.main_master_switch_title)
        
        isProgrammaticSwitchChange = true
        binding.switchMasterToggle.isChecked = masterState
        isProgrammaticSwitchChange = false
        
        updateSubSwitchesVisibility(masterState)

        binding.switchMasterToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isProgrammaticSwitchChange) return@setOnCheckedChangeListener

            if (isChecked) {
                val (btConnected, _) = getBluetoothConnectionInfo()
                if (!btConnected) {
                    // Cannot enable without connected Bluetooth device -> revert and show glass snackbar
                    Log.d(TAG, "Master Switch manually toggled ON without connected BT device -> rejecting.")
                    isProgrammaticSwitchChange = true
                    binding.switchMasterToggle.isChecked = false
                    isProgrammaticSwitchChange = false

                    prefs.edit().putBoolean(KEY_MASTER_SWITCH, false).apply()
                    updateSubSwitchesVisibility(false)
                    showGlassSnackbar(getString(R.string.msg_bt_connect_required))
                    return@setOnCheckedChangeListener
                }

                Log.d(TAG, "Master Switch manually toggled ON with connected BT device.")
                prefs.edit().putBoolean(KEY_MASTER_SWITCH, true).apply()
                updateSubSwitchesVisibility(true)
                try { BluetoothMonitoringService.startService(this) } catch (_: Exception) { }
            } else {
                Log.d(TAG, "Master Switch manually toggled OFF.")
                prefs.edit().putBoolean(KEY_MASTER_SWITCH, false).apply()
                updateSubSwitchesVisibility(false)
                BluetoothMonitoringService.stopService(this)
            }

            updateDashboardStatus()
        }
    }

    private fun setupReceiveLockedSwitch() {
        binding.tvReceiveLockedTitle.text = getString(R.string.main_locked_screen_receive_title)
        val lockedState = prefs.getBoolean(KEY_RECEIVE_LOCKED, true)
        binding.switchReceiveLocked.isChecked = lockedState
        updateReceiveLockedDescription(lockedState)

        binding.switchReceiveLocked.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_RECEIVE_LOCKED, isChecked).apply()
            updateReceiveLockedDescription(isChecked)
            Log.d(TAG, "Lock Screen Receive Switch toggled: $isChecked")

            // If both Lock and Unlocked are OFF -> automatically turn OFF Auto-Answer
            if (!isChecked && !binding.switchReceiveUnlocked.isChecked) {
                if (binding.switchAutoAnswer.isChecked) {
                    prefs.edit().putBoolean(KEY_AUTO_ANSWER, false).apply()
                    binding.switchAutoAnswer.isChecked = false
                    updateAutoAnswerDescription(false)
                }
            }
        }
    }

    private fun updateReceiveLockedDescription(isOn: Boolean) {
        if (isOn) {
            binding.tvReceiveLockedDesc.text = getString(R.string.main_locked_screen_receive_desc_on)
        } else {
            binding.tvReceiveLockedDesc.text = getString(R.string.main_locked_screen_receive_desc_off)
        }
    }

    private fun setupReceiveUnlockedSwitch() {
        binding.tvReceiveUnlockedTitle.text = getString(R.string.main_unlocked_screen_receive_title)
        val unlockedState = prefs.getBoolean(KEY_RECEIVE_UNLOCKED, false)
        binding.switchReceiveUnlocked.isChecked = unlockedState
        updateReceiveUnlockedDescription(unlockedState)

        binding.switchReceiveUnlocked.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_RECEIVE_UNLOCKED, isChecked).apply()
            updateReceiveUnlockedDescription(isChecked)
            Log.d(TAG, "Unlocked Screen Receive Switch toggled: $isChecked")

            // If both Lock and Unlocked are OFF -> automatically turn OFF Auto-Answer
            if (!isChecked && !binding.switchReceiveLocked.isChecked) {
                if (binding.switchAutoAnswer.isChecked) {
                    prefs.edit().putBoolean(KEY_AUTO_ANSWER, false).apply()
                    binding.switchAutoAnswer.isChecked = false
                    updateAutoAnswerDescription(false)
                }
            }
        }
    }

    private fun updateReceiveUnlockedDescription(isOn: Boolean) {
        if (isOn) {
            binding.tvReceiveUnlockedDesc.text = getString(R.string.main_unlocked_screen_receive_desc_on)
        } else {
            binding.tvReceiveUnlockedDesc.text = getString(R.string.main_unlocked_screen_receive_desc_off)
        }
    }

    private fun setupAutoAnswerSwitch() {
        binding.tvAutoAnswerTitle.text = getString(R.string.main_auto_answer_title)
        val autoAnswerState = prefs.getBoolean(KEY_AUTO_ANSWER, false)
        binding.switchAutoAnswer.isChecked = autoAnswerState
        updateAutoAnswerDescription(autoAnswerState)

        binding.switchAutoAnswer.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Rule 4: At least one of Lock or Unlocked receive must be ON to enable Auto-Answer
                if (!binding.switchReceiveLocked.isChecked && !binding.switchReceiveUnlocked.isChecked) {
                    Log.d(TAG, "Auto-Answer rejected because both Lock and Unlocked receive are OFF.")
                    binding.switchAutoAnswer.isChecked = false
                    showGlassSnackbar(getString(R.string.msg_auto_answer_requires_state))
                    return@setOnCheckedChangeListener
                }
                prefs.edit().putBoolean(KEY_AUTO_ANSWER, true).apply()
                updateAutoAnswerDescription(true)
                Log.d(TAG, "Auto-Answer Switch toggled: true")
            } else {
                prefs.edit().putBoolean(KEY_AUTO_ANSWER, false).apply()
                updateAutoAnswerDescription(false)
                Log.d(TAG, "Auto-Answer Switch toggled: false")
            }
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
        // 1. Bluetooth Connection Status & Device Name
        val (btConnected, deviceName) = getBluetoothConnectionInfo()

        if (btConnected) {
            val userMasterPref = prefs.getBoolean(KEY_MASTER_SWITCH, true)
            if (binding.switchMasterToggle.isChecked != userMasterPref) {
                isProgrammaticSwitchChange = true
                binding.switchMasterToggle.isChecked = userMasterPref
                isProgrammaticSwitchChange = false
            }
            updateSubSwitchesVisibility(userMasterPref)
            if (userMasterPref) {
                try { BluetoothMonitoringService.startService(this) } catch (_: Exception) { }
            } else {
                BluetoothMonitoringService.stopService(this)
            }

            val statusText = if (!deviceName.isNullOrBlank()) {
                "${getString(R.string.main_bt_connected)}: $deviceName"
            } else {
                getString(R.string.main_bt_connected)
            }
            binding.tvBtStatusCardValue.text = statusText
            binding.tvBtStatusCardValue.setTextColor(ContextCompat.getColor(this, R.color.status_green))
            binding.layoutBtStatusItem.contentDescription = "${getString(R.string.main_bt_status_title)}: $statusText"
        } else {
            // Auto-turn off Master Switch when device is disconnected (save battery)
            if (prefs.getBoolean(KEY_MASTER_SWITCH, false)) {
                prefs.edit().putBoolean(KEY_MASTER_SWITCH, false).apply()
            }
            if (binding.switchMasterToggle.isChecked) {
                isProgrammaticSwitchChange = true
                binding.switchMasterToggle.isChecked = false
                isProgrammaticSwitchChange = false
            }
            updateSubSwitchesVisibility(false)
            BluetoothMonitoringService.stopService(this)

            binding.tvBtStatusCardValue.text = getString(R.string.main_bt_disconnected)
            binding.tvBtStatusCardValue.setTextColor(ContextCompat.getColor(this, R.color.status_red))
            binding.layoutBtStatusItem.contentDescription = "${getString(R.string.main_bt_status_title)}: ${getString(R.string.main_bt_disconnected)}"
        }

        // 2. Accessibility Service Status
        val accEnabled = PermissionUtils.isAccessibilityServiceEnabled(this)
        val accStatusText = if (accEnabled) getString(R.string.status_enabled) else getString(R.string.status_disabled_v2)
        binding.tvAccStatusCardValue.text = accStatusText
        binding.tvAccStatusCardValue.setTextColor(ContextCompat.getColor(this, if (accEnabled) R.color.status_green else R.color.status_red))
        binding.layoutAccStatusItem.contentDescription = "${getString(R.string.main_acc_status_title)}: $accStatusText"

        // 3. Notification Listener Access Status
        val notificationAccessEnabled = PermissionUtils.isNotificationListenerEnabled(this)
        val notificationAccessText = if (notificationAccessEnabled) {
            getString(R.string.status_enabled)
        } else {
            getString(R.string.status_disabled_v2)
        }
        binding.tvNotificationAccessStatusCardValue.text = notificationAccessText
        binding.tvNotificationAccessStatusCardValue.setTextColor(
            ContextCompat.getColor(
                this,
                if (notificationAccessEnabled) R.color.status_green else R.color.status_red
            )
        )
        binding.layoutNotificationAccessStatusItem.contentDescription =
            "${getString(R.string.main_notification_access_status_title)}: $notificationAccessText"

        // 4. Cellular Call Answering Permission Status
        val phonePermissionGranted = PermissionUtils.hasAnswerPhoneCallsPermission(this)
        val phonePermissionText = if (phonePermissionGranted) {
            getString(R.string.status_granted)
        } else {
            getString(R.string.status_not_granted_v2)
        }
        binding.tvPhonePermissionStatusCardValue.text = phonePermissionText
        binding.tvPhonePermissionStatusCardValue.setTextColor(
            ContextCompat.getColor(
                this,
                if (phonePermissionGranted) R.color.status_green else R.color.status_red
            )
        )
        binding.layoutPhonePermissionStatusItem.contentDescription =
            "${getString(R.string.main_phone_permission_status_title)}: $phonePermissionText"
    }

    /**
     * Displays an ultra-transparent frosted glass-morphism Snackbar with centered text.
     * Also announces the message immediately to TalkBack screen reader for visually impaired users.
     */
    private fun showGlassSnackbar(message: String) {
        val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
        val snackbarView = snackbar.view
        snackbarView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        snackbarView.background = ContextCompat.getDrawable(this, R.drawable.bg_glass_snackbar_clear)
        snackbarView.elevation = 0f

        val textView = snackbarView.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        if (textView != null) {
            textView.background = null
            textView.textAlignment = View.TEXT_ALIGNMENT_CENTER
            textView.gravity = Gravity.CENTER
            textView.setTextColor(ContextCompat.getColor(this, R.color.white))
            textView.textSize = 14.5f
            textView.maxLines = 3
            textView.setShadowLayer(8f, 0f, 2f, ContextCompat.getColor(this, R.color.black))
        }

        val params = snackbarView.layoutParams as? ViewGroup.MarginLayoutParams
        if (params != null) {
            val marginPx = (18 * resources.displayMetrics.density).toInt()
            val bottomMarginPx = (28 * resources.displayMetrics.density).toInt()
            params.setMargins(marginPx, params.topMargin, marginPx, bottomMarginPx)
            snackbarView.layoutParams = params
        }

        // Announce for blind users using TalkBack
        try {
            window.decorView.announceForAccessibility(message)
        } catch (_: Exception) { }

        snackbar.show()
    }

    @SuppressLint("MissingPermission", "WrongConstant")
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
                }

                if (connectedDev != null) {
                    val headsetConn = try { adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED } catch (e: Exception) { false }
                    val a2dpConn = try { adapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothProfile.STATE_CONNECTED } catch (e: Exception) { false }
                    val isConn = headsetConn || a2dpConn

                    if (isConn) {
                        val name = try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !connectedDev.alias.isNullOrBlank()) {
                                connectedDev.alias
                            } else {
                                connectedDev.name
                            }
                        } catch (e: SecurityException) {
                            null
                        }
                        return Pair(true, name)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching connected Bluetooth device info", e)
        }

        return Pair(false, null)
    }
}
