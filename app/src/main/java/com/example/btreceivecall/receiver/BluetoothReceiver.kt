package com.example.btreceivecall.receiver

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.btreceivecall.service.BluetoothMonitoringService

/**
 * BroadcastReceiver monitoring Bluetooth connection states (ACL_CONNECTED and ACL_DISCONNECTED).
 * Starts or stops the background monitoring service automatically to conserve battery.
 */
class BluetoothReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BluetoothReceiver"
        private const val PREFS_NAME = "bt_receive_call_prefs"
        private const val KEY_MASTER_SWITCH = "master_switch_state"

        fun isMasterSwitchOn(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_MASTER_SWITCH, true)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Bluetooth event received: $action")

        val isMasterOn = isMasterSwitchOn(context)
        if (!isMasterOn) {
            Log.d(TAG, "Master Switch is OFF. Ignoring event and ensuring service is stopped.")
            BluetoothMonitoringService.stopService(context)
            return
        }

        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val device: BluetoothDevice? = getBluetoothDeviceExtra(intent)
                Log.d(TAG, "Bluetooth Device Connected: ${device?.name ?: "Unknown"}")
                BluetoothMonitoringService.startService(context)
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device: BluetoothDevice? = getBluetoothDeviceExtra(intent)
                Log.d(TAG, "Bluetooth Device Disconnected: ${device?.name ?: "Unknown"}")
                BluetoothMonitoringService.stopService(context)
            }

            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                    Log.d(TAG, "Bluetooth Adapter turned OFF. Stopping background service.")
                    BluetoothMonitoringService.stopService(context)
                }
            }
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
}
