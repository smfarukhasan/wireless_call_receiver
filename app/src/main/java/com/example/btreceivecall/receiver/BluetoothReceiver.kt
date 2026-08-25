package com.example.btreceivecall.receiver

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.btreceivecall.service.BluetoothMonitoringService
import com.example.btreceivecall.service.CallAccessibilityService

/**
 * BroadcastReceiver monitoring Bluetooth connection states.
 * Automatically activates Master Service when a Bluetooth device connects,
 * and completely disables Master Service & stops background work when disconnected to save 100% battery.
 */
class BluetoothReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BluetoothReceiver"
        private const val PREFS_NAME = "bt_receive_call_prefs"
        const val KEY_MASTER_SWITCH = "master_switch_state"
        const val KEY_AUTO_ANSWER = "auto_answer_state"
        const val KEY_RECEIVE_LOCKED = "receive_locked_state"
        const val KEY_RECEIVE_UNLOCKED = "receive_unlocked_state"

        fun isMasterSwitchOn(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_MASTER_SWITCH, false)
        }

        fun setMasterSwitch(context: Context, enabled: Boolean) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_MASTER_SWITCH, enabled).apply()
        }

        fun isAutoAnswerOn(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_AUTO_ANSWER, false)
        }

        fun isReceiveLockedOn(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_RECEIVE_LOCKED, true)
        }

        fun isReceiveUnlockedOn(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_RECEIVE_UNLOCKED, false)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Bluetooth event received: $action")

        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED,
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                val device: BluetoothDevice? = getBluetoothDeviceExtra(intent)
                Log.d(TAG, "Bluetooth device active event: $action (state=$state, device=${safeDeviceName(device)})")

                if (state == BluetoothProfile.STATE_DISCONNECTED) {
                    if (!isAnyBluetoothDeviceConnected(context)) {
                        handleDeviceDisconnected(context, device)
                    }
                } else if (state == BluetoothProfile.STATE_CONNECTED || action == BluetoothDevice.ACTION_ACL_CONNECTED) {
                    handleDeviceConnected(context, device)
                }
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device: BluetoothDevice? = getBluetoothDeviceExtra(intent)
                Log.d(TAG, "Bluetooth Device Disconnected: ${safeDeviceName(device)}")
                if (!isAnyBluetoothDeviceConnected(context)) {
                    handleDeviceDisconnected(context, device)
                }
            }

            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                    Log.d(TAG, "Bluetooth Adapter turned OFF. Stopping background service.")
                    handleDeviceDisconnected(context, null)
                }
            }
        }
    }

    private fun handleDeviceConnected(context: Context, device: BluetoothDevice?) {
        Log.i(TAG, ">>> Bluetooth device CONNECTED (${safeDeviceName(device)}). Auto-enabling Master Service. <<<")
        setMasterSwitch(context, true)
        try {
            BluetoothMonitoringService.startService(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start BluetoothMonitoringService on connection", e)
        }
    }

    private fun handleDeviceDisconnected(context: Context, device: BluetoothDevice?) {
        Log.i(TAG, ">>> No Bluetooth device connected. Auto-disabling Master Service to save 100% battery. <<<")
        setMasterSwitch(context, false)
        try {
            BluetoothMonitoringService.stopService(context)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to stop BluetoothMonitoringService on disconnection", e)
        }
        CallAccessibilityService.instance?.transitionToIdle("bluetooth disconnected")
    }

    @SuppressLint("MissingPermission")
    private fun isAnyBluetoothDeviceConnected(context: Context): Boolean {
        return try {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return false
            val adapter = bm.adapter ?: return false
            if (!adapter.isEnabled) return false

            val headset = adapter.getProfileConnectionState(BluetoothProfile.HEADSET)
            val a2dp = adapter.getProfileConnectionState(BluetoothProfile.A2DP)
            headset == BluetoothProfile.STATE_CONNECTED || a2dp == BluetoothProfile.STATE_CONNECTED
        } catch (_: Exception) {
            false
        }
    }

    private fun getBluetoothDeviceExtra(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    private fun safeDeviceName(device: BluetoothDevice?): String = try {
        device?.name ?: "Unknown"
    } catch (_: SecurityException) {
        "Unknown"
    }
}
