package com.pin.batteryguard.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "xiaomi_fix_snapshots")
data class XiaomiFixSnapshotEntity(
    @PrimaryKey val packageName: String,
    val uid: Int,
    val capturedAt: Long = System.currentTimeMillis(),
    val originalOp10053: Int,         // 0 = allow, 1 = ignore, 2 = deny, 3 = default
    val originalOp10008: Int,
    val originalStandbyBucket: Int,    // 10 = active, 20 = working_set, 30 = frequent, 40 = rare, 45 = restricted
    val wasInDozeWhitelist: Boolean,
    val wasInNetpolicy: Boolean
)
