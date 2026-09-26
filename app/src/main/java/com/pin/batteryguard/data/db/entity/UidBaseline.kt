package com.pin.batteryguard.data.db.entity

import androidx.room.Entity

@Entity(tableName = "uid_baselines", primaryKeys = ["sessionId", "uid"])
data class UidBaseline(
    val sessionId: Int = 1,
    val uid: Int,
    val userId: Int,
    val totalPowerMah: Double,
    val foregroundTimeMs: Long = 0L,
    val backgroundTimeMs: Long = 0L,
    val cpuPowerMah: Double = 0.0,
    val wakeLockPowerMah: Double = 0.0,
    val audioPowerMah: Double = 0.0,
    val gnssPowerMah: Double = 0.0,
    val sensorPowerMah: Double = 0.0,
    val consecutiveBreaches: Int = 0
)
