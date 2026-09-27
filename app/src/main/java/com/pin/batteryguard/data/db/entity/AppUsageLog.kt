package com.pin.batteryguard.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_usage_logs",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["periodEnd"]),
        Index(value = ["packageName", "timestamp"])
    ]
)
data class AppUsageLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val foregroundTimeMs: Long,        // Foreground time in milliseconds
    val backgroundTimeMs: Long,        // Background time in milliseconds
    val estimatedDrainPercent: Float,  // Estimated battery drain in percentage
    val cpuTimeMs: Long = 0,           // CPU time from dumpsys
    val networkBytes: Long = 0,        // Network bytes from dumpsys
    val wakeLockMs: Long = 0,          // Wake lock duration in milliseconds
    val periodStart: Long,             // Period start timestamp
    val periodEnd: Long,               // Period end timestamp
    val timestamp: Long = System.currentTimeMillis(),
    val userId: Int = 0,
    val uid: Int = 0,
    val deltaMah: Double = 0.0,
    val ratePercentPerHour: Double = 0.0,
    val evidence: String = "",
    val decision: String = "",
    val skipReason: String = ""
)
