package com.pin.batteryguard.ui.screen.shield

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pin.batteryguard.data.db.dao.ShieldedAppDao
import com.pin.batteryguard.data.db.entity.ShieldedAppEntity
import com.pin.batteryguard.data.preferences.SettingsDataStore
import com.pin.batteryguard.domain.model.AppShieldConfig
import com.pin.batteryguard.domain.shield.AppShieldManager
import com.pin.batteryguard.service.shield.AppShieldAccessibilityService
import com.pin.batteryguard.shizuku.ShizukuManager
import com.pin.batteryguard.util.PackageHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class AppShieldFilterTab(val label: String) {
    ALL("Tất cả"),
    SHIELDED("Đang bảo vệ"),
    BANKS("Ngân hàng")
}

data class AppShieldUiItem(
    val packageName: String,
    val appName: String,
    val isShielded: Boolean,
    val isPresetBank: Boolean,
    val isAutoDetected: Boolean = false
)

data class AppShieldUiState(
    val config: AppShieldConfig = AppShieldConfig(),
    val isCurrentlyHidden: Boolean = false,
    val triggeredPackage: String = "",
    val hasWriteSecureSettings: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val isShizukuReady: Boolean = false,
    val apps: List<AppShieldUiItem> = emptyList(),
    val filteredApps: List<AppShieldUiItem> = emptyList(),
    val searchQuery: String = "",
    val filterTab: AppShieldFilterTab = AppShieldFilterTab.ALL,
    val autoDetectedCount: Int = 0,
    val manuallyShieldedCount: Int = 0,
    val isLoading: Boolean = true,
    val message: String? = null
)

@HiltViewModel
class AppShieldViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appShieldManager: AppShieldManager,
    private val shieldedAppDao: ShieldedAppDao,
    private val settingsDataStore: SettingsDataStore,
    private val shizukuManager: ShizukuManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppShieldUiState())
    val uiState: StateFlow<AppShieldUiState> = _uiState.asStateFlow()

    private val searchQueryFlow = MutableStateFlow("")
    private val filterTabFlow = MutableStateFlow(AppShieldFilterTab.ALL)
    private var cachedInstalledUserApps: List<Pair<String, String>>? = null

    init {
        refreshPermissions()

        // Lắng nghe cấu hình App Shield
        viewModelScope.launch {
            settingsDataStore.shieldConfigFlow.collectLatest { config ->
                _uiState.value = _uiState.value.copy(config = config)
            }
        }

        // Lắng nghe trạng thái ẩn hiện live từ Room snapshot
        viewModelScope.launch {
            appShieldManager.currentSnapshotFlow.collectLatest { snapshot ->
                _uiState.value = _uiState.value.copy(
                    isCurrentlyHidden = snapshot?.isCurrentlyHidden == true,
                    triggeredPackage = snapshot?.triggeredPackage ?: ""
                )
            }
        }

        // Tải danh sách ứng dụng và kết hợp bộ lọc tìm kiếm & tab
        viewModelScope.launch {
            combine(
                shieldedAppDao.getAllShieldedApps(),
                searchQueryFlow,
                filterTabFlow,
                settingsDataStore.shieldConfigFlow
            ) { dbShielded, query, tab, config ->
                val allUserApps = loadAllInstalledUserApps()
                val shieldedMap = dbShielded.associateBy { it.packageName }

                val merged = allUserApps.map { (pkg, name) ->
                    val isBank = appShieldManager.isSensitiveSecurityPackage(pkg)
                    val isManuallySelected = shieldedMap[pkg]?.isEnabled == true
                    val isAutoProtected = config.autoDetectBanks && isBank

                    AppShieldUiItem(
                        packageName = pkg,
                        appName = name,
                        isShielded = isAutoProtected || isManuallySelected,
                        isPresetBank = isBank,
                        isAutoDetected = isAutoProtected
                    )
                }.sortedWith(
                    compareByDescending<AppShieldUiItem> { it.isShielded }
                        .thenByDescending { it.isPresetBank }
                        .thenBy { it.appName.lowercase() }
                )

                val tabFiltered = when (tab) {
                    AppShieldFilterTab.ALL -> merged
                    AppShieldFilterTab.SHIELDED -> merged.filter { it.isShielded }
                    AppShieldFilterTab.BANKS -> merged.filter { it.isPresetBank }
                }

                val finalFiltered = if (query.isBlank()) {
                    tabFiltered
                } else {
                    tabFiltered.filter {
                        it.appName.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
                    }
                }

                val autoCount = merged.count { it.isAutoDetected }
                val manualCount = merged.count { !it.isAutoDetected && it.isShielded }

                Triple(merged, finalFiltered, Pair(autoCount, manualCount))
            }.collectLatest { (all, filtered, counts) ->
                _uiState.value = _uiState.value.copy(
                    apps = all,
                    filteredApps = filtered,
                    autoDetectedCount = counts.first,
                    manuallyShieldedCount = counts.second,
                    isLoading = false
                )
            }
        }
    }

    fun refreshPermissions() {
        val hasSecure = appShieldManager.hasWriteSecureSettings()
        val isAcc = isAccessibilityServiceEnabled()
        val isShizuku = shizukuManager.isReady()
        _uiState.value = _uiState.value.copy(
            hasWriteSecureSettings = hasSecure,
            isAccessibilityEnabled = isAcc,
            isShizukuReady = isShizuku
        )
    }

    fun onSearchQueryChanged(query: String) {
        searchQueryFlow.value = query
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun onFilterTabChanged(tab: AppShieldFilterTab) {
        filterTabFlow.value = tab
        _uiState.value = _uiState.value.copy(filterTab = tab)
    }

    fun setMasterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val updated = _uiState.value.config.copy(isEnabled = enabled)
            settingsDataStore.updateShieldConfig(updated)
        }
    }

    fun setAutoRevertMinutes(minutes: Int) {
        viewModelScope.launch {
            val updated = _uiState.value.config.copy(autoRevertMinutes = minutes)
            settingsDataStore.updateShieldConfig(updated)
        }
    }

    fun updateToggle(
        autoDetectBanks: Boolean? = null,
        revertOnScreenOff: Boolean? = null,
        hideDevOptions: Boolean? = null,
        hideAdb: Boolean? = null,
        hideWirelessAdb: Boolean? = null,
        hideAccessibility: Boolean? = null,
        relaunchApp: Boolean? = null,
        autoRestartShizuku: Boolean? = null
    ) {
        viewModelScope.launch {
            val current = _uiState.value.config
            val updated = current.copy(
                autoDetectBanks = autoDetectBanks ?: current.autoDetectBanks,
                revertOnScreenOff = revertOnScreenOff ?: current.revertOnScreenOff,
                hideDevOptions = hideDevOptions ?: current.hideDevOptions,
                hideAdb = hideAdb ?: current.hideAdb,
                hideWirelessAdb = hideWirelessAdb ?: current.hideWirelessAdb,
                hideAccessibility = hideAccessibility ?: current.hideAccessibility,
                relaunchApp = relaunchApp ?: current.relaunchApp,
                autoRestartShizuku = autoRestartShizuku ?: current.autoRestartShizuku
            )
            settingsDataStore.updateShieldConfig(updated)
        }
    }

    fun toggleAppShield(item: AppShieldUiItem, enabled: Boolean) {
        viewModelScope.launch {
            val entity = ShieldedAppEntity(
                packageName = item.packageName,
                appName = item.appName,
                isEnabled = enabled,
                isPresetBank = item.isPresetBank
            )
            shieldedAppDao.insertOrUpdate(entity)
        }
    }

    fun selectAllBanks() {
        viewModelScope.launch {
            val bankItems = _uiState.value.apps.filter { it.isPresetBank }
            val entities = bankItems.map {
                ShieldedAppEntity(
                    packageName = it.packageName,
                    appName = it.appName,
                    isEnabled = true,
                    isPresetBank = true
                )
            }
            shieldedAppDao.insertAll(entities)
            _uiState.value = _uiState.value.copy(message = "Đã chọn ${entities.size} ứng dụng ngân hàng.")
        }
    }

    fun manualHide() {
        viewModelScope.launch {
            val success = appShieldManager.hideSettingsForApp("com.manual.test")
            _uiState.value = _uiState.value.copy(
                message = if (success) "Đã ẩn Developer Options & ADB thành công!" else "Không thể ẩn (Kiểm tra quyền WRITE_SECURE_SETTINGS)"
            )
        }
    }

    fun manualRevert() {
        viewModelScope.launch {
            val success = appShieldManager.restoreSettings("Người dùng bấm khôi phục thủ công")
            _uiState.value = _uiState.value.copy(
                message = if (success) "Đã khôi phục cài đặt và kích hoạt Shizuku!" else "Không có cài đặt nào đang bị ẩn."
            )
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    private suspend fun loadAllInstalledUserApps(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val cached = cachedInstalledUserApps
        if (cached != null) return@withContext cached
        val pm = context.packageManager
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val list = installed
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || isLikelyBankApp(it.packageName, "") }
            .filter { it.packageName != context.packageName }
            .map { appInfo ->
                val label = pm.getApplicationLabel(appInfo).toString()
                Pair(appInfo.packageName, label)
            }
        cachedInstalledUserApps = list
        list
    }

    fun invalidateAppListCache() {
        cachedInstalledUserApps = null
    }

    private fun isLikelyBankApp(packageName: String, label: String): Boolean {
        val lowerPkg = packageName.lowercase()
        val lowerLabel = label.lowercase()
        val bankKeywords = listOf(
            "bank", "vcb", "momo", "zalopay", "timo", "cake", "bidv", "vietinbank",
            "agribank", "techcombank", "mbmobile", "vpbank", "tpb", "acb", "sacombank",
            "shb", "msb", "hdbank", "ocb", "scb", "vib", "vtpay", "vnpay"
        )
        return bankKeywords.any { lowerPkg.contains(it) || lowerLabel.contains(it) }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        // 1. Kiểm tra trực tiếp liveness từ service instance đã kết nối
        if (AppShieldAccessibilityService.isRunning) {
            return true
        }

        // 2. Kiểm tra qua AccessibilityManager chính thức của hệ điều hành
        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            val enabledServices = am?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).orEmpty()
            val targetPkg = context.packageName
            val targetClass = AppShieldAccessibilityService::class.java.name
            val isBound = enabledServices.any {
                val serviceInfo = it.resolveInfo?.serviceInfo
                serviceInfo?.packageName == targetPkg && (serviceInfo.name == targetClass || serviceInfo.name.endsWith(".AppShieldAccessibilityService"))
            }
            if (isBound) return true
        } catch (_: Exception) {}

        // 3. Fallback: Parse chuỗi ENABLED_ACCESSIBILITY_SERVICES với ComponentName unflattenFromString
        try {
            val enabledServicesStr = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()

            val expectedComponent = ComponentName(context, AppShieldAccessibilityService::class.java)
            val services = enabledServicesStr.split(':')
                .filter { it.isNotBlank() }
                .mapNotNull { ComponentName.unflattenFromString(it.trim()) }

            return services.any {
                it.packageName == expectedComponent.packageName && it.className == expectedComponent.className
            }
        } catch (_: Exception) {
            return false
        }
    }
}
