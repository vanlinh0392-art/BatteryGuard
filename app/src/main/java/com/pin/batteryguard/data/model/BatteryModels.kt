package com.pin.batteryguard.data.model

/**
 * Thông tin pin chi tiết của từng ứng dụng, đã tính % hao pin
 */
data class AppBatteryInfo(
    val uid: Int,
    val packageName: String,
    val appName: String = "",
    val cpuTimeMs: Long = 0L,
    val wakeLockMs: Long = 0L,
    val mobileBytes: Long = 0L,
    val wifiBytes: Long = 0L,
    val gpsMs: Long = 0L,
    val sensorMs: Long = 0L,
    val drainPercent: Float = 0f,
    val score: Double = 0.0
)

/**
 * Dữ liệu thô từ dumpsys batterystats, chưa tính %
 */
data class RawAppStat(
    val uid: Int,
    val packageName: String,
    val cpuTimeMs: Long = 0L,
    val wakeLockMs: Long = 0L,
    val mobileBytes: Long = 0L,
    val wifiBytes: Long = 0L,
    val gpsMs: Long = 0L,
    val sensorMs: Long = 0L
)

/**
 * Dùng khi fallback - ước lượng hao pin từ UsageStatsManager
 */
data class AppUsageLog(
    val packageName: String,
    val appName: String = "",
    val foregroundTimeMs: Long = 0L,
    val estimatedDrainPercent: Float = 0f
)

/**
 * Log mỗi lần force-stop / freeze
 */
data class ForceStopLogEntry(
    val packageName: String,
    val appName: String = "",
    val action: StopAction,
    val timestamp: Long = System.currentTimeMillis(),
    val success: Boolean = true,
    val drainPercent: Float = 0f,
    val reason: String = ""
)

enum class StopAction {
    FORCE_STOP,
    FREEZE,
    UNFREEZE
}

/**
 * Cấu hình giám sát pin - đọc từ DataStore
 */
data class MonitoringConfig(
    val isEnabled: Boolean = true,
    val monitoringPeriodMinutes: Int = 15,
    val drainThresholdPercent: Float = 3f,
    val autoForceStop: Boolean = true,
    val useFreeze: Boolean = false,
    val dataRetentionDays: Int = 30,
    val exceptionPackages: Set<String> = emptySet()
)

/**
 * Bước hướng dẫn setup (dành cho Xiaomi / HyperOS)
 */
data class SetupStep(
    val title: String,
    val description: String,
    val action: (() -> Unit)? = null
)

/**
 * Trạng thái state machine của BatteryMonitorService
 */
enum class ServiceState {
    IDLE,       // Service chạy nhưng không giám sát (màn hình BẬT)
    MONITORING, // Màn hình TẮT, đang theo dõi pin
    SCANNING    // Đang quét kiểm tra pin
}

/**
 * Snapshot pin tại thời điểm nhất định
 */
data class BatterySnapshot(
    val level: Int,
    val temperature: Int = 0,
    val voltage: Int = 0,
    val isCharging: Boolean = false,
    val chargeType: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
