package com.pin.batteryguard.ui.screen.automation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Danh mục định danh cho các quy tắc tự động hóa (Presets)
 */
enum class AutomationRuleId {
    WIFI_AUTO_DATA,        // 📶 Tự động tắt 4G khi có Wi-Fi
    DAY_NIGHT_RINGER,      // 🔔 Tự động chuyển chuông theo giờ
    OVERNIGHT_CHARGING,    // 🔋 Chế độ bảo vệ pin khi sạc qua đêm
    LOW_BATTERY_SAVER,     // ⚡ Tự động tiết kiệm pin khi pin yếu (< 20%)
    DEEP_SCREEN_OFF,       // 📴 Tối ưu sâu khi tắt màn hình
    APP_STOP_MANAGEMENT    // 🛑 Dừng ứng dụng hao pin ngầm
}

/**
 * Trạng thái hoạt động tức thời của Rule
 */
enum class RuleActiveStatus {
    ACTIVE,    // Đang thực thi/áp dụng (Active / Running)
    IDLE,      // Đang chờ thỏa mãn điều kiện trigger (Idle / Standby)
    DISABLED   // Đã tắt hoặc Master Switch đang tắt
}

enum class RingerTargetMode {
    VIBRATE,
    SILENT
}

/**
 * Lựa chọn SIM để bật/tắt dữ liệu di động
 */
enum class TargetSimSelection {
    AUTO,   // Tự động nhận diện SIM dữ liệu đang dùng (Default Data SIM)
    SIM_1,  // Cố định SIM 1 (Slot 0)
    SIM_2   // Cố định SIM 2 (Slot 1)
}

/**
 * Thông tin SIM được phát hiện trên thiết bị
 */
data class SimInfoItem(
    val slotIndex: Int,
    val subId: Int,
    val displayName: String,
    val carrierName: String
)

/**
 * Marker interface cho cấu hình tham số của từng rule
 */
sealed interface AutomationConfigParams

/**
 * 1. Tham số Tự động tắt 4G khi có Wi-Fi
 */
data class WifiAutoDataParams(
    val delaySeconds: Int = 5,
    val restoreDelaySeconds: Int = 3,
    val autoRestoreDataOnDisconnect: Boolean = true,
    val targetSim: TargetSimSelection = TargetSimSelection.AUTO,
    val targetSubId: Int = -1, // -1 = Auto
    val requireShizuku: Boolean = true
) : AutomationConfigParams

/**
 * 2. Tham số Tự động chuyển chuông theo giờ
 */
data class DayNightRingerParams(
    val startHour: Int = 23,
    val startMinute: Int = 0,
    val endHour: Int = 6,
    val endMinute: Int = 30,
    val nightMode: RingerTargetMode = RingerTargetMode.VIBRATE,
    val applyOnWeekends: Boolean = true
) : AutomationConfigParams

/**
 * 3. Tham số Bảo vệ pin khi sạc qua đêm
 */
data class OvernightChargingParams(
    val maxChargeLimitPercent: Int = 80,
    val alertSoundOnTarget: Boolean = true,
    val startHour: Int = 22,
    val endHour: Int = 7,
    val preventOverheat: Boolean = true,
    val fastCharge90WEnabled: Boolean = true,
    val prioritizeFastChargeNoThrottle: Boolean = false,
    val criticalThermalLimit: Float = 48.0f,
    val syncOnChargeOnly: Boolean = false
) : AutomationConfigParams

/**
 * 4. Tham số Tự động tiết kiệm pin khi pin yếu
 */
data class LowBatterySaverParams(
    val thresholdPercent: Int = 20,
    val dimDisplayBrightness: Boolean = true,
    val capRefreshRate60Hz: Boolean = true,
    val enableSystemPowerSaver: Boolean = true,
    val turnOffAodAndRadios: Boolean = true,
    val restrictBackgroundSync: Boolean = true,
    val smoothSaver120Hz: Boolean = false
) : AutomationConfigParams

/**
 * 5. Tham số Tối ưu sâu khi tắt màn hình
 */
data class DeepScreenOffParams(
    val delayMinutes: Int = 3,
    val forceStopBackgroundDrainers: Boolean = true,
    val enableDeepDoze: Boolean = true,
    val turnOffHotspotIfIdle: Boolean = true,
    val restoreBrightnessOnScreenOn: Boolean = true,
    val screenOnBrightnessIncreasePercent: Int = 10,
    val instantDeepDoze: Boolean = true,
    val gmsDozeSafeMode: Boolean = false,
    val turnOffDataOnScreenOff: Boolean = false,
    val disableBackgroundScanning: Boolean = false
) : AutomationConfigParams

/**
 * 6. Tham số Quản lý dừng ứng dụng hao pin ngầm
 */
data class AppStopManagementParams(
    val drainThresholdPercent: Float = 1.0f,
    val monitoringPeriodMinutes: Int = 15,
    val autoForceStop: Boolean = true,
    val enableFreezeMode: Boolean = false,
    val notifyBeforeStop: Boolean = true,
    val excludeSystemApps: Boolean = true,
    val smartAutoFreeze: Boolean = true,
    val freezeXiaomiTelemetry: Boolean = false,
    val freezeXiaomiInterconnect: Boolean = false,
    val stopExceptionsIfOverThreshold: Boolean = false,
    val exceptionDrainThresholdPercent: Float = 5.0f
) : AutomationConfigParams

/**
 * UI Model đại diện cho 1 Automation Card
 */
data class AutomationRuleUiModel(
    val id: AutomationRuleId,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val isEnabled: Boolean,
    val activeStatus: RuleActiveStatus,
    val statusBadgeText: String,
    val statusDetail: String,
    val params: AutomationConfigParams
)

/**
 * Toàn bộ UI State của màn hình AutomationScreen
 */
data class AutomationUiState(
    val isMasterEnabled: Boolean = true,
    val rules: List<AutomationRuleUiModel> = emptyList(),
    val activeRulesCount: Int = 0,
    val enabledRulesCount: Int = 0,
    val estimatedBatterySavingsPercent: Int = 22,
    val expandedRuleId: AutomationRuleId? = null,
    val isShizukuReady: Boolean = false,
    val hasWriteSecureSettings: Boolean = false,
    val currentBatteryPercent: Int = 75,
    val isCurrentlyCharging: Boolean = false,
    val isWifiConnected: Boolean = true,
    val availableSims: List<SimInfoItem> = emptyList(),
    val autoSortActiveToTop: Boolean = true,
    val snackbarMessage: String? = null
)
