package com.pin.batteryguard.domain.model

data class BatteryState(
    val level: Int = 0,
    val temperature: Float = 0f,
    val voltage: Int = 0,
    val currentNow: Long = 0,          // µA (tức thời)
    val currentAvg: Long = 0,          // µA (trung bình)
    val isCharging: Boolean = false,
    val chargeType: String = "None",   // USB/AC/Wireless
    val health: Int = 0,
    val technology: String = ""
)
