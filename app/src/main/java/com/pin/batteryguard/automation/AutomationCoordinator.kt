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

    // Cache cấu hình đang áp dụng
    @Volatile private var currentConfig = AutomationMasterConfig()

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
    private var cachedBrightnessForScreenOff: Int? = null

    // Cờ nhớ trạng thái chuông đã áp dụng để chống gọi thừa IPC shell khi bật màn hình (Idempotency)
    private var lastAppliedRingerIsNight: Boolean? = null

    // C2: Guard flag chống coroutine flooding khi pin yếu
    @Volatile private var isPowerSaverApplied = false

    // H8: Cooldown toggle 4G chống Wi-Fi chập chờn
    @Volatile private var lastMobileDataToggleTime = 0L

    companion object {
        const val ACTION_RINGER_SCHEDULE_TRIGGER = "com.pin.batteryguard.automation.RINGER_TRIGGER"
        private const val RINGER_ALARM_REQUEST_CODE = 992
        private const val NOTIF_CHANNEL_AUTOMATION = "batteryguard_automation"
        private const val NOTIF_ID_OVERNIGHT = 2001
    }

    fun start(serviceScope: CoroutineScope) {
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
        unregisterNetworkCallback()
        unregisterRingerAlarmReceiver()
        cancelRingerAlarm() // H7: Cancel zombie alarms
        wifiDataDisableJob?.cancel()
        wifiDataRestoreJob?.cancel()
        screenOffDozeJob?.cancel()
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

        if (!new.overnightChargingEnabled) {
            updateRuleState(AutomationRuleId.OVERNIGHT_CHARGING, RuleActiveStatus.DISABLED, "Đã tắt")
        }
        if (!new.lowBatterySaverEnabled) {
            updateRuleState(AutomationRuleId.LOW_BATTERY_SAVER, RuleActiveStatus.DISABLED, "Đã tắt")
        }
        if (!new.deepScreenOffEnabled) {
            updateRuleState(AutomationRuleId.DEEP_SCREEN_OFF, RuleActiveStatus.DISABLED, "Đã tắt")
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

        if (!currentConfig.isMasterEnabled) return

        if (chargingStateChanged && currentConfig.dayNightRingerEnabled) {
            evaluateRingerSchedule()
        }

        if (!charging) {
            hasNotifiedOvernightCharging = false
        }

        // Module 3: Sạc qua đêm bảo vệ pin
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
                updateRuleState(
                    AutomationRuleId.OVERNIGHT_CHARGING,
                    RuleActiveStatus.IDLE,
                    "Đang sạc (${level}% / ${limit}%)",
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
                        SystemActionBridge.setPowerSaveMode(context, enable = true)
                    }
                    if (saverParams.dimDisplayBrightness) {
                        val current = SystemActionBridge.getScreenBrightness(context)
                        if (cachedBrightnessForBatterySaver == null) {
                            cachedBrightnessForBatterySaver = current
                        }
                        val dimmed = (current * 0.7f).toInt().coerceAtLeast(15)
                        SystemActionBridge.setScreenBrightness(context, dimmed)
                    }
                    if (saverParams.capRefreshRate60Hz) {
                        SystemActionBridge.setDisplayRefreshRate60Hz(context)
                    }
                }
            }
            updateRuleState(
                AutomationRuleId.LOW_BATTERY_SAVER,
                RuleActiveStatus.ACTIVE,
                "Đã kích hoạt Tiết kiệm pin (${level}%)",
                "Tự động bật Tiết kiệm pin & hạ màn hình 60Hz"
            )
        } else if (charging || level > threshold) {
            if (isPowerSaverApplied) {
                isPowerSaverApplied = false
                scope?.launch(Dispatchers.IO) {
                    SystemActionBridge.setPowerSaveMode(context, enable = false)
                    cachedBrightnessForBatterySaver?.let { restoreBright ->
                        SystemActionBridge.setScreenBrightness(context, restoreBright)
                        cachedBrightnessForBatterySaver = null
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
            cachedBrightnessForScreenOff = SystemActionBridge.getScreenBrightness(context)
        }
        if (!currentConfig.isMasterEnabled || !currentConfig.deepScreenOffEnabled) return

        val deepParams = currentConfig.deepScreenOffParams
        val delayMin = deepParams.delayMinutes
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

    fun onScreenOn() {
        screenOffDozeJob?.cancel()

        // Lưới an toàn 0% hao pin: Gọi ngay evaluateRingerSchedule() khi bật màn hình
        if (currentConfig.isMasterEnabled && currentConfig.dayNightRingerEnabled) {
            evaluateRingerSchedule()
        }

        if (currentConfig.deepScreenOffEnabled) {
            if (currentConfig.deepScreenOffParams.restoreBrightnessOnScreenOn) {
                scope?.launch(Dispatchers.IO) {
                    // Nếu đang trong chế độ pin yếu, bảo toàn độ sáng tiết kiệm pin
                    if (isPowerSaverApplied) return@launch

                    val targetBrightness = cachedBrightnessForScreenOff?.let { cached ->
                        (cached * 1.3f).toInt().coerceIn(15, 255)
                    } ?: run {
                        val current = SystemActionBridge.getScreenBrightness(context)
                        (current * 1.3f).toInt().coerceIn(current, 255)
                    }
                    cachedBrightnessForScreenOff = null
                    SystemActionBridge.setScreenBrightness(context, targetBrightness)
                }
            }

            // Khôi phục device_idle_constants và Master Sync khi bật màn hình
            scope?.launch(Dispatchers.IO) {
                try {
                    // Reset device_idle_constants về mặc định
                    executeShizukuCommandWithTimeout(
                        arrayOf("settings", "delete", "global", "device_idle_constants"), 2000L
                    )
                    // Unforce idle nếu đang force
                    executeShizukuCommandWithTimeout(
                        arrayOf("dumpsys", "deviceidle", "unforce"), 2000L
                    )
                } catch (_: Exception) {}
            }
            updateRuleState(
                AutomationRuleId.DEEP_SCREEN_OFF,
                RuleActiveStatus.IDLE,
                "Chờ màn hình tắt",
                "Sẵn sàng kích hoạt Doze sâu"
            )
        }
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
