package com.example.btreceivecall.receiver

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.example.btreceivecall.service.BluetoothMonitoringService
import com.example.btreceivecall.service.CallAccessibilityService

/**
 * BroadcastReceiver monitoring Bluetooth connection states and headset events.
 * Starts or stops the background monitoring service automatically to conserve battery.
 */
class BluetoothReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BluetoothReceiver"
        private const val PREFS_NAME = "bt_receive_call_prefs"
        private const val KEY_MASTER_SWITCH = "master_switch_state"
        private const val KEY_AUTO_ANSWER = "auto_answer_state"

        fun isMasterSwitchOn(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_MASTER_SWITCH, true)
        }

        fun isAutoAnswerOn(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_AUTO_ANSWER, false)
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

            BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED -> {
                val audioState = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, -1)
                Log.i(TAG, ">>> Headset Audio State Changed: $audioState <<<")
                if (audioState == BluetoothHeadset.STATE_AUDIO_CONNECTED || audioState == BluetoothHeadset.STATE_AUDIO_CONNECTING) {
                    val service = CallAccessibilityService.instance
                    if (service != null && service.isCallActive) {
                        Log.i(TAG, "Headset audio connected during ringing -> Answering call...")
                        val answered = service.performAnswerCallAction()
                        if (answered) {
                            service.announceCallAnswered()
                        }
                    }
                }
            }

            AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED -> {
                val scoState = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
                Log.i(TAG, ">>> SCO Audio State Updated: $scoState <<<")
                if (scoState == AudioManager.SCO_AUDIO_STATE_CONNECTED) {
                    val service = CallAccessibilityService.instance
                    if (service != null && service.isCallActive) {
                        Log.i(TAG, "SCO audio connected during ringing -> Answering call...")
                        val answered = service.performAnswerCallAction()
                        if (answered) {
                            service.announceCallAnswered()
                        }
                    }
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
