package com.pin.batteryguard.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "exception_apps")
data class ExceptionApp(
    @PrimaryKey val packageName: String,
    val appName: String,
    val reason: String = "",           // Lý do cho vào ngoại lệ
    val isSystemDefault: Boolean = false, // Tránh force stop các dịch vụ hệ thống core
    val addedAt: Long = System.currentTimeMillis()
)
