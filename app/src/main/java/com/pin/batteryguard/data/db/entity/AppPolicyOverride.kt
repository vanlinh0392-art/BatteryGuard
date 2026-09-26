package com.pin.batteryguard.data.db.entity

import androidx.room.Entity

@Entity(tableName = "app_policy_overrides", primaryKeys = ["userId", "packageName"])
data class AppPolicyOverride(
    val userId: Int,
    val packageName: String,
    val allowActiveUseStop: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
