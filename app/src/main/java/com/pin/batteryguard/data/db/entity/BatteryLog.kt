package com.pin.batteryguard.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "battery_logs")
data class BatteryLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val level: Int,                    // 0-100%
    val temperature: Float,            // Celsius
    val voltage: Int,                  // mV  
    val currentNow: Long,              // µA instant
    val currentAvg: Long,              // µA average
    val isCharging: Boolean,
    val chargeType: String,            // USB/AC/Wireless/None
    val screenOn: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
