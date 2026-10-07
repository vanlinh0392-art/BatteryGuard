package com.pin.batteryguard.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.pin.batteryguard.data.db.dao.FrozenAppDao
import com.pin.batteryguard.data.db.entity.AppUsageLog
import com.pin.batteryguard.data.db.entity.BatteryLog
import com.pin.batteryguard.data.db.entity.ForceStopLog
import com.pin.batteryguard.data.db.entity.FrozenApp
import com.pin.batteryguard.data.preferences.SettingsDataStore
import com.pin.batteryguard.data.repository.AppRepository
import com.pin.batteryguard.data.repository.BatteryRepository
import com.pin.batteryguard.data.repository.MonitoringStateStore
import com.pin.batteryguard.domain.battery.BatteryDrainDiffer
import com.pin.batteryguard.domain.battery.DecisionGuard
import com.pin.batteryguard.domain.battery.DetectionAction
import com.pin.batteryguard.domain.battery.DetectionContext
import com.pin.batteryguard.domain.battery.DrainPolicy
import com.pin.batteryguard.domain.battery.UidDrainSample
import com.pin.batteryguard.domain.model.MonitoringConfig
import com.pin.batteryguard.shizuku.BatteryStatsParser
import com.pin.batteryguard.shizuku.ForceStopManager
import com.pin.batteryguard.shizuku.ShizukuAutoStarter
import com.pin.batteryguard.shizuku.ShizukuManager
import com.pin.batteryguard.shizuku.UidPackageResolver
import com.pin.batteryguard.util.NotificationHelper
import com.pin.batteryguard.util.PackageHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.min

@AndroidEntryPoint
class BatteryMonitorService : LifecycleService() {
    @Inject lateinit var batteryRepository: BatteryRepository
    @Inject lateinit var appRepository: AppRepository
    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var shizukuManager: ShizukuManager
    @Inject lateinit var forceStopManager: ForceStopManager
    @Inject lateinit var batteryStatsParser: BatteryStatsParser
    @Inject lateinit var frozenAppDao: FrozenAppDao
    @Inject lateinit var monitoringStateStore: MonitoringStateStore
    @Inject lateinit var uidPackageResolver: UidPackageResolver
    @Inject lateinit var protectionChecker: AppProtectionChecker
    @Inject lateinit var appShieldManager: com.pin.batteryguard.domain.shield.AppShieldManager
    @Inject lateinit var automationCoordinator: com.pin.batteryguard.automation.AutomationCoordinator

    private val stateMutex = Mutex()
    private val differ = BatteryDrainDiffer()
    private val drainPolicy = DrainPolicy()
    private var screenReceiver: ScreenStateReceiver? = null
    private var config = MonitoringConfig()
    private var wakeLock: PowerManager.WakeLock? = null
    private var isCharging = false
    private var lastBatteryTemperature = 0f
    private var isCpuThrottled = false
    private var lastTempAlertTime = 0L
    private var screenOnRestoreComplete = false
    private var lastBreachesBeforeClear: Map<Int, Int> = emptyMap()
    private var lastNotifiedBatteryLevel = -1
    private var lastBatteryLogLevel = -1
    private var lastBatteryLogTime = 0L

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createNotificationChannels(this)
        startForeground(
            NotificationHelper.NOTIFICATION_ID_SERVICE,
            NotificationHelper.createMonitoringNotification(this, getBatteryLevelNow(), false, 0)
        )
        isCharging = (getSystemService(Context.BATTERY_SERVICE) as BatteryManager).isCharging
        registerReceivers()
        automationCoordinator.start(lifecycleScope)
        maybeCleanupOldData()

        lifecycleScope.launch {
            insertBatteryLog()
        }

        lifecycleScope.launch {
            settingsDataStore.migrateIfNeeded()
            settingsDataStore.configFlow.collect { newConfig ->
                stateMutex.withLock {
                    config = newConfig
                    reconcileMonitoringState()
                }
                updateNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PERIODIC_SCAN -> lifecycleScope.launch { stateMutex.withLock { runPeriodicScan() } }
            ACTION_COOL_DOWN -> lifecycleScope.launch { stateMutex.withLock { coolDownDevice() } }
            ACTION_RECONNECT_SHIZUKU -> lifecycleScope.launch { shizukuManager.forceRefresh() }
        }
        return START_STICKY
    }

    private fun registerReceivers() {
        screenReceiver = ScreenStateReceiver(
            onScreenEvent = { action, _ ->
                lifecycleScope.launch {
                    stateMutex.withLock {
                        if (action == Intent.ACTION_SCREEN_OFF) {
                            automationCoordinator.onScreenOff()
                            handleScreenOff()
                        } else {
                            automationCoordinator.onScreenOn()
                            handleScreenOn()
                        }
                    }
                }
            },
            onBatteryChanged = { batteryPct, temperature, charging ->
                lastBatteryTemperature = temperature
                automationCoordinator.onBatteryChanged(batteryPct, temperature, charging)
                lifecycleScope.launch {
                    checkTemperatureAlert(temperature)
                    var chargingChanged = false
                    stateMutex.withLock {
                        if (charging != isCharging) {
                            isCharging = charging
                            chargingChanged = true
                            if (charging) pauseMonitoring("charging") else reconcileMonitoringState()
                        }
                    }
                    if (batteryPct != lastNotifiedBatteryLevel || chargingChanged) {
                        lastNotifiedBatteryLevel = batteryPct
                        updateNotification(batteryPct)
                    }
                    val now = System.currentTimeMillis()
                    if (batteryPct != lastBatteryLogLevel || chargingChanged || (now - lastBatteryLogTime >= 15 * 60 * 1000L)) {
                        lastBatteryLogLevel = batteryPct
                        lastBatteryLogTime = now
                        insertBatteryLog(level = batteryPct, temperature = temperature, charging = charging)
                    }
                }
            }
        )
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    private suspend fun reconcileMonitoringState() {
        if (!config.isMonitoringEnabled) {
            pauseMonitoring("disabled")
            return
        }
        if (!isScreenOff() || isCharging) {
            cancelNextScan()
            val hadSession = monitoringStateStore.load() != null
            monitoringStateStore.clear()
            if (!isScreenOff() && (!screenOnRestoreComplete || hadSession)) {
                unfreezeDeepSleepApps()
                screenOnRestoreComplete = true
            }
            return
        }
        val persisted = monitoringStateStore.load()
        if (persisted == null) captureNewBaseline() else scheduleNextScan(initialDelayMinutes())
    }

    private suspend fun handleScreenOff() {
        insertBatteryLog()
        // Khôi phục cài đặt App Shield ngay khi khóa màn hình nếu người dùng bật tùy chọn này
        try {
            val shieldConfig = settingsDataStore.shieldConfigFlow.first()
            if (shieldConfig.revertOnScreenOff) {
                appShieldManager.restoreSettings(reason = "Khóa màn hình (Screen Off)")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Lỗi khôi phục App Shield khi tắt màn hình: ${e.message}")
        }

        if (!config.isMonitoringEnabled || isCharging) return
        android.util.Log.i(TAG, "Screen off: starting a new Android 15/16 drain session")
        screenOnRestoreComplete = false
        freezeDeepSleepApps()
        captureNewBaseline()
    }

    private suspend fun handleScreenOn() {
        insertBatteryLog()
        android.util.Log.i(TAG, "Screen on: ending drain session and restoring deep-sleep apps")
        cancelNextScan()
        lastBreachesBeforeClear = monitoringStateStore.load()?.consecutiveBreaches ?: emptyMap()
        monitoringStateStore.clear()
        unfreezeDeepSleepApps()
        screenOnRestoreComplete = true
    }

    private suspend fun captureNewBaseline() {
        val snapshot = batteryStatsParser.captureSnapshot()
        if (snapshot.consumers.isEmpty() || snapshot.capacityMah <= 0.0) {
            android.util.Log.w(TAG, "Baseline unavailable; retrying in 10 minutes")
            scheduleNextScan(CONFIRMATION_MINUTES)
            return
        }
        // Preserve breach counters across screen on/off cycles
        val breaches = lastBreachesBeforeClear.ifEmpty {
            monitoringStateStore.load()?.consecutiveBreaches ?: emptyMap()
        }
        monitoringStateStore.save(snapshot, breaches)
        lastBreachesBeforeClear = emptyMap()
        scheduleNextScan(initialDelayMinutes())
        android.util.Log.i(TAG, "Baseline saved: ${snapshot.consumers.size} UIDs, capacity=${snapshot.capacityMah}mAh, preserved ${breaches.size} breach counters")
    }

    private suspend fun runPeriodicScan() = withContext(Dispatchers.IO) {
        acquireWakeLock()
        try {
            if (!config.isMonitoringEnabled || !isScreenOff() || isCharging) {
                pauseMonitoring("screen_on_or_charging")
                return@withContext
            }
            if (isCpuThrottled) {
                coolDownDevice()
                scheduleNextScan(CONFIRMATION_MINUTES)
                return@withContext
            }

            val persisted = monitoringStateStore.load()
            if (persisted == null) {
                captureNewBaseline()
                return@withContext
            }
            val current = batteryStatsParser.captureSnapshot()
            if (chargeCounterMovedBackwards(persisted.snapshot.chargeCounterUah, current.chargeCounterUah)) {
                android.util.Log.w(TAG, "Charge counter increased while locked; rebaselining")
                monitoringStateStore.save(current, persisted.consecutiveBreaches)
                scheduleNextScan(initialDelayMinutes())
                return@withContext
            }
            val diff = differ.diff(persisted.snapshot, current)
            if (!diff.isValid) {
                android.util.Log.w(TAG, "Snapshot diff invalid (${diff.invalidReason}); rebaselining without action")
                monitoringStateStore.save(current, persisted.consecutiveBreaches)
                scheduleNextScan(initialDelayMinutes())
                return@withContext
            }

            val resolved = uidPackageResolver.resolveAll()
            val exceptions = appRepository.getExceptionPackages()
            val updatedBreaches = persisted.consecutiveBreaches.toMutableMap()
            val usageLogs = mutableListOf<AppUsageLog>()
            val stoppedApps = mutableListOf<String>()
            var hasActionableWarning = false
            var performedAction = false

            for (sample in diff.samples) {
                val uidInfo = resolved[sample.uid]
                val packages = uidInfo?.packageNames.orEmpty()
                val primaryPackage = packages.singleOrNull() ?: packages.firstOrNull() ?: "uid:${sample.uid}"
                val appName = if (packages.isEmpty()) primaryPackage else PackageHelper.getAppName(this@BatteryMonitorService, primaryPackage)
                val isSharedUid = packages.size != 1
                val isSystem = packages.any(protectionChecker::isSystemApp)
                val isProtected = packages.any { protectionChecker.isProtected(it, sample.uid) }
                val isException = packages.any(exceptions::contains)
                val shouldCheckActiveUse = sample.ratePercentPerHour >= config.warningThresholdPercentPerHour &&
                    !isSharedUid && !isSystem && !isProtected && !isException
                val activeUse = shouldCheckActiveUse && protectionChecker.hasActiveForegroundService(primaryPackage)
                val activeUseOverride = !isSharedUid && batteryRepository.isActiveUseStopAllowed(primaryPackage, sample.userId)
                val latestAction = if (!isSharedUid) batteryRepository.getLatestVerifiedLog(primaryPackage, sample.userId) else null
                val inCooldown = latestAction != null && System.currentTimeMillis() - latestAction.timestamp < ACTION_COOLDOWN_MILLIS
                val verifiedStops = if (!isSharedUid) {
                    batteryRepository.getVerifiedStopCount(primaryPackage, sample.userId, System.currentTimeMillis() - REPEAT_WINDOW_MILLIS)
                } else 0
                val decision = drainPolicy.evaluate(
                    sample,
                    DetectionContext(
                        consecutiveBreaches = persisted.consecutiveBreaches[sample.uid] ?: 0,
                        verifiedStopsInWindow = verifiedStops,
                        isException = isException,
                        isSystemApp = isSystem,
                        isSharedUid = isSharedUid,
                        isProtected = isProtected,
                        isActiveUse = activeUse,
                        activeUseOverride = activeUseOverride,
                        isInActionCooldown = inCooldown
                    ),
                    config
                )
                updatedBreaches[sample.uid] = decision.nextConsecutiveBreaches
                if (decision.action == DetectionAction.WARN && decision.guard == DecisionGuard.NONE) hasActionableWarning = true

                var actionResult: com.pin.batteryguard.domain.battery.ActionResult? = null
                if ((decision.action == DetectionAction.FORCE_STOP || decision.action == DetectionAction.FREEZE) && isScreenOff()) {
                    performedAction = true
                    actionResult = if (decision.action == DetectionAction.FREEZE) {
                        val res = forceStopManager.freezeDetailed(primaryPackage, sample.userId)
                        if (res.isSuccess || res.commandSucceeded) {
                            frozenAppDao.insert(FrozenApp(sample.userId, primaryPackage, appName, isManual = false))
                        }
                        res
                    } else {
                        forceStopManager.forceStopDetailed(primaryPackage, sample.userId)
                    }
                    if (actionResult.isSuccess) stoppedApps += appName
                    batteryRepository.insertForceStopLog(
                        ForceStopLog(
                            packageName = primaryPackage,
                            appName = appName,
                            drainPercent = sample.ratePercentPerHour.toFloat(),
                            action = decision.action.name.lowercase(),
                            success = actionResult.isSuccess,
                            reason = decision.reason,
                            userId = sample.userId,
                            uid = sample.uid,
                            deltaMah = sample.deltaMah,
                            ratePercentPerHour = sample.ratePercentPerHour,
                            verified = actionResult.verified,
                            exitCode = actionResult.exitCode,
                            errorMessage = actionResult.errorMessage
                        )
                    )
                }

                usageLogs += sample.toUsageLog(
                    packageName = primaryPackage,
                    appName = appName,
                    periodEnd = current.capturedAtMillis,
                    decision = decision.action.name.lowercase(),
                    skipReason = if (decision.guard == DecisionGuard.NONE) "" else decision.guard.name.lowercase()
                )
                android.util.Log.i(
                    POLICY_TAG,
                    "u${sample.userId} $primaryPackage ${"%.3f".format(sample.deltaMah)}mAh ${"%.3f".format(sample.ratePercentPerHour)}%/h -> ${decision.action}/${decision.guard}" +
                        (actionResult?.let { " verified=${it.verified} exit=${it.exitCode}" } ?: "")
                )
            }

            if (usageLogs.isNotEmpty()) batteryRepository.insertAppUsageLogs(usageLogs)
            val nextBaseline = if (performedAction && isScreenOff()) batteryStatsParser.captureSnapshot() else current
            monitoringStateStore.save(
                snapshot = nextBaseline,
                breaches = updatedBreaches,
                startedAtMillis = persisted.startedAtMillis,
                startedAtElapsedRealtime = persisted.startedAtElapsedRealtime
            )
            insertBatteryLog()
            if (stoppedApps.isNotEmpty() && config.notifyBeforeStop) {
                NotificationHelper.showForceStopSummary(
                    this@BatteryMonitorService,
                    stoppedApps.size,
                    usageLogs.filter { it.decision == "force_stop" || it.decision == "freeze" }
                        .sumOf { it.ratePercentPerHour }.toFloat()
                )
            }
            updateNotification()
            val nextDelay = when {
                performedAction -> ACTION_COOLDOWN_MINUTES
                hasActionableWarning -> CONFIRMATION_MINUTES
                else -> cleanIntervalMinutes()
            }
            scheduleNextScan(nextDelay)
        } catch (error: Exception) {
            android.util.Log.e(TAG, "Periodic scan failed", error)
            scheduleNextScan(CONFIRMATION_MINUTES)
        } finally {
            releaseWakeLock()
        }
    }

    private fun UidDrainSample.toUsageLog(
        packageName: String,
        appName: String,
        periodEnd: Long,
        decision: String,
        skipReason: String
    ) = AppUsageLog(
        packageName = packageName,
        appName = appName,
        foregroundTimeMs = evidence.foregroundTimeMs,
        backgroundTimeMs = evidence.backgroundTimeMs,
        estimatedDrainPercent = ratePercentPerHour.toFloat(),
        periodStart = periodEnd - durationMillis,
        periodEnd = periodEnd,
        userId = userId,
        uid = uid,
        deltaMah = deltaMah,
        ratePercentPerHour = ratePercentPerHour,
        evidence = "cpu=${evidence.cpuMah},wakelock=${evidence.wakeLockMah},audio=${evidence.audioMah},gnss=${evidence.gnssMah},sensor=${evidence.sensorMah}",
        decision = decision,
        skipReason = skipReason
    )

    private suspend fun freezeDeepSleepApps() {
        val exceptions = appRepository.getExceptionPackages()
        frozenAppDao.getAll().filter { it.isManual && it.packageName !in exceptions }.forEach { app ->
            if (!forceStopManager.isPackageFrozen(app.packageName, app.userId)) {
                forceStopManager.freezeDetailed(app.packageName, app.userId)
            }
        }
    }

    private suspend fun unfreezeDeepSleepApps() {
        // CHỈ rã đông các app tự động ngầm (!isManual). TUYỆT ĐỐI không rã đông app thủ công của người dùng!
        val autoApps = frozenAppDao.getAll().filter { !it.isManual }
        for (app in autoApps) {
            val res = forceStopManager.unfreezeDetailed(app.packageName, app.userId)
            if (res.isSuccess || res.commandSucceeded) {
                frozenAppDao.delete(app.packageName, app.userId)
            }
        }
    }

    private suspend fun coolDownDevice() {
        val exceptions = appRepository.getExceptionPackages()
        val latest = batteryRepository.getLatestAppUsagePeriod().first()
            .filter { it.skipReason.isBlank() && it.packageName !in exceptions && it.ratePercentPerHour >= config.drainThresholdPercent }
            .take(3)
        var stopped = 0
        for (app in latest) {
            val result = forceStopManager.forceStopDetailed(app.packageName, app.userId)
            if (result.isSuccess) stopped++
        }
        if (stopped > 0) NotificationHelper.showCoolDownSuccessNotification(this, stopped)
    }

    private fun scheduleNextScan(delayMinutes: Int) {
        if (!config.isMonitoringEnabled || !isScreenOff() || isCharging) return
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = scanPendingIntent(PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + delayMinutes * 60_000L,
            pendingIntent
        )
        android.util.Log.d(TAG, "Next inexact idle scan in $delayMinutes minutes")
    }

    private fun cancelNextScan() {
        val pendingIntent = scanPendingIntent(PendingIntent.FLAG_NO_CREATE) ?: return
        (getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun scanPendingIntent(extraFlag: Int): PendingIntent? {
        val intent = Intent(this, BatteryMonitorService::class.java).setAction(ACTION_PERIODIC_SCAN)
        return PendingIntent.getForegroundService(
            this,
            SCAN_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or extraFlag
        )
    }

    private suspend fun pauseMonitoring(reason: String) {
        android.util.Log.d(TAG, "Monitoring paused: $reason")
        cancelNextScan()
        monitoringStateStore.clear()
    }

    private fun initialDelayMinutes() = maxOf(15, config.monitoringPeriodMinutes.coerceIn(10, 30))
    private fun cleanIntervalMinutes() = min(60, config.monitoringPeriodMinutes.coerceIn(10, 30) * 2)
    private fun isScreenOff() = !(getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive

    private fun chargeCounterMovedBackwards(previous: Long?, current: Long?): Boolean =
        previous != null && current != null && current > previous + 1_000L

    private suspend fun insertBatteryLog(
        level: Int = getBatteryLevelNow(),
        temperature: Float = lastBatteryTemperature,
        charging: Boolean = isCharging
    ) {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val curNow = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val sticky = try {
            registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        } catch (_: Exception) {
            null
        }
        val volt = sticky?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val chargePlug = sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val chargeType = when (chargePlug) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> if (charging) "Charging" else "None"
        }
        batteryRepository.insertBatteryLog(
            BatteryLog(
                level = level,
                temperature = if (temperature > 0f) temperature else lastBatteryTemperature,
                voltage = volt,
                currentNow = curNow,
                currentAvg = 0,
                isCharging = charging,
                chargeType = chargeType,
                screenOn = pm.isInteractive
            )
        )
    }

    private suspend fun checkTemperatureAlert(temp: Float) {
        isCpuThrottled = temp >= 42f
        if (isCpuThrottled && System.currentTimeMillis() - lastTempAlertTime > 10 * 60 * 1000L) {
            lastTempAlertTime = System.currentTimeMillis()
            NotificationHelper.showHighTempAlertNotification(this, temp)
        }
    }

    private fun maybeCleanupOldData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val preferences = getSharedPreferences("maintenance", Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            if (now - preferences.getLong("last_cleanup", 0L) >= DAY_MILLIS) {
                batteryRepository.cleanupOldData(30)
                preferences.edit().putLong("last_cleanup", now).apply()
            }
        }
    }

    private fun updateNotification(overrideLevel: Int? = null) {
        lifecycleScope.launch {
            val today = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            val count = runCatching { batteryRepository.getForceStopCountSinceDirect(today) }.getOrDefault(0)
            val currentLevel = overrideLevel?.takeIf { it in 0..100 } ?: getBatteryLevelNow()
            val notification = NotificationHelper.createMonitoringNotification(
                this@BatteryMonitorService,
                currentLevel,
                config.isMonitoringEnabled,
                count
            )
            (getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
                .notify(NotificationHelper.NOTIFICATION_ID_SERVICE, notification)
        }
    }

    private fun getBatteryLevelNow(): Int {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val cap = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (cap in 0..100) return cap
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else 100
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BatteryGuard::SnapshotScan")
        }
        wakeLock?.acquire(30_000L)
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
    }

    override fun onDestroy() {
        automationCoordinator.stop()
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    companion object {
        const val ACTION_COOL_DOWN = "com.pin.batteryguard.ACTION_COOL_DOWN"
        const val ACTION_PERIODIC_SCAN = "com.pin.batteryguard.ACTION_PERIODIC_SCAN"
        const val ACTION_RECONNECT_SHIZUKU = "com.pin.batteryguard.ACTION_RECONNECT_SHIZUKU"
        private const val TAG = "BatteryMonitorService"
        private const val POLICY_TAG = "BatteryDrainPolicy"
        private const val SCAN_REQUEST_CODE = 10
        private const val CONFIRMATION_MINUTES = 10
        private const val ACTION_COOLDOWN_MINUTES = 30
        private const val ACTION_COOLDOWN_MILLIS = 30 * 60 * 1000L
        private const val REPEAT_WINDOW_MILLIS = 3 * 60 * 60 * 1000L
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}
