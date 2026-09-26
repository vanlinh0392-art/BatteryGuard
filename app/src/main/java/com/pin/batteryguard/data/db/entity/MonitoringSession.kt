package com.pin.batteryguard.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "monitoring_sessions")
data class MonitoringSession(
    @PrimaryKey val id: Int = 1,
    val startedAtMillis: Long,
    val startedAtElapsedRealtime: Long,
    val lastCapturedAtMillis: Long,
    val lastCapturedAtElapsedRealtime: Long,
    val statsStartFingerprint: String,
    val capacityMah: Double,
    val chargeCounterUah: Long? = null,
    val isScreenOff: Boolean = true,
    val isCharging: Boolean = false
)
