package com.pin.batteryguard.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Bản sao lưu trạng thái quyền (Permission Snapshot) để Rollback 1-chạm chuẩn ACID.
 * Lưu trữ trạng thái quyền Runtime, AppOps, Doze Whitelist và Standby Bucket trước khi can thiệp.
 */
@Entity(
    tableName = "permission_snapshots",
    indices = [
        Index(value = ["packageName", "capturedAt"]),
        Index(value = ["packageName"])
    ]
)
data class PermissionSnapshotEntity(
    @PrimaryKey val id: String, // format: "snap_${packageName}_${timestamp}"
    val packageName: String,
    val appName: String,
    val capturedAt: Long = System.currentTimeMillis(),
    val formattedDate: String,
    val grantedPermissionsJson: String, // List<String> JSON (danh sách tên quyền đã granted)
    val appOpsStatesJson: String,       // Map<String, String> JSON (ví dụ {"SYSTEM_ALERT_WINDOW":"allow"})
    val isBatteryWhitelisted: Boolean,
    val standbyBucket: Int,             // 10 = ACTIVE, 20 = WORKING_SET, 40 = RARE
    val snapshotReason: String          // "PRE_GRANT_ALL", "PRESET_APPLIED", "MANUAL"
)
