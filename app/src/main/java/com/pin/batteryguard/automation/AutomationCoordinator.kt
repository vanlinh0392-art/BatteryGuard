package com.pin.batteryguard.automation

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pin.batteryguard.R
import com.pin.batteryguard.shizuku.ShizukuManager
import com.pin.batteryguard.shizuku.executeShizukuCommandWithTimeout
import com.pin.batteryguard.ui.screen.automation.AutomationRuleId
import com.pin.batteryguard.ui.screen.automation.RingerTargetMode
import com.pin.batteryguard.ui.screen.automation.RuleActiveStatus
import com.pin.batteryguard.ui.screen.automation.TargetSimSelection
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AutomationCoordinator"

data class RuleRuntimeState(
    val status: RuleActiveStatus = RuleActiveStatus.IDLE,
    val badgeText: String = "Sẵn sàng",
    val detailText: String = ""
)

@Singleton
class AutomationCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val automationDataStore: AutomationDataStore,
    private val shizukuManager: ShizukuManager
) {
    private var scope: CoroutineScope? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var ringerAlarmReceiver: BroadcastReceiver? = null

    // Jobs quản lý Debounce & Trì hoãn
    private var wifiDataDisableJob: Job? = null
    private var wifiDataRestoreJob: Job? = null
    private var screenOffDozeJob: Job? = null
    private var instantDozeJob: Job? = null
    @Volatile private var isGmsSafeModeApplied = false

    // Cache cấu hình đang áp dụng
    @Volatile private var currentConfig = AutomationMasterConfig()
    @Volatile private var isStarted = false

    // Trạng thái phần cứng tức thời
    private val _isWifiConnected = MutableStateFlow(false)
    val isWifiConnected = _isWifiConnected.asStateFlow()

    private val _ruleStates = MutableStateFlow<Map<AutomationRuleId, RuleRuntimeState>>(
        AutomationRuleId.entries.associateWith { RuleRuntimeState() }
    )
    val ruleStates = _ruleStates.asStateFlow()

    private var currentBatteryLevel = 100
    private var isCurrentlyCharging = false
    private var hasNotifiedOvernightCharging = false
    private var lastScreenOffTime = 0L

    // Cache độ sáng độc lập cho từng module chống xung đột trạng thái (ARC Architect Fix)
    private var cachedBrightnessForBatterySaver: Int? = null
    @Volatile private var cachedBrightnessForScreenOff: Int? = null
    @Volatile private var lastPreBoostBrightness: Int? = null
    @Volatile private var lastBoostedBrightness: Int? = null
    @Volatile private var lastBoostTimestamp = 0L

    // Cờ nhớ trạng thái chuông đã áp dụng để chống gọi thừa IPC shell khi bật màn hình (Idempotency)
    private var lastAppliedRingerIsNight: Boolean? = null

    // C2: Guard flag chống coroutine flooding khi pin yếu
    @Volatile private var isPowerSaverApplied = false

    // Smart Thermal: Cờ hạ công suất sạc 90W khi quá nhiệt
    @Volatile private var isFastChargeThrottled = false
    private var wasFastChargeOriginallyEnabled: Boolean? = null
    @Volatile private var isCriticalThermalThrottled = false
    @Volatile private var hasAppliedThermalOverride = false

    // Cache trạng thái dark mode gốc trước khi vào low battery saver
    private var cachedWasDarkModeForBatterySaver: Boolean? = null

    // H8: Cooldown toggle 4G chống Wi-Fi chập chờn
    @Volatile private var lastMobileDataToggleTime = 0L

    companion object {
        const val ACTION_RINGER_SCHEDULE_TRIGGER = "com.pin.batteryguard.automation.RINGER_TRIGGER"
        private const val RINGER_ALARM_REQUEST_CODE = 992
        private const val NOTIF_CHANNEL_AUTOMATION = "batteryguard_automation"
        private const val NOTIF_ID_OVERNIGHT = 2001
    }

    fun start(serviceScope: CoroutineScope) {
        if (isStarted) {
            Log.d(TAG, "AutomationCoordinator đã chạy, bỏ qua gọi start() trùng lặp")
            return
        }
        isStarted = true
        this.scope = serviceScope
        Log.i(TAG, "🚀 Khởi động AutomationCoordinator trên BatteryMonitorService")

        initInitialBatteryState()
        createNotificationChannel()
        registerNetworkCallback()
        registerRingerAlarmReceiver()

        serviceScope.launch(Dispatchers.IO) {
            automationDataStore.configFlow.collectLatest { newConfig ->
                val prevConfig = currentConfig
                currentConfig = newConfig
                onConfigChanged(prevConfig, newConfig)
            }
        }
    }

    fun stop() {
        Log.i(TAG, "🛑 Dừng AutomationCoordinator")
        isStarted = false
        scope?.launch(Dispatchers.IO) {
            SystemActionBridge.resetThermalStatus(context)
        }
        hasAppliedThermalOverride = false
        unregisterNetworkCallback()
        unregisterRingerAlarmReceiver()
        cancelRingerAlarm() // H7: Cancel zombie alarms
        wifiDataDisableJob?.cancel()
        wifiDataRestoreJob?.cancel()
        screenOffDozeJob?.cancel()
        instantDozeJob?.cancel()
        if (isGmsSafeModeApplied) {
            isGmsSafeModeApplied = false
            scope?.launch(Dispatchers.IO) {
                SystemActionBridge.setGmsDozeSafeMode(false)
            }
        }
        isPowerSaverApplied = false
        this.scope = null
    }

    /** H7: Cancel pending ringer alarm intent để tránh zombie alarm */
    private fun cancelRingerAlarm() {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(ACTION_RINGER_SCHEDULE_TRIGGER).apply {
                setPackage(context.packageName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                RINGER_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pendingIntent?.let {
                alarmManager.cancel(it)
                it.cancel()
                Log.d(TAG, "⏰ Đã hủy ringer alarm pending intent")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Không thể hủy ringer alarm: ${e.message}")
        }
    }

    private fun updateRuleState(ruleId: AutomationRuleId, status: RuleActiveStatus, badge: String, detail: String = "") {
        _ruleStates.value = _ruleStates.value.toMutableMap().apply {
            put(ruleId, RuleRuntimeState(status = status, badgeText = badge, detailText = detail))
        }
    }

    private fun onConfigChanged(old: AutomationMasterConfig, new: AutomationMasterConfig) {
        if (!new.isMasterEnabled) {
            // Tắt toàn bộ rules
            AutomationRuleId.entries.forEach { id ->
                updateRuleState(id, RuleActiveStatus.DISABLED, "Tạm dừng (Master Off)")
            }
            wifiDataDisableJob?.cancel()
            wifiDataRestoreJob?.cancel()
            screenOffDozeJob?.cancel()
            return
        }

        // Cập nhật trạng thái từng rule
        if (new.wifiAutoDataEnabled) {
            if (_isWifiConnected.value) {
                updateRuleState(AutomationRuleId.WIFI_AUTO_DATA, RuleActiveStatus.ACTIVE, "Đang dùng Wi-Fi (Tắt 4G)", "Áp dụng SIM: ${new.wifiAutoDataParams.targetSim.name}")
            } else {
                updateRuleState(AutomationRuleId.WIFI_AUTO_DATA, RuleActiveStatus.IDLE, "Chờ kết nối Wi-Fi", "SIM: ${new.wifiAutoDataParams.targetSim.name}")
            }
        } else {
            updateRuleState(AutomationRuleId.WIFI_AUTO_DATA, RuleActiveStatus.DISABLED, "Đã tắt")
        }

        if (new.dayNightRingerEnabled) {
            evaluateRingerSchedule()
            scheduleNextRingerAlarm()
        } else {
            cancelRingerAlarm() // H7: Hủy zombie alarm khi tắt tính năng
            updateRuleState(AutomationRuleId.DAY_NIGHT_RINGER, RuleActiveStatus.DISABLED, "Đã tắt")
        }

        if (new.overnightChargingEnabled) {
            val oldSyncOnCharge = old.overnightChargingParams.syncOnChargeOnly
            val newSyncOnCharge = new.overnightChargingParams.syncOnChargeOnly
            if (oldSyncOnCharge != newSyncOnCharge) {
                scope?.launch(Dispatchers.IO) {
                    if (newSyncOnCharge) {
                        SystemActionBridge.setMasterSync(isCurrentlyCharging)
                    } else {
                        SystemActionBridge.setMasterSync(true)
                    }
                }
            }
        } else {
            updateRuleState(AutomationRuleId.OVERNIGHT_CHARGING, RuleActiveStatus.DISABLED, "Đã tắt")
        }
        if (!new.lowBatterySaverEnabled) {
            updateRuleState(AutomationRuleId.LOW_BATTERY_SAVER, RuleActiveStatus.DISABLED, "Đã tắt")
        }
        if (!new.deepScreenOffEnabled) {
            updateRuleState(AutomationRuleId.DEEP_SCREEN_OFF, RuleActiveStatus.DISABLED, "Đã tắt")
        }
        if (new.appStopManagementParams.smartAutoFreeze) {
            updateRuleState(
                AutomationRuleId.APP_STOP_MANAGEMENT,
                RuleActiveStatus.IDLE,
                "Smart Auto-Freeze sẵn sàng",
                "Tự động đưa app hao pin vào Standby Bucket & Freeze"
            )
        } else {
            updateRuleState(AutomationRuleId.APP_STOP_MANAGEMENT, RuleActiveStatus.DISABLED, "Đã tắt")
        }
    }

    // =========================================================================
    // 📶 Module 1: Tự động bật/tắt dữ liệu di động theo Wi-Fi (với lựa chọn SIM)
    // =========================================================================

    private fun registerNetworkCallback() {
        try {
            connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.d(TAG, "📶 Wi-Fi đã kết nối (onAvailable)")
                    _isWifiConnected.value = true
                    handleWifiConnected()
                }

                override fun onLost(network: Network) {
                    Log.d(TAG, "📶 Wi-Fi đã ngắt kết nối (onLost)")
                    _isWifiConnected.value = false
                    handleWifiLost()
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    if (hasInternet && !_isWifiConnected.value) {
                        _isWifiConnected.value = true
                        handleWifiConnected()
                    }
                }
            }

            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.e(TAG, "Không thể đăng ký NetworkCallback: ${e.message}")
        }
    }

    private fun unregisterNetworkCallback() {
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
            networkCallback = null
        } catch (_: Exception) {}
    }

    private fun handleWifiConnected() {
        wifiDataRestoreJob?.cancel() // Hủy job khôi phục 4G nếu đang đếm ngược

        if (!currentConfig.isMasterEnabled || !currentConfig.wifiAutoDataEnabled) return

        val params = currentConfig.wifiAutoDataParams
        val delaySec = params.delaySeconds.coerceIn(0, 15)

        val badgeText = if (delaySec == 0) "Tắt 4G tức thì" else "Chờ ${delaySec}s để tắt 4G"
        updateRuleState(
            AutomationRuleId.WIFI_AUTO_DATA,
            RuleActiveStatus.IDLE,
            badgeText,
            "Đã kết nối Wi-Fi. Đang chờ ngắt dữ liệu di động."
        )

        wifiDataDisableJob?.cancel()
        wifiDataDisableJob = scope?.launch(Dispatchers.IO) {
            if (delaySec > 0) {
                delay(delaySec * 1000L)
            }
            if (_isWifiConnected.value) {
                executeMobileDataToggle(enable = false, targetSim = params.targetSim)
                updateRuleState(
                    AutomationRuleId.WIFI_AUTO_DATA,
                    RuleActiveStatus.ACTIVE,
                    "Đã tắt dữ liệu di động",
                    "Đang dùng Wi-Fi • SIM: ${params.targetSim.name}"
                )
            }
        }
    }

    private fun handleWifiLost() {
        wifiDataDisableJob?.cancel()

        if (!currentConfig.isMasterEnabled || !currentConfig.wifiAutoDataEnabled) return
        val params = currentConfig.wifiAutoDataParams
        if (!params.autoRestoreDataOnDisconnect) return

        val restoreSec = params.restoreDelaySeconds.coerceIn(0, 15)
        val badgeText = if (restoreSec == 0) "Bật lại 4G tức thì" else "Chờ ${restoreSec}s bật lại 4G"
        updateRuleState(
            AutomationRuleId.WIFI_AUTO_DATA,
            RuleActiveStatus.IDLE,
            badgeText,
            "Mất Wi-Fi. Đang chuẩn bị khôi phục dữ liệu di động."
        )

        wifiDataRestoreJob?.cancel()
        wifiDataRestoreJob = scope?.launch(Dispatchers.IO) {
            if (restoreSec > 0) {
                delay(restoreSec * 1000L)
            }
            if (!_isWifiConnected.value) {
                executeMobileDataToggle(enable = true, targetSim = params.targetSim)
                updateRuleState(
                    AutomationRuleId.WIFI_AUTO_DATA,
                    RuleActiveStatus.ACTIVE,
                    "Đã bật lại dữ liệu di động",
                    "Wi-Fi ngắt • Đã khôi phục SIM: ${params.targetSim.name}"
                )
            }
        }
    }

    private suspend fun executeMobileDataToggle(enable: Boolean, targetSim: TargetSimSelection) {
        Log.i(TAG, "Thực thi Toggle Mobile Data (${if (enable) "BẬT" else "TẮT"}) cho SIM $targetSim (Ưu tiên Native First)")
        SystemActionBridge.toggleMobileData(context, enable, targetSim)
    }

    // =========================================================================
    // 🔔 Module 2: Lịch chuông & Im lặng Ngày/Đêm
    // =========================================================================

    private fun registerRingerAlarmReceiver() {
        ringerAlarmReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent?.action == ACTION_RINGER_SCHEDULE_TRIGGER) {
                    Log.d(TAG, "🔔 Nhận được alarm ringer trigger")
                    scope?.launch(Dispatchers.IO) {
                        evaluateRingerSchedule()
                        scheduleNextRingerAlarm()
                    }
                }
            }
        }
        val filter = IntentFilter(ACTION_RINGER_SCHEDULE_TRIGGER)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(ringerAlarmReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(ringerAlarmReceiver, filter)
        }
    }

    private fun unregisterRingerAlarmReceiver() {
        try {
            ringerAlarmReceiver?.let { context.unregisterReceiver(it) }
            ringerAlarmReceiver = null
        } catch (_: Exception) {}
    }

    fun evaluateRingerSchedule(force: Boolean = false) {
        if (!currentConfig.isMasterEnabled || !currentConfig.dayNightRingerEnabled) return

        val p = currentConfig.dayNightRingerParams
        val cal = Calendar.getInstance()
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val startMinutes = p.startHour * 60 + p.startMinute
        val endMinutes = p.endHour * 60 + p.endMinute
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val isWeekend = (dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY)

        val isNightTime = if (!p.applyOnWeekends && isWeekend) {
            false // Không áp dụng chế độ ban đêm vào cuối tuần nếu người dùng tắt
        } else if (startMinutes > endMinutes) {
            // Qua nửa đêm (Ví dụ: 21:30 -> 06:00)
            currentMinutes >= startMinutes || currentMinutes < endMinutes
        } else {
            currentMinutes in startMinutes until endMinutes
        }

        val shouldApplyAction = force || (lastAppliedRingerIsNight != isNightTime)
        lastAppliedRingerIsNight = isNightTime

        scope?.launch(Dispatchers.IO) {
            if (isNightTime) {
                if (shouldApplyAction) {
                    SystemActionBridge.setRingerMode(context, p.nightMode)
                }

                updateRuleState(
                    AutomationRuleId.DAY_NIGHT_RINGER,
                    RuleActiveStatus.ACTIVE,
                    "Chế độ ban đêm (${if (p.nightMode == RingerTargetMode.SILENT) "Im lặng" else "Rung"})",
                    "Khung giờ: ${String.format("%02d:%02d", p.startHour, p.startMinute)} - ${String.format("%02d:%02d", p.endHour, p.endMinute)}"
                )
            } else {
                if (shouldApplyAction) {
                    SystemActionBridge.setNormalRingerMode(context)
                }

                updateRuleState(
                    AutomationRuleId.DAY_NIGHT_RINGER,
                    RuleActiveStatus.IDLE,
                    "Chế độ ban ngày (Chuông)",
                    "Chờ đến ${String.format("%02d:%02d", p.startHour, p.startMinute)} chuyển chế độ ban đêm"
                )
            }
        }
    }

    private fun scheduleNextRingerAlarm() {
        if (!currentConfig.isMasterEnabled || !currentConfig.dayNightRingerEnabled) return

        val p = currentConfig.dayNightRingerParams
        val now = Calendar.getInstance()
        val calStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, p.startHour)
            set(Calendar.MINUTE, p.startMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }

        val calEnd = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, p.endHour)
            set(Calendar.MINUTE, p.endMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }

        val nextTriggerTime = if (calStart.before(calEnd)) calStart.timeInMillis else calEnd.timeInMillis

        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(ACTION_RINGER_SCHEDULE_TRIGGER).apply {
                setPackage(context.packageName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                RINGER_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (canScheduleExact) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerTime, pendingIntent)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTriggerTime, pendingIntent)
                }
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, nextTriggerTime, pendingIntent)
            }
            Log.d(TAG, "⏰ Đã lên lịch ringer alarm tiếp theo lúc: ${java.util.Date(nextTriggerTime)} (exact=$canScheduleExact)")
        } catch (e: Exception) {
            Log.w(TAG, "Không thể lập lịch alarm ringer: ${e.message}")
        }
    }

    // =========================================================================
    // 🔋 Module 3, 4, 5: Battery & Screen Events
    // =========================================================================

    fun onBatteryChanged(level: Int, temperature: Float, charging: Boolean) {
        currentBatteryLevel = level
        val chargingStateChanged = (charging != isCurrentlyCharging)
        isCurrentlyCharging = charging

        if (!charging) {
            hasNotifiedOvernightCharging = false
            if (hasAppliedThermalOverride) {
                hasAppliedThermalOverride = false
                scope?.launch(Dispatchers.IO) {
                    SystemActionBridge.resetThermalStatus(context)
                }
            }
            if (isCriticalThermalThrottled) {
                isCriticalThermalThrottled = false
                scope?.launch(Dispatchers.IO) {
                    SystemActionBridge.setFastChargingEnabled(context, true)
                }
            }
            if (isFastChargeThrottled) {
                isFastChargeThrottled = false
                val shouldRestore = wasFastChargeOriginallyEnabled ?: true
                wasFastChargeOriginallyEnabled = null
                if (shouldRestore) {
                    scope?.launch(Dispatchers.IO) {
                        SystemActionBridge.setFastChargingEnabled(context, true)
                    }
                }
            }
        }

        if (!currentConfig.isMasterEnabled) return

        if (chargingStateChanged && currentConfig.dayNightRingerEnabled) {
            evaluateRingerSchedule()
        }

        if (chargingStateChanged && currentConfig.overnightChargingEnabled && currentConfig.overnightChargingParams.syncOnChargeOnly) {
            scope?.launch(Dispatchers.IO) {
                SystemActionBridge.setMasterSync(charging)
                Log.i(TAG, "⚡ [SyncOnCharge] Cắm/Rút sạc (charging=$charging) -> setMasterSync($charging)")
            }
        }

        if (charging) {
            val overnightParams = currentConfig.overnightChargingParams

            // Module Quản lý Sạc & Thermal Guard
            if (overnightParams.prioritizeFastChargeNoThrottle) {
                // Chế độ: Ưu tiên Sạc Nhanh Tối Đa (Bỏ qua bảo vệ nhiệt độ 38-45°C)
                if (chargingStateChanged || !hasAppliedThermalOverride) {
                    hasAppliedThermalOverride = true
                    scope?.launch(Dispatchers.IO) {
                        SystemActionBridge.overrideThermalStatus(context, 0)
                        if (overnightParams.fastCharge90WEnabled) {
                            SystemActionBridge.setFastChargingEnabled(context, true)
                        }
                    }
                }

                // Hard Fail-Safe: Cắt sạc nhanh nếu nhiệt độ chạm trần nguy hiểm >= 48°C
                val criticalLimit = overnightParams.criticalThermalLimit
                if (temperature >= criticalLimit && !isCriticalThermalThrottled) {
                    isCriticalThermalThrottled = true
                    scope?.launch(Dispatchers.IO) {
                        SystemActionBridge.resetThermalStatus(context)
                        SystemActionBridge.setFastChargingEnabled(context, false)
                        Log.w(TAG, "🚨 [EmergencyThermalGuard] Pin chạm ${temperature}°C (>= ${criticalLimit}°C) -> Kích hoạt Hard Fail-Safe ngắt sạc nhanh bảo vệ cell pin!")
                    }
                } else if (temperature <= 42.0f && isCriticalThermalThrottled) {
                    isCriticalThermalThrottled = false
                    scope?.launch(Dispatchers.IO) {
                        SystemActionBridge.overrideThermalStatus(context, 0)
                        if (overnightParams.fastCharge90WEnabled) {
                            SystemActionBridge.setFastChargingEnabled(context, true)
                        }
                        Log.i(TAG, "❄️ [EmergencyThermalGuard] Pin đã hạ an toàn về ${temperature}°C (<= 42°C) -> Khôi phục ưu tiên sạc nhanh tối đa")
                    }
                }
            } else {
                // Chế độ bảo vệ nhiệt độ tiêu chuẩn
                if (hasAppliedThermalOverride) {
                    hasAppliedThermalOverride = false
                    scope?.launch(Dispatchers.IO) {
                        SystemActionBridge.resetThermalStatus(context)
                    }
                }

                if (temperature >= 38.5f && !isFastChargeThrottled) {
                    isFastChargeThrottled = true
                    wasFastChargeOriginallyEnabled = SystemActionBridge.isFastChargingEnabled(context)
                    scope?.launch(Dispatchers.IO) {
                        SystemActionBridge.setFastChargingEnabled(context, false)
                        Log.w(TAG, "🔥 [SmartThermalGuard] Pin đạt ${temperature}°C (>38.5°C) -> Tạm hạ công suất sạc 90W bảo vệ Surge P2/G1")
                    }
                } else if (temperature <= 36.5f && isFastChargeThrottled) {
                    isFastChargeThrottled = false
                    val shouldRestore = wasFastChargeOriginallyEnabled ?: true
                    wasFastChargeOriginallyEnabled = null
                    if (shouldRestore) {
                        scope?.launch(Dispatchers.IO) {
                            SystemActionBridge.setFastChargingEnabled(context, true)
                            Log.i(TAG, "❄️ [SmartThermalGuard] Pin đã hạ về ${temperature}°C (<36.5°C) -> Khôi phục sạc nhanh HyperCharge 90W")
                        }
                    }
                }
            }
        }

        // Module 3: Quản lý sạc pin & Bảo vệ qua đêm
        if (currentConfig.overnightChargingEnabled && charging) {
            val overnightParams = currentConfig.overnightChargingParams
            val limit = overnightParams.maxChargeLimitPercent
            val cal = Calendar.getInstance()
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            val startH = overnightParams.startHour
            val endH = overnightParams.endHour

            val isNightHours = if (startH > endH) {
                hour >= startH || hour < endH
            } else {
                hour in startH until endH
            }

            if (isNightHours && level >= limit) {
                updateRuleState(
                    AutomationRuleId.OVERNIGHT_CHARGING,
                    RuleActiveStatus.ACTIVE,
                    "Đã đạt mức sạc $level% (Giới hạn: $limit%)",
                    "Vui lòng ngắt sạc để bảo vệ tuổi thọ pin"
                )
                if (overnightParams.alertSoundOnTarget && !hasNotifiedOvernightCharging) {
                    hasNotifiedOvernightCharging = true
                    showOvernightAlertNotification(level, limit)
                    SystemActionBridge.applySmartChargingLimit(context, limit)
                }
            } else {
                val statusText = when {
                    overnightParams.prioritizeFastChargeNoThrottle && isCriticalThermalThrottled -> "🚨 Khẩn cấp: Quá nhiệt ${temperature}°C • Đang hạ sạc"
                    overnightParams.prioritizeFastChargeNoThrottle -> "🔥 Đang ưu tiên sạc nhanh tối đa (${temperature}°C)"
                    overnightParams.fastCharge90WEnabled -> "⚡ Đang sạc nhanh HyperCharge (${level}%)"
                    else -> "🔋 Đang sạc (${level}% / ${limit}%)"
                }
                updateRuleState(
                    AutomationRuleId.OVERNIGHT_CHARGING,
                    RuleActiveStatus.IDLE,
                    statusText,
                    "Khung giờ đêm: ${String.format("%02d:00", startH)} - ${String.format("%02d:00", endH)}"
                )
            }
        }

        // Module 4: Tự động tiết kiệm pin khi pin yếu
        evaluateLowBatterySaver(level, charging, chargingStateChanged)
    }

    private fun evaluateLowBatterySaver(level: Int, charging: Boolean, chargingStateChanged: Boolean) {
        if (!currentConfig.lowBatterySaverEnabled) return

        val saverParams = currentConfig.lowBatterySaverParams
        val threshold = saverParams.thresholdPercent
        if (!charging && level <= threshold) {
            if (!isPowerSaverApplied) {
                isPowerSaverApplied = true
                scope?.launch(Dispatchers.IO) {
                    if (saverParams.enableSystemPowerSaver) {
                        if (cachedWasDarkModeForBatterySaver == null) {
                            cachedWasDarkModeForBatterySaver = SystemActionBridge.isSystemDarkMode(context)
                        }
                        SystemActionBridge.setPowerSaveMode(context, enable = true)
                        SystemActionBridge.setSystemDarkMode(context, enable = true)
                    }
                    if (saverParams.dimDisplayBrightness) {
                        val current = SystemActionBridge.getScreenBrightness(context)
                        if (cachedBrightnessForBatterySaver == null) {
                            cachedBrightnessForBatterySaver = current
                        }
                        val dimmed = (current * 0.7f).toInt().coerceAtLeast(15)
                        SystemActionBridge.setScreenBrightness(context, dimmed)
                    }
                    if (saverParams.smoothSaver120Hz) {
                        SystemActionBridge.setSmoothSaverMode(context, enable = true)
                    } else if (saverParams.capRefreshRate60Hz) {
                        SystemActionBridge.setDisplayRefreshRate60Hz(context)
                    }
                }
            }
            val detailText = if (saverParams.smoothSaver120Hz) {
                "Tự động bật Tiết kiệm pin (Mượt 120Hz)"
            } else {
                "Tự động bật Tiết kiệm pin & hạ màn hình 60Hz"
            }
            updateRuleState(
                AutomationRuleId.LOW_BATTERY_SAVER,
                RuleActiveStatus.ACTIVE,
                "Đã kích hoạt Tiết kiệm pin (${level}%)",
                detailText
            )
        } else if (charging || level > threshold) {
            if (isPowerSaverApplied) {
                isPowerSaverApplied = false
                scope?.launch(Dispatchers.IO) {
                    SystemActionBridge.setPowerSaveMode(context, enable = false)
                    val wasDark = cachedWasDarkModeForBatterySaver ?: false
                    if (!wasDark) {
                        SystemActionBridge.setSystemDarkMode(context, enable = false)
                    }
                    cachedWasDarkModeForBatterySaver = null

                    cachedBrightnessForBatterySaver?.let { restoreBright ->
                        SystemActionBridge.setScreenBrightness(context, restoreBright)
                        cachedBrightnessForBatterySaver = null
                    }
                    if (saverParams.smoothSaver120Hz || saverParams.capRefreshRate60Hz) {
                        SystemActionBridge.restoreDisplayRefreshRate(context)
                    }
                }
            }
            if (charging && chargingStateChanged) {
                updateRuleState(
                    AutomationRuleId.LOW_BATTERY_SAVER,
                    RuleActiveStatus.IDLE,
                    "Bình thường (Đang sạc)",
                    "Ngưỡng kích hoạt: ≤ $threshold%"
                )
            }
        }
    }

    fun onScreenOff() {
        lastScreenOffTime = System.currentTimeMillis()
        if (currentConfig.deepScreenOffEnabled && currentConfig.deepScreenOffParams.restoreBrightnessOnScreenOn) {
            val current = SystemActionBridge.getScreenBrightness(context)
            val isImmediateReflap = (System.currentTimeMillis() - lastBoostTimestamp < 3000L) && (current == lastBoostedBrightness)
            if (isImmediateReflap && lastPreBoostBrightness != null) {
                // Chống leo thang độ sáng khi đóng/mở máy liên tục: bảo toàn mức độ sáng gốc ban đầu
                cachedBrightnessForScreenOff = lastPreBoostBrightness
            } else {
                cachedBrightnessForScreenOff = current
            }
        }
        if (!currentConfig.isMasterEnabled || !currentConfig.deepScreenOffEnabled) return

        val deepParams = currentConfig.deepScreenOffParams
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val isMusicActive = audioManager?.isMusicActive == true

        // 1. Instant Deep Doze: Debounce 3s sau khi tắt màn hình nếu không phát nhạc
        if (deepParams.instantDeepDoze && !isMusicActive) {
            instantDozeJob?.cancel()
            instantDozeJob = scope?.launch(Dispatchers.IO) {
                delay(3000L)
                Log.i(TAG, "⚡ Kích hoạt Instant Deep Doze (sau 3s tắt màn hình)")
                SystemActionBridge.triggerInstantDeepDoze(context)
                updateRuleState(
                    AutomationRuleId.DEEP_SCREEN_OFF,
                    RuleActiveStatus.ACTIVE,
                    "Đã kích hoạt Instant Deep Doze",
                    "Ngủ sâu tức thì sau 3s tắt màn hình"
                )
            }
        }

        // 2. GMS Doze Safe Mode: Tạm thời đưa GMS vào Doze khi tắt màn hình
        if (deepParams.gmsDozeSafeMode) {
            isGmsSafeModeApplied = true
            scope?.launch(Dispatchers.IO) {
                SystemActionBridge.setGmsDozeSafeMode(true)
            }
        }

        val delayMin = deepParams.delayMinutes
        if (!deepParams.instantDeepDoze || isMusicActive) {
            updateRuleState(
                AutomationRuleId.DEEP_SCREEN_OFF,
                RuleActiveStatus.IDLE,
                "Chờ ${delayMin}p tối ưu Doze",
                "Màn hình đã tắt"
            )

            screenOffDozeJob?.cancel()
            screenOffDozeJob = scope?.launch(Dispatchers.IO) {
                delay(delayMin * 60 * 1000L)
                Log.i(TAG, "📴 Kích hoạt sớm Deep Doze sau ${delayMin} phút tắt màn hình")
                if (deepParams.enableDeepDoze) {
                    SystemActionBridge.triggerDeepDoze()
                }
                updateRuleState(
                    AutomationRuleId.DEEP_SCREEN_OFF,
                    RuleActiveStatus.ACTIVE,
                    "Đã kích hoạt Deep Doze",
                    "Đang duy trì chế độ ngủ sâu tối đa"
                )
            }
        }
    }

    fun onScreenOn() {
        instantDozeJob?.cancel()
        screenOffDozeJob?.cancel()

        // Khôi phục GMS Doze Safe Mode
        if (isGmsSafeModeApplied || (currentConfig.deepScreenOffEnabled && currentConfig.deepScreenOffParams.gmsDozeSafeMode)) {
            isGmsSafeModeApplied = false
            scope?.launch(Dispatchers.IO) {
                SystemActionBridge.setGmsDozeSafeMode(false)
            }
        }

        // Lưới an toàn 0% hao pin: Gọi ngay evaluateRingerSchedule() khi bật màn hình
        if (currentConfig.isMasterEnabled && currentConfig.dayNightRingerEnabled) {
            evaluateRingerSchedule()
        }

        if (currentConfig.deepScreenOffEnabled) {
            if (currentConfig.deepScreenOffParams.restoreBrightnessOnScreenOn) {
                scope?.launch(Dispatchers.IO) {
                    // Nếu đang trong chế độ pin yếu, bảo toàn độ sáng tiết kiệm pin
                    if (isPowerSaverApplied) return@launch

                    val baseBrightness = cachedBrightnessForScreenOff ?: SystemActionBridge.getScreenBrightness(context)
                    lastPreBoostBrightness = baseBrightness
                    val percent = currentConfig.deepScreenOffParams.screenOnBrightnessIncreasePercent.coerceIn(5, 30)
                    val factor = 1.0f + (percent / 100.0f)
                    val targetBrightness = (baseBrightness * factor).toInt().coerceIn(15, 255)
                    lastBoostedBrightness = targetBrightness
                    lastBoostTimestamp = System.currentTimeMillis()
                    cachedBrightnessForScreenOff = null
                    SystemActionBridge.setScreenBrightness(context, targetBrightness)
                }
            }

            // Khôi phục device_idle_constants và unforce idle khi bật màn hình
            scope?.launch(Dispatchers.IO) {
                SystemActionBridge.resetDeepDoze(context)
            }
            updateRuleState(
                AutomationRuleId.DEEP_SCREEN_OFF,
                RuleActiveStatus.IDLE,
                "Chờ màn hình tắt",
                "Sẵn sàng kích hoạt Doze sâu"
            )
        }
    }

    /**
     * Smart Auto-Freeze: Đưa app vào Standby Bucket RESTRICTED hoặc ACTIVE
     */
    suspend fun applySmartFreeze(packageName: String, freeze: Boolean): Boolean {
        return SystemActionBridge.applySmartFreeze(packageName, freeze)
    }

    private fun initInitialBatteryState() {
        try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            val cap = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
            if (cap in 0..100) {
                currentBatteryLevel = cap
            }
            isCurrentlyCharging = bm?.isCharging == true
        } catch (_: Exception) {}
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = android.app.NotificationChannel(
                NOTIF_CHANNEL_AUTOMATION,
                "Tự động hóa BatteryGuard",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Thông báo cảnh báo từ các module tự động hóa"
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun showOvernightAlertNotification(current: Int, limit: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(context, NOTIF_CHANNEL_AUTOMATION)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("🔋 Pin đã đạt ${current}% (Khuyến nghị ngắt sạc)")
            .setContentText("Tính năng Bảo vệ sạc đêm: Vui lòng rút sạc để chống chai phồng pin.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID_OVERNIGHT, notif)
    }
}
