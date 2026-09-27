package com.pin.batteryguard.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "force_stop_logs",
    indices = [
        Index(value = ["packageName", "userId", "verified", "timestamp"])
    ]
)
data class ForceStopLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val drainPercent: Float,
    val action: String,                // "force_stop" hoặc "freeze"
    val success: Boolean,
    val reason: String,                // "threshold_exceeded" hoặc "manual"
    val timestamp: Long = System.currentTimeMillis(),
    val userId: Int = 0,
    val uid: Int = 0,
    val deltaMah: Double = 0.0,
    val ratePercentPerHour: Double = 0.0,
    val verified: Boolean = success,
    val exitCode: Int = 0,
    val errorMessage: String? = null
)
