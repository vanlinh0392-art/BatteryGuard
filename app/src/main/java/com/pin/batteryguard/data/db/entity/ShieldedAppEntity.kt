package com.pin.batteryguard.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ứng dụng được bảo vệ (App Whitelist cho chế độ ẩn cài đặt).
 */
@Entity(tableName = "shielded_apps")
data class ShieldedAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isEnabled: Boolean = true,
    val isPresetBank: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)
