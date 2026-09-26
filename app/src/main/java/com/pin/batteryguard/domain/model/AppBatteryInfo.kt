package com.pin.batteryguard.domain.model

import android.graphics.drawable.Drawable

data class AppBatteryInfo(
    val packageName: String,
    val appName: String,
    val appIcon: Drawable?,
    val drainPercent: Float,
    val foregroundTimeMs: Long,
    val backgroundTimeMs: Long,
    val isRunning: Boolean,
    val isException: Boolean,
    val isFrozen: Boolean = false,
    val uid: Int = 0,
    val userId: Int = 0,
    val packageNames: List<String> = listOf(packageName),
    val isSharedUid: Boolean = false,
    val deltaMah: Double = 0.0,
    val ratePercentPerHour: Double = 0.0,
    val evidence: String = "",
    val decision: String = "",
    val isActionAllowed: Boolean = true,
    val activeUseOverride: Boolean = false
)
