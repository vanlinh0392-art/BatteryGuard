package com.pin.batteryguard.ui.screen.dashboard

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pin.batteryguard.data.db.dao.FrozenAppDao
import com.pin.batteryguard.data.db.entity.BatteryLog
import com.pin.batteryguard.data.db.entity.ForceStopLog
import com.pin.batteryguard.data.db.entity.FrozenApp
import com.pin.batteryguard.data.preferences.SettingsDataStore
import com.pin.batteryguard.data.repository.AppRepository
import com.pin.batteryguard.data.repository.BatteryRepository
import com.pin.batteryguard.domain.model.AppBatteryInfo
import com.pin.batteryguard.domain.model.BatteryState
import com.pin.batteryguard.service.BatteryMonitorService
import com.pin.batteryguard.shizuku.ForceStopManager
import com.pin.batteryguard.shizuku.ShizukuManager
import com.pin.batteryguard.shizuku.ShizukuStatus
import com.pin.batteryguard.util.PackageHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val batteryState: BatteryState = BatteryState(),
    val isMonitoring: Boolean = false,
    val shizukuStatus: ShizukuStatus = ShizukuStatus.NOT_INSTALLED,
    val stoppedToday: Int = 0,
    val savedPercent: Float = 0f,
    val topDrainingApps: List<AppBatteryInfo> = emptyList(),
    val batteryHistory: List<com.pin.batteryguard.data.db.entity.BatteryLog> = emptyList(),
    val recentForceStopLogs: List<com.pin.batteryguard.data.db.entity.ForceStopLog> = emptyList(),
    val isSetupCompleted: Boolean? = null,
    val actionResult: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val batteryRepository: BatteryRepository,
    private val appRepository: AppRepository,
    private val settingsDataStore: SettingsDataStore,
    private val shizukuManager: ShizukuManager,
    private val forceStopManager: ForceStopManager,
    private val frozenAppDao: FrozenAppDao
) : ViewModel() {

    private val _batteryState = MutableStateFlow(BatteryState())
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private var lastUiBatteryLogLevel = -1
    private var lastUiBatteryLogTime = 0L

    private val batteryReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                updateBatteryStateFromIntent(intent)
            }
        }
    }

    init {
        loadBatteryState()
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            context.registerReceiver(batteryReceiver, filter)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        seedInitialBatteryLogIfNeeded()
        observeData()
    }

    fun refreshBatteryState() {
        loadBatteryState()
    }

    private fun updateBatteryStateFromIntent(intent: Intent?) {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val directCapacity = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val levelPct = if (intent != null) {
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            if (level >= 0 && scale > 0) {
                (level * 100 / scale.toFloat()).toInt()
            } else {
                directCapacity
            }
        } else {
            directCapacity
        }

        val finalLevel = if (levelPct in 0..100) levelPct else if (directCapacity in 0..100) directCapacity else 0

        val temp = if (intent != null) {
            intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10.0f
        } else 0f

        val volt = if (intent != null) {
            intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        } else 0

        val curNow = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL ||
                bm.isCharging

        val chargePlug = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val chargeType = when (chargePlug) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> if (isCharging) "Charging" else "None"
        }

        val newState = BatteryState(
            level = finalLevel,
            temperature = temp,
            voltage = volt,
            currentNow = curNow,
            isCharging = isCharging,
            chargeType = chargeType
        )
        // C7: Only emit when meaningful fields change — currentNow/voltage
        // fluctuate every second and would cause cascade Flow recompositions.
        val old = _batteryState.value
        if (old.level == newState.level &&
            old.isCharging == newState.isCharging &&
            old.temperature == newState.temperature
        ) return
        _batteryState.value = newState
        maybeLogBatteryFromUi(newState)
    }

    private fun seedInitialBatteryLogIfNeeded() {
        viewModelScope.launch {
            val latest = batteryRepository.getLatestLog().first()
            val now = System.currentTimeMillis()
            if (latest == null || now - latest.timestamp > 15 * 60 * 1000L) {
                val state = _batteryState.value
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val level = if (state.level > 0) state.level else bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (level > 0) {
                    batteryRepository.insertBatteryLog(
                        BatteryLog(
                            level = level,
                            temperature = state.temperature,
                            voltage = state.voltage,
                            currentNow = state.currentNow,
                            currentAvg = 0,
                            isCharging = state.isCharging,
                            chargeType = state.chargeType,
                            screenOn = true,
                            timestamp = now
                        )
                    )
                }
            }
        }
    }

    private fun maybeLogBatteryFromUi(state: BatteryState) {
        val now = System.currentTimeMillis()
        if (state.level > 0 && (state.level != lastUiBatteryLogLevel || now - lastUiBatteryLogTime >= 15 * 60 * 1000L)) {
            lastUiBatteryLogLevel = state.level
            lastUiBatteryLogTime = now
            viewModelScope.launch {
                batteryRepository.insertBatteryLog(
                    BatteryLog(
                        level = state.level,
                        temperature = state.temperature,
                        voltage = state.voltage,
                        currentNow = state.currentNow,
                        currentAvg = 0,
                        isCharging = state.isCharging,
                        chargeType = state.chargeType,
                        screenOn = true,
                        timestamp = now
                    )
                )
            }
        }
    }

    private fun loadBatteryState() {
        val intent = try {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (e: Exception) {
            null
        }
        updateBatteryStateFromIntent(intent)
    }

    private fun observeData() {
        // Collect setup completion status
        viewModelScope.launch {
            settingsDataStore.isSetupCompletedFlow.collectLatest { completed ->
                _uiState.update { it.copy(isSetupCompleted = completed) }
            }
        }

        // Collect monitoring config status
        viewModelScope.launch {
            settingsDataStore.configFlow.collectLatest { config ->
                _uiState.update { it.copy(isMonitoring = config.isMonitoringEnabled) }
            }
        }

        // Collect Shizuku status
        viewModelScope.launch {
            shizukuManager.status.collectLatest { status ->
                _uiState.update { it.copy(shizukuStatus = status) }
                if (status == ShizukuStatus.READY) {
                    loadTopDrainingApps()
                }
            }
        }

        // Collect battery state combined with logs
        viewModelScope.launch {
            val since = System.currentTimeMillis() - (24 * 60 * 60 * 1000L) // 24h
            combine(
                _batteryState,
                batteryRepository.getLogsSince(since),
                batteryRepository.getAllForceStopLogs()
            ) { state, logs, actionLogs ->
                val history = logs
                val todayStart = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }.timeInMillis
                val verifiedToday = actionLogs.filter {
                    (it.verified || it.success) && it.timestamp >= todayStart && it.action != "unfreeze"
                }
                val savedPercent = verifiedToday.sumOf {
                    val rate = if (it.ratePercentPerHour > 0.0) it.ratePercentPerHour else it.drainPercent.toDouble()
                    if (rate > 0.0) rate else 0.5
                }.toFloat()

                _uiState.update {
                    it.copy(
                        batteryState = state,
                        batteryHistory = history,
                        stoppedToday = verifiedToday.size,
                        savedPercent = savedPercent,
                        recentForceStopLogs = actionLogs.take(10)
                    )
                }
            }.collect {}
        }
    }

    private fun loadTopDrainingApps() {
        viewModelScope.launch {
            try {
                val latest = batteryRepository.getLatestAppUsagePeriod().first()
                val exceptionPackages = appRepository.getExceptionPackages()

                val mappedList = latest.map { log ->
                    val activeUseOverride = batteryRepository.isActiveUseStopAllowed(log.packageName, log.userId)
                    AppBatteryInfo(
                        packageName = log.packageName,
                        appName = log.appName,
                        appIcon = PackageHelper.getAppIcon(context, log.packageName),
                        drainPercent = log.ratePercentPerHour.toFloat(),
                        foregroundTimeMs = log.foregroundTimeMs,
                        backgroundTimeMs = log.backgroundTimeMs,
                        isRunning = PackageHelper.isAppRunning(context, log.packageName),
                        isException = exceptionPackages.contains(log.packageName),
                        isFrozen = forceStopManager.isPackageFrozen(log.packageName, log.userId),
                        uid = log.uid,
                        userId = log.userId,
                        deltaMah = log.deltaMah,
                        ratePercentPerHour = log.ratePercentPerHour,
                        evidence = log.evidence,
                        decision = if (log.skipReason.isBlank()) log.decision else "${log.decision}: ${log.skipReason}",
                        isActionAllowed = log.skipReason !in setOf("system_app", "shared_uid", "protected_app") &&
                            (log.skipReason != "active_use" || activeUseOverride),
                        activeUseOverride = activeUseOverride
                    )
                }.sortedByDescending { it.ratePercentPerHour }.take(5)

                _uiState.update { it.copy(topDrainingApps = mappedList) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleMonitoring(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setMonitoringEnabled(enabled)
            val serviceIntent = Intent(context, BatteryMonitorService::class.java)
            if (enabled) {
                context.startForegroundService(serviceIntent)
            } else {
                context.stopService(serviceIntent)
            }
            _uiState.update { it.copy(isMonitoring = enabled) }
        }
    }

    fun forceStopApp(app: AppBatteryInfo) {
        viewModelScope.launch {
            val result = forceStopManager.forceStopPackage(app.packageName, app.userId)
            val actionRes = result.getOrNull()
            if (result.isSuccess || actionRes?.commandSucceeded == true) {
                batteryRepository.insertForceStopLog(
                    ForceStopLog(
                        packageName = app.packageName,
                        appName = app.appName,
                        drainPercent = if (app.drainPercent > 0f) app.drainPercent else (app.ratePercentPerHour.toFloat().coerceAtLeast(0.5f)),
                        action = "force_stop",
                        success = true,
                        reason = "manual",
                        userId = app.userId,
                        uid = app.uid,
                        deltaMah = app.deltaMah,
                        ratePercentPerHour = if (app.ratePercentPerHour > 0.0) app.ratePercentPerHour else 0.5,
                        verified = actionRes?.verified ?: true,
                        exitCode = actionRes?.exitCode ?: 0,
                        errorMessage = actionRes?.errorMessage
                    )
                )
                loadTopDrainingApps()
            }
        }
    }

    fun freezeApp(app: AppBatteryInfo) {
        viewModelScope.launch {
            val isFrozen = forceStopManager.isPackageFrozen(app.packageName, app.userId)
            val result = if (isFrozen) {
                forceStopManager.unfreezePackage(app.packageName, app.userId)
            } else {
                forceStopManager.freezePackage(app.packageName, app.userId)
            }
            val actionRes = result.getOrNull()
            if (result.isSuccess || actionRes?.commandSucceeded == true) {
                if (isFrozen) {
                    frozenAppDao.delete(app.packageName, app.userId)
                    _uiState.update { it.copy(actionResult = "Đã rã đông ${app.appName}") }
                } else {
                    frozenAppDao.insert(FrozenApp(app.userId, app.packageName, app.appName, isManual = true))
                    _uiState.update { it.copy(actionResult = "Đã đóng băng ${app.appName}") }
                }
                batteryRepository.insertForceStopLog(
                    ForceStopLog(
                        packageName = app.packageName,
                        appName = app.appName,
                        drainPercent = if (app.drainPercent > 0f) app.drainPercent else (app.ratePercentPerHour.toFloat().coerceAtLeast(0.8f)),
                        action = if (isFrozen) "unfreeze" else "freeze",
                        success = true,
                        reason = "manual",
                        userId = app.userId,
                        uid = app.uid,
                        deltaMah = app.deltaMah,
                        ratePercentPerHour = if (app.ratePercentPerHour > 0.0) app.ratePercentPerHour else 0.8,
                        verified = actionRes?.verified ?: true,
                        exitCode = actionRes?.exitCode ?: 0,
                        errorMessage = actionRes?.errorMessage
                    )
                )
                loadTopDrainingApps()
            } else {
                _uiState.update { it.copy(actionResult = "Lỗi thao tác đóng băng/rã đông") }
            }
        }
    }

    fun clearActionResult() {
        _uiState.update { it.copy(actionResult = null) }
    }

    fun addToException(packageName: String, appName: String) {
        viewModelScope.launch {
            appRepository.addException(packageName, appName, "Thêm từ Tổng quan")
            loadTopDrainingApps()
        }
    }

    fun allowActiveUseStop(app: AppBatteryInfo) {
        viewModelScope.launch {
            batteryRepository.setActiveUseStopAllowed(app.packageName, app.userId, true)
            loadTopDrainingApps()
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Ignore
        }
    }
}
