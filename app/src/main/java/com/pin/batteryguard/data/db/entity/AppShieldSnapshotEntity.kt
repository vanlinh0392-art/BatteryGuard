package com.pin.batteryguard.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Lưu trữ trạng thái hệ thống trước khi ẩn để khôi phục chính xác 100%.
 */
@Entity(tableName = "app_shield_snapshots")
data class AppShieldSnapshotEntity(
    @PrimaryKey val id: Int = 1,
    val isCurrentlyHidden: Boolean = false,
    val hideTimestamp: Long = 0L,
    val timeoutMinutes: Int = 10,
    val triggeredPackage: String = "",
    val originalDevOptionsEnabled: Int = 1,
    val originalAdbEnabled: Int = 1,
    val originalAdbWifiEnabled: Int = 1,
    val originalAccessibilityEnabled: Int = 1,
    val originalAccessibilityServices: String = "",
    val overlaidPackagesDenied: String = "",
    val wasShizukuRunning: Boolean = false
)
