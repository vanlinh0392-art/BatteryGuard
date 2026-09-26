package com.pin.batteryguard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager

class ScreenStateReceiver(
    private val onScreenEvent: (action: String, batteryLevel: Int) -> Unit,
    private val onBatteryChanged: (level: Int, temperature: Float, isCharging: Boolean) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        
        // Trích xuất battery level
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            getBackupBatteryLevel(context)
        }

        if (action == Intent.ACTION_BATTERY_CHANGED) {
            val rawTemp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
            val temperature = rawTemp / 10f
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            onBatteryChanged(batteryPct, temperature, isCharging)
        } else {
            when (action) {
                Intent.ACTION_SCREEN_OFF -> {
                    onScreenEvent(Intent.ACTION_SCREEN_OFF, batteryPct)
                }
                Intent.ACTION_SCREEN_ON -> {
                    onScreenEvent(Intent.ACTION_SCREEN_ON, batteryPct)
                }
            }
        }
    }

    private fun getBackupBatteryLevel(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}
