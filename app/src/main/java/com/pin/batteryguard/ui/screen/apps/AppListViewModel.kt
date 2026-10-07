package com.pin.batteryguard.ui.screen.apps

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pin.batteryguard.data.db.dao.FrozenAppDao
import com.pin.batteryguard.data.db.entity.ForceStopLog
import com.pin.batteryguard.data.db.entity.FrozenApp
import com.pin.batteryguard.data.repository.AppRepository
import com.pin.batteryguard.data.repository.BatteryRepository
import com.pin.batteryguard.domain.model.AppBatteryInfo
import com.pin.batteryguard.domain.model.MonitoringConfig
import com.pin.batteryguard.data.preferences.SettingsDataStore
import com.pin.batteryguard.shizuku.ForceStopManager
import com.pin.batteryguard.shizuku.UidPackageResolver
import com.pin.batteryguard.util.PackageHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AppFilter { ALL, RUNNING, STOPPED, SYSTEM }

data class AppListUiState(
    val apps: List<AppBatteryInfo> = emptyList(),
    val filteredApps: List<AppBatteryInfo> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: AppFilter = AppFilter.ALL,
    val isLoading: Boolean = true,
    val actionResult: String? = null
)

@HiltViewModel
class AppListViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
    private val batteryRepository: BatteryRepository,
    private val forceStopManager: ForceStopManager,
    private val uidPackageResolver: UidPackageResolver,
    private val settingsDataStore: SettingsDataStore,
    private val frozenAppDao: FrozenAppDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppListUiState())
    val uiState: StateFlow<AppListUiState> = _uiState.asStateFlow()

    private var lastExcludeSystemApps: Boolean? = null

    init {
        loadApps()
        // React to config changes (e.g. excludeSystemApps toggle) and reload automatically
        viewModelScope.launch {
            settingsDataStore.configFlow.collect { config ->
                val exclude = config.excludeSystemApps
                if (lastExcludeSystemApps != null && lastExcludeSystemApps != exclude) {
                    loadApps()
                }
                lastExcludeSystemApps = exclude
            }
        }
    }

    fun loadApps() {
        _uiState.update { it.copy(isLoading = true) }
        // PackageManager and Shizuku calls are blocking. More importantly,
        // one unavailable source must not abort the inventory fallback.
        viewModelScope.launch(Dispatchers.IO) {
            val exceptionPackages = runSuspendCatching { appRepository.getExceptionPackages() }
                .getOrDefault(emptyList())
            val latest = runSuspendCatching { batteryRepository.getLatestAppUsagePeriod().first() }
                .getOrDefault(emptyList())
            val config = runSuspendCatching { settingsDataStore.configFlow.first() }
                .getOrDefault(MonitoringConfig())
            val latestByUid = latest.associateBy { "${it.userId}:${it.uid}" }
            val resolved = runSuspendCatching { uidPackageResolver.resolveAll() }
                .getOrDefault(emptyMap())
            val mappedList = mutableListOf<AppBatteryInfo>()
            val frozenByUser = mutableMapOf<Int, Set<String>>()
            for (userId in resolved.values.map { it.userId }.distinct()) {
                frozenByUser[userId] = runSuspendCatching { forceStopManager.listFrozenPackages(userId) }
                    .getOrDefault(emptySet())
            }

            val activeUseOverrides = runSuspendCatching { batteryRepository.getAllActiveUseOverrides() }
                .getOrDefault(emptyMap())

            for (uidInfo in resolved.values.sortedWith(compareBy({ it.userId }, { it.primaryPackage ?: "" }))) {
                for (packageName in uidInfo.packageNames) {
                    if (config.excludeSystemApps && PackageHelper.isSystemApp(context, packageName)) continue
                    // A single stale package or binder error should not
                    // prevent all other apps from being rendered.
                    buildResolvedApp(
                        uidInfo,
                        packageName,
                        latestByUid,
                        exceptionPackages,
                        frozenByUser[uidInfo.userId].orEmpty(),
                        activeUseOverrides
                    )?.let(mappedList::add)
                }
            }

            // Shizuku is needed for multi-user/UID accuracy, but not for
            // showing the Applications tab. Fall back to the owner inventory
            // when the resolver is empty or temporarily unavailable.
            if (mappedList.isEmpty()) {
                val ownerFrozen = frozenByUser.getOrPut(0) {
                    runSuspendCatching { forceStopManager.listFrozenPackages(0) }.getOrDefault(emptySet())
                }
                mappedList += loadOwnerInventory(exceptionPackages, ownerFrozen, config.excludeSystemApps)
            }

            val uniqueApps = mappedList.distinctBy { "${it.userId}:${it.uid}:${it.packageName}" }
            _uiState.update {
                it.copy(
                    apps = uniqueApps,
                    isLoading = false,
                    actionResult = if (uniqueApps.isEmpty()) {
                        "Không đọc được danh sách ứng dụng từ hệ thống"
                    } else {
                        it.actionResult
                    }
                )
            }
            applyFilterAndSearch()
        }
    }

    private suspend fun buildResolvedApp(
        uidInfo: com.pin.batteryguard.domain.battery.ResolvedUid,
        packageName: String,
        latestByUid: Map<String, com.pin.batteryguard.data.db.entity.AppUsageLog>,
        exceptionPackages: List<String>,
        frozenPackages: Set<String>,
        activeUseOverrides: Map<String, Boolean>
    ): AppBatteryInfo? {
        return try {
            val log = if (!uidInfo.isShared) latestByUid["${uidInfo.userId}:${uidInfo.uid}"] else null
            val activeUseOverride = activeUseOverrides["${uidInfo.userId}:$packageName"] ?: false
            val isSystem = PackageHelper.isSystemApp(context, packageName)
            AppBatteryInfo(
                packageName = packageName,
                appName = PackageHelper.getAppName(context, packageName),
                appIcon = PackageHelper.getAppIcon(context, packageName),
                drainPercent = log?.ratePercentPerHour?.toFloat() ?: 0f,
                foregroundTimeMs = log?.foregroundTimeMs ?: 0L,
                backgroundTimeMs = log?.backgroundTimeMs ?: 0L,
                isRunning = isRunningSafely(packageName),
                isException = exceptionPackages.contains(packageName),
                isFrozen = packageName in frozenPackages,
                uid = uidInfo.uid,
                userId = uidInfo.userId,
                packageNames = uidInfo.packageNames,
                isSharedUid = uidInfo.isShared,
                deltaMah = log?.deltaMah ?: 0.0,
                ratePercentPerHour = log?.ratePercentPerHour ?: 0.0,
                evidence = log?.evidence ?: "Chưa có mẫu screen-off",
                decision = when {
                    uidInfo.isShared -> "shared_uid: chỉ hiển thị, chưa quy trách nhiệm"
                    log == null -> "inventory: chờ mẫu screen-off"
                    log.skipReason.isBlank() -> log.decision
                    else -> "${log.decision}: ${log.skipReason}"
                },
                isActionAllowed = !isSystem && !uidInfo.isShared &&
                    (log?.skipReason != "active_use" || activeUseOverride),
                activeUseOverride = activeUseOverride
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun loadOwnerInventory(
        exceptionPackages: List<String>,
        frozenPackages: Set<String>,
        excludeSystemApps: Boolean
    ): List<AppBatteryInfo> {
        return try {
            val apps = mutableListOf<AppBatteryInfo>()
            val installed = context.packageManager
                .getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { it.packageName != context.packageName }
                .filter { !excludeSystemApps || !PackageHelper.isSystemApp(context, it.packageName) }
            for (appInfo in installed) {
                    val packageName = appInfo.packageName
                    val userId = 0
                    val isSystem = PackageHelper.isSystemApp(context, packageName)
                    apps += AppBatteryInfo(
                        packageName = packageName,
                        appName = PackageHelper.getAppName(context, packageName),
                        appIcon = PackageHelper.getAppIcon(context, packageName),
                        drainPercent = 0f,
                        foregroundTimeMs = 0L,
                        backgroundTimeMs = 0L,
                        isRunning = isRunningSafely(packageName),
                        isException = exceptionPackages.contains(packageName),
                        isFrozen = packageName in frozenPackages,
                        uid = appInfo.uid,
                        userId = userId,
                        evidence = "Chưa có mẫu screen-off",
                        decision = "inventory: chờ mẫu screen-off",
                        isActionAllowed = !isSystem
                    )
            }
            apps
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isRunningSafely(packageName: String): Boolean =
        runCatching { PackageHelper.isAppRunning(context, packageName) }.getOrDefault(false)

    private suspend fun <T> runSuspendCatching(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (error: Throwable) {
            Result.failure(error)
        }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilterAndSearch()
    }

    fun onFilterSelected(filter: AppFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
        applyFilterAndSearch()
    }

    private fun applyFilterAndSearch() {
        val state = _uiState.value
        var list = state.apps

        if (state.searchQuery.isNotEmpty()) {
            list = list.filter {
                it.appName.contains(state.searchQuery, ignoreCase = true) ||
                    it.packageName.contains(state.searchQuery, ignoreCase = true)
            }
        }

        list = when (state.selectedFilter) {
            AppFilter.ALL -> list
            AppFilter.RUNNING -> list.filter { it.isRunning }
            AppFilter.STOPPED -> list.filter { !it.isRunning && !it.isFrozen }
            AppFilter.SYSTEM -> list.filter { it.isException }
        }

        _uiState.update { it.copy(filteredApps = list.sortedByDescending { app -> app.ratePercentPerHour }) }
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
                _uiState.update { it.copy(actionResult = "Đã buộc dừng ${app.appName} (user ${app.userId})") }
                loadApps()
            } else {
                _uiState.update { it.copy(actionResult = "Lỗi dừng: ${result.exceptionOrNull()?.message}") }
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
                } else {
                    frozenAppDao.insert(FrozenApp(app.userId, app.packageName, app.appName, isManual = true))
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
                val msg = if (isFrozen) "Đã bỏ đóng băng" else "Đã đóng băng"
                _uiState.update { it.copy(actionResult = "$msg ${app.appName} (user ${app.userId})") }
                loadApps()
            } else {
                _uiState.update { it.copy(actionResult = "Lỗi xử lý đóng băng") }
            }
        }
    }

    fun addToException(packageName: String, appName: String) {
        viewModelScope.launch {
            appRepository.addException(packageName, appName, "Thêm từ Danh sách ứng dụng")
            _uiState.update { it.copy(actionResult = "Đã thêm $appName vào danh sách ngoại lệ") }
            loadApps()
        }
    }

    fun allowActiveUseStop(app: AppBatteryInfo) {
        viewModelScope.launch {
            batteryRepository.setActiveUseStopAllowed(app.packageName, app.userId, true)
            _uiState.update {
                it.copy(actionResult = "Đã cho phép tự dừng ${app.appName} khi đang có foreground service")
            }
            loadApps()
        }
    }

    fun clearActionResult() {
        _uiState.update { it.copy(actionResult = null) }
    }
}
