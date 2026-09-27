package com.pin.batteryguard.ui.screen.automation

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.Wifi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pin.batteryguard.automation.AutomationCoordinator
import com.pin.batteryguard.automation.AutomationDataStore
import com.pin.batteryguard.automation.AutomationMasterConfig
import com.pin.batteryguard.automation.SimHelper
import com.pin.batteryguard.automation.SystemActionBridge
import com.pin.batteryguard.shizuku.ShizukuManager
import com.pin.batteryguard.shizuku.ShizukuStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutomationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val automationDataStore: AutomationDataStore,
    private val automationCoordinator: AutomationCoordinator,
    private val shizukuManager: ShizukuManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AutomationUiState())
    val uiState: StateFlow<AutomationUiState> = _uiState.asStateFlow()

    init {
        detectAvailableSims()
        observeShizukuStatus()
        observeDataStoreAndCoordinator()
        refreshBatteryState()
        triggerAutoGrant()
    }

    private fun detectAvailableSims() {
        val detected = SimHelper.getDetectedSims(context)
        _uiState.update { it.copy(availableSims = detected) }
    }

    private fun observeShizukuStatus() {
        viewModelScope.launch {
            shizukuManager.status.collectLatest { status ->
                var hasSecure = SystemActionBridge.hasWriteSecureSettings(context)

                // Tự động cấp quyền WRITE_SECURE_SETTINGS khi đủ điều kiện ADB / Shizuku
                if (status == ShizukuStatus.READY && !hasSecure) {
                    shizukuManager.grantSystemPermissions { granted ->
                        if (granted) {
                            _uiState.update { it.copy(hasWriteSecureSettings = true) }
                        }
                    }
                    hasSecure = SystemActionBridge.hasWriteSecureSettings(context)
                }

                _uiState.update {
                    it.copy(
                        isShizukuReady = status == ShizukuStatus.READY,
                        hasWriteSecureSettings = hasSecure
                    )
                }
            }
        }
    }

    /**
     * Chủ động thử tự động cấp WRITE_SECURE_SETTINGS khi đủ điều kiện ADB (Shizuku hoặc Local ADB)
     */
    fun triggerAutoGrant() {
        viewModelScope.launch {
            if (shizukuManager.isReady()) {
                shizukuManager.grantSystemPermissions { granted ->
                    if (granted) {
                        _uiState.update { it.copy(hasWriteSecureSettings = true) }
                    }
                }
            } else {
                val granted = SystemActionBridge.autoGrantWriteSecureSettingsIfAdbAvailable(context)
                if (granted) {
                    _uiState.update { it.copy(hasWriteSecureSettings = true) }
                } else if (!shizukuManager.checkPermission()) {
                    shizukuManager.requestPermission(101)
                }
            }
        }
    }

    private fun observeDataStoreAndCoordinator() {
        viewModelScope.launch {
            combine(
                automationDataStore.configFlow,
                automationCoordinator.ruleStates,
                automationCoordinator.isWifiConnected
            ) { config, runtimeStates, isWifi ->
                Triple(config, runtimeStates, isWifi)
            }.collectLatest { (config, runtimeStates, isWifi) ->
                updateUiWithConfigAndRuntime(config, runtimeStates, isWifi)
            }
        }
    }

    private fun updateUiWithConfigAndRuntime(
        config: AutomationMasterConfig,
        runtimeStates: Map<AutomationRuleId, com.pin.batteryguard.automation.RuleRuntimeState>,
        isWifi: Boolean
    ) {
        val rules = listOf(
            AutomationRuleUiModel(
                id = AutomationRuleId.WIFI_AUTO_DATA,
                title = "Tắt 4G khi có Wi-Fi",
                subtitle = "Tự ngắt dữ liệu mạng di động khi Wi-Fi ổn định",
                icon = Icons.Filled.Wifi,
                isEnabled = config.wifiAutoDataEnabled,
                activeStatus = if (!config.isMasterEnabled || !config.wifiAutoDataEnabled) RuleActiveStatus.DISABLED
                else runtimeStates[AutomationRuleId.WIFI_AUTO_DATA]?.status ?: RuleActiveStatus.IDLE,
                statusBadgeText = if (!config.isMasterEnabled || !config.wifiAutoDataEnabled) "Đã tắt"
                else runtimeStates[AutomationRuleId.WIFI_AUTO_DATA]?.badgeText ?: "Sẵn sàng",
                statusDetail = if (!config.isMasterEnabled || !config.wifiAutoDataEnabled) "Đã tắt"
                else runtimeStates[AutomationRuleId.WIFI_AUTO_DATA]?.detailText ?: "SIM: ${config.wifiAutoDataParams.targetSim.name}",
                params = config.wifiAutoDataParams
            ),
            AutomationRuleUiModel(
                id = AutomationRuleId.DAY_NIGHT_RINGER,
                title = "Chuyển chuông theo giờ",
                subtitle = "Tự động chuyển Rung / Im lặng ban đêm",
                icon = Icons.Filled.NotificationsActive,
                isEnabled = config.dayNightRingerEnabled,
                activeStatus = if (!config.isMasterEnabled || !config.dayNightRingerEnabled) RuleActiveStatus.DISABLED
                else runtimeStates[AutomationRuleId.DAY_NIGHT_RINGER]?.status ?: RuleActiveStatus.IDLE,
                statusBadgeText = if (!config.isMasterEnabled || !config.dayNightRingerEnabled) "Đã tắt"
                else runtimeStates[AutomationRuleId.DAY_NIGHT_RINGER]?.badgeText ?: "Sẵn sàng",
                statusDetail = if (!config.isMasterEnabled || !config.dayNightRingerEnabled) "Đã tắt"
                else runtimeStates[AutomationRuleId.DAY_NIGHT_RINGER]?.detailText ?: "Khung giờ: ${String.format("%02d:%02d", config.dayNightRingerParams.startHour, config.dayNightRingerParams.startMinute)} - ${String.format("%02d:%02d", config.dayNightRingerParams.endHour, config.dayNightRingerParams.endMinute)}",
                params = config.dayNightRingerParams
            ),
            AutomationRuleUiModel(
                id = AutomationRuleId.OVERNIGHT_CHARGING,
                title = "Bảo vệ pin sạc qua đêm",
                subtitle = "Cảnh báo ngắt sạc khi pin đạt ngưỡng an toàn",
                icon = Icons.Filled.BatteryChargingFull,
                isEnabled = config.overnightChargingEnabled,
                activeStatus = if (!config.isMasterEnabled || !config.overnightChargingEnabled) RuleActiveStatus.DISABLED
                else runtimeStates[AutomationRuleId.OVERNIGHT_CHARGING]?.status ?: RuleActiveStatus.IDLE,
                statusBadgeText = if (!config.isMasterEnabled || !config.overnightChargingEnabled) "Đã tắt"
                else runtimeStates[AutomationRuleId.OVERNIGHT_CHARGING]?.badgeText ?: "Sẵn sàng",
                statusDetail = if (!config.isMasterEnabled || !config.overnightChargingEnabled) "Đã tắt"
                else runtimeStates[AutomationRuleId.OVERNIGHT_CHARGING]?.detailText ?: "Giới hạn khuyến nghị: ${config.overnightChargingParams.maxChargeLimitPercent}%",
                params = config.overnightChargingParams
            ),
            AutomationRuleUiModel(
                id = AutomationRuleId.LOW_BATTERY_SAVER,
                title = "Tiết kiệm pin khi pin yếu",
                subtitle = "Tự bật Tiết kiệm pin & hạ màn hình 60Hz",
                icon = Icons.Filled.BatterySaver,
                isEnabled = config.lowBatterySaverEnabled,
                activeStatus = if (!config.isMasterEnabled || !config.lowBatterySaverEnabled) RuleActiveStatus.DISABLED
                else runtimeStates[AutomationRuleId.LOW_BATTERY_SAVER]?.status ?: RuleActiveStatus.IDLE,
                statusBadgeText = if (!config.isMasterEnabled || !config.lowBatterySaverEnabled) "Đã tắt"
                else runtimeStates[AutomationRuleId.LOW_BATTERY_SAVER]?.badgeText ?: "Sẵn sàng",
                statusDetail = if (!config.isMasterEnabled || !config.lowBatterySaverEnabled) "Đã tắt"
                else runtimeStates[AutomationRuleId.LOW_BATTERY_SAVER]?.detailText ?: "Kích hoạt khi pin ≤ ${config.lowBatterySaverParams.thresholdPercent}%",
                params = config.lowBatterySaverParams
            ),
            AutomationRuleUiModel(
                id = AutomationRuleId.DEEP_SCREEN_OFF,
                title = "Tối ưu sâu khi tắt màn hình",
                subtitle = "Kích hoạt Deep Doze sớm sau khi khoá máy",
                icon = Icons.Filled.ScreenLockPortrait,
                isEnabled = config.deepScreenOffEnabled,
                activeStatus = if (!config.isMasterEnabled || !config.deepScreenOffEnabled) RuleActiveStatus.DISABLED
                else runtimeStates[AutomationRuleId.DEEP_SCREEN_OFF]?.status ?: RuleActiveStatus.IDLE,
                statusBadgeText = if (!config.isMasterEnabled || !config.deepScreenOffEnabled) "Đã tắt"
                else runtimeStates[AutomationRuleId.DEEP_SCREEN_OFF]?.badgeText ?: "Sẵn sàng",
                statusDetail = if (!config.isMasterEnabled || !config.deepScreenOffEnabled) "Đã tắt"
                else runtimeStates[AutomationRuleId.DEEP_SCREEN_OFF]?.detailText ?: "Kích hoạt sau ${config.deepScreenOffParams.delayMinutes} phút tắt màn hình",
                params = config.deepScreenOffParams
            )
        )

        val sortedRules = if (config.autoSortActiveToTop) {
            rules.sortedWith(
                compareByDescending<AutomationRuleUiModel> { rule ->
                    when {
                        !config.isMasterEnabled || !rule.isEnabled -> 0
                        rule.activeStatus == RuleActiveStatus.ACTIVE -> 2
                        else -> 1
                    }
                }.thenBy { it.id.ordinal }
            )
        } else {
            rules
        }

        val activeCount = rules.count { it.isEnabled && it.activeStatus == RuleActiveStatus.ACTIVE }
        val enabledCount = rules.count { it.isEnabled }

        _uiState.update { current ->
            current.copy(
                isMasterEnabled = config.isMasterEnabled,
                rules = sortedRules,
                activeRulesCount = activeCount,
                enabledRulesCount = enabledCount,
                isWifiConnected = isWifi,
                autoSortActiveToTop = config.autoSortActiveToTop,
                hasWriteSecureSettings = SystemActionBridge.hasWriteSecureSettings(context)
            )
        }
    }

    fun refreshLiveSensorStatus() {
        refreshBatteryState()
        detectAvailableSims()
    }

    fun refreshBatteryState() {
        try {
            val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, batteryFilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 75
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
            val batteryPct = if (scale > 0) (level * 100 / scale) else 75
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            _uiState.update {
                it.copy(
                    currentBatteryPercent = batteryPct,
                    isCurrentlyCharging = isCharging
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Bật/Tắt Master Toggle toàn bộ hệ thống tự động
     */
    fun toggleMaster(enabled: Boolean) {
        viewModelScope.launch {
            automationDataStore.setMasterEnabled(enabled)
            _uiState.update {
                it.copy(snackbarMessage = if (enabled) "Đã kích hoạt toàn bộ tự động hóa" else "Đã tạm dừng toàn bộ tự động hóa")
            }
        }
    }

    /**
     * Bật/Tắt nhanh 1 Rule cụ thể (1 chạm)
     */
    fun toggleRule(ruleId: AutomationRuleId, enabled: Boolean) {
        viewModelScope.launch {
            automationDataStore.setRuleEnabled(ruleId, enabled)
            _uiState.update {
                it.copy(snackbarMessage = "Đã ${if (enabled) "bật" else "tắt"} module tự động")
            }
        }
    }

    /**
     * Mở rộng / thu gọn panel cấu hình của Rule
     */
    fun toggleExpandRule(ruleId: AutomationRuleId) {
        _uiState.update { state ->
            state.copy(
                expandedRuleId = if (state.expandedRuleId == ruleId) null else ruleId
            )
        }
    }

    /**
     * Cập nhật tham số cấu hình của Rule
     */
    fun updateRuleParams(ruleId: AutomationRuleId, newParams: AutomationConfigParams) {
        viewModelScope.launch {
            when (ruleId) {
                AutomationRuleId.WIFI_AUTO_DATA -> {
                    (newParams as? WifiAutoDataParams)?.let {
                        automationDataStore.updateWifiAutoDataParams(it)
                    }
                }
                AutomationRuleId.DAY_NIGHT_RINGER -> {
                    (newParams as? DayNightRingerParams)?.let {
                        automationDataStore.updateDayNightRingerParams(it)
                    }
                }
                AutomationRuleId.OVERNIGHT_CHARGING -> {
                    (newParams as? OvernightChargingParams)?.let {
                        automationDataStore.updateOvernightChargingParams(it)
                    }
                }
                AutomationRuleId.LOW_BATTERY_SAVER -> {
                    (newParams as? LowBatterySaverParams)?.let {
                        automationDataStore.updateLowBatterySaverParams(it)
                    }
                }
                AutomationRuleId.DEEP_SCREEN_OFF -> {
                    (newParams as? DeepScreenOffParams)?.let {
                        automationDataStore.updateDeepScreenOffParams(it)
                    }
                }
            }
            _uiState.update { it.copy(snackbarMessage = "Đã lưu cài đặt mới") }
        }
    }

    /**
     * Bật/Tắt chế độ tự động sắp xếp đẩy module Active lên trên
     */
    fun toggleAutoSortActive() {
        viewModelScope.launch {
            val nextState = !_uiState.value.autoSortActiveToTop
            automationDataStore.setAutoSortActiveToTop(nextState)
            _uiState.update {
                it.copy(
                    autoSortActiveToTop = nextState,
                    snackbarMessage = if (nextState) "Đã bật tự động ưu tiên module đang chạy lên đầu"
                    else "Đã chuyển về thứ tự hiển thị mặc định"
                )
            }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
