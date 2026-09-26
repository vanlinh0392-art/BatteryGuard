package com.pin.batteryguard.ui.screen.dashboard

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val isSetupCompleted: Boolean? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val batteryRepository: BatteryRepository,
    private val appRepository: AppRepository,
    private val settingsDataStore: SettingsDataStore,
    private val shizukuManager: ShizukuManager,
    private val forceStopManager: ForceStopManager
) : ViewModel() {

    private val _batteryState = MutableStateFlow(BatteryState())
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

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
        if (_batteryState.value != newState) {
            _batteryState.value = newState
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
                val verifiedToday = actionLogs.filter { it.verified && it.timestamp >= todayStart }
                val savedPercent = verifiedToday.sumOf { it.ratePercentPerHour }.toFloat()

                _uiState.update {
                    it.copy(
                        batteryState = state,
                        batteryHistory = history,
                        stoppedToday = verifiedToday.size,
                        savedPercent = savedPercent
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
            if (result.isSuccess) {
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
            if (result.isSuccess) {
                loadTopDrainingApps()
            }
        }
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
