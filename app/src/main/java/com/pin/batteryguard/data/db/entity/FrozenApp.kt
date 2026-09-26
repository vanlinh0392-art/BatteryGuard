package com.pin.batteryguard.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "frozen_apps", primaryKeys = ["userId", "packageName"])
data class FrozenApp(
    val userId: Int = 0,
    val packageName: String,
    val appName: String,
    val frozenAt: Long = System.currentTimeMillis()
)
