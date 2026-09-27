package com.pin.batteryguard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager

class ScreenStateReceiver(
    private val onScreenEvent: (action: String, batteryLevel: Int) -> Unit,
    private val onBatteryChanged: (level: Int, temperature: Float, isCharging: Boolean) -> Unit
) : BroadcastReceiver() {

    // ── H5: Throttle ACTION_BATTERY_CHANGED ─────────────────────────────
    private var lastBatteryLevel = -1
    private var lastIsCharging = false
    private var lastRawTemperature = -1 // raw (x10), so 5 units = 0.5°C
    // ─────────────────────────────────────────────────────────────────────

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
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

            // H5: Only forward when something meaningful changed
            val tempDelta = if (lastRawTemperature < 0) Int.MAX_VALUE
                            else kotlin.math.abs(rawTemp - lastRawTemperature)
            if (batteryPct == lastBatteryLevel && isCharging == lastIsCharging && tempDelta < 5) {
                return // nothing changed — skip downstream work
            }
            lastBatteryLevel = batteryPct
            lastIsCharging = isCharging
            lastRawTemperature = rawTemp

            val temperature = rawTemp / 10f
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
