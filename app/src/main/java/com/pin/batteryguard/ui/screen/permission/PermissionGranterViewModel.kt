package com.pin.batteryguard.ui.screen.permission

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pin.batteryguard.permission.UniversalPermissionManager
import com.pin.batteryguard.permission.model.DynamicPermissionItem
import com.pin.batteryguard.permission.model.PermissionCategory
import com.pin.batteryguard.permission.model.PermissionPreset
import com.pin.batteryguard.permission.model.PermissionStatus
import com.pin.batteryguard.util.PackageHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AppFilterCategory {
    AUTOMATION,        // Tasker, MacroDroid, AutoApps, Automate,...
    CHAT_AND_BANKING,  // Zalo, Messenger, Telegram, Ngân hàng, Ví...
    USER_INSTALLED,
    ALL
}

/**
 * Metadata thông tin ứng dụng mục tiêu.
 * ZERO-BITMAP STATE: Không lưu trữ Drawable/Bitmap trong State để tiết kiệm 99.5% RAM.
 * Icon sẽ được render On-Demand trong Composable qua PackageHelper LRU cache.
 */
data class TargetAppInfo(
    val packageName: String,
    val appName: String,
    val isAutomationApp: Boolean,
    val isChatOrBankApp: Boolean = false,
    val isSystem: Boolean,
    val isSelfApp: Boolean = false
)

data class PermissionGranterUiState(
    val allApps: List<TargetAppInfo> = emptyList(),
    val filteredApps: List<TargetAppInfo> = emptyList(),
    val selectedApp: TargetAppInfo? = null,
    val permissions: List<DynamicPermissionItem> = emptyList(),
    val searchQuery: String = "",
    val activeCategory: AppFilterCategory = AppFilterCategory.AUTOMATION,
    val isLoading: Boolean = true,
    val isBatchProcessing: Boolean = false,
    val isRollingBack: Boolean = false,
    val hasSnapshotToRollback: Boolean = false,
    val statusFeedback: String? = null,
    val showSelfGrantDialog: Boolean = false,
    val pendingSelfGrantBatch: Boolean = false,
    val pendingSelfItem: DynamicPermissionItem? = null,
    val pendingSelfEnable: Boolean? = null,
    val isXiaomiDevice: Boolean = false,
    val isChinaRom: Boolean = false,
    val selectedAppHasXiaomiSnapshot: Boolean = false,
    val isXiaomiFixing: Boolean = false,
    val batchProgress: Pair<Int, Int>? = null
)

@HiltViewModel
class PermissionGranterViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionManager: UniversalPermissionManager,
    private val xiaomiFixManager: com.pin.batteryguard.permission.XiaomiNotificationFixManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionGranterUiState())
    val uiState: StateFlow<PermissionGranterUiState> = _uiState.asStateFlow()

    companion object {
        val KNOWN_AUTOMATION_PACKAGES = setOf(
            "net.dinglisch.android.taskerm",
            "com.joaomgcd.taskersettings",
            "com.joaomgcd.autoinput",
            "com.joaomgcd.autonotification",
            "com.joaomgcd.autotools",
            "com.joaomgcd.autowear",
            "com.joaomgcd.autoshare",
            "com.joaomgcd.join",
            "com.arlosoft.macrodroid",
            "com.llamalab.automate",
            "ch.gridvision.ppam.androidautomator",
            "com.kieronquinn.app.darq",
            "com.catchingnow.icebox",
            "com.aistra.hail",
            "samhaik.shizukushare",
            "rikka.shizuku",
            "com.pin.batteryguard"
        )
    }

    init {
        _uiState.update {
            it.copy(
                isXiaomiDevice = xiaomiFixManager.isXiaomiDevice(),
                isChinaRom = xiaomiFixManager.isChinaRom()
            )
        }
        loadInstalledApps()
    }

    fun loadInstalledApps() {
        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val installed = try {
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
            } catch (_: Exception) {
                emptyList<ApplicationInfo>()
            }

            val appList = installed.map { appInfo ->
                val pkg = appInfo.packageName
                val appName = PackageHelper.getAppName(context, pkg)
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isSelf = (pkg == context.packageName)
                val isAutomation = isSelf || pkg in KNOWN_AUTOMATION_PACKAGES ||
                        pkg.contains("tasker", ignoreCase = true) ||
                        pkg.contains("macro", ignoreCase = true) ||
                        pkg.contains("automate", ignoreCase = true) ||
                        pkg.contains("autotool", ignoreCase = true)
                val isChatOrBank = xiaomiFixManager.isChatOrBankApp(pkg, appName)

                TargetAppInfo(
                    packageName = pkg,
                    appName = appName,
                    isAutomationApp = isAutomation,
                    isChatOrBankApp = isChatOrBank,
                    isSystem = isSystem,
                    isSelfApp = isSelf
                )
            }.sortedWith(
                compareByDescending<TargetAppInfo> { it.isSelfApp }
                    .thenByDescending { it.isAutomationApp }
                    .thenByDescending { it.isChatOrBankApp }
                    .thenBy { it.appName.lowercase() }
            )

            val defaultApp = appList.firstOrNull { it.isSelfApp } ?: appList.firstOrNull()

            _uiState.update {
                it.copy(
                    allApps = appList,
                    isLoading = false,
                    selectedApp = defaultApp
                )
            }
            filterApps()
            defaultApp?.let { selectApp(it) }
        }
    }

    fun setCategory(category: AppFilterCategory) {
        _uiState.update { it.copy(activeCategory = category) }
        filterApps()
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        filterApps()
    }

    private fun filterApps() {
        val state = _uiState.value
        val filtered = state.allApps.filter { app ->
            val matchCategory = when (state.activeCategory) {
                AppFilterCategory.AUTOMATION -> app.isAutomationApp
                AppFilterCategory.CHAT_AND_BANKING -> app.isChatOrBankApp
                AppFilterCategory.USER_INSTALLED -> !app.isSystem
                AppFilterCategory.ALL -> true
            }
            val matchQuery = state.searchQuery.isBlank() ||
                    app.appName.contains(state.searchQuery, ignoreCase = true) ||
                    app.packageName.contains(state.searchQuery, ignoreCase = true)

            matchCategory && matchQuery
        }
        _uiState.update { it.copy(filteredApps = filtered) }
    }

    fun selectApp(app: TargetAppInfo) {
        _uiState.update { it.copy(selectedApp = app) }
        viewModelScope.launch(Dispatchers.IO) {
            val hasXiaomiSnap = xiaomiFixManager.hasSnapshot(app.packageName)
            val hasUniversalSnap = permissionManager.hasSnapshot(app.packageName)
            _uiState.update {
                it.copy(
                    selectedAppHasXiaomiSnapshot = hasXiaomiSnap,
                    hasSnapshotToRollback = hasUniversalSnap
                )
            }
        }
        refreshPermissionsForSelectedApp()
    }

    fun refreshPermissionsForSelectedApp() {
        val target = _uiState.value.selectedApp ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val list = permissionManager.inspectAppPermissions(target.packageName)
            val hasSnap = permissionManager.hasSnapshot(target.packageName)
            _uiState.update {
                it.copy(
                    permissions = list,
                    hasSnapshotToRollback = hasSnap
                )
            }
        }
    }

    fun requestTogglePermission(item: DynamicPermissionItem, enable: Boolean) {
        val target = _uiState.value.selectedApp ?: return
        if (target.isSelfApp && enable) {
            _uiState.update {
                it.copy(
                    showSelfGrantDialog = true,
                    pendingSelfGrantBatch = false,
                    pendingSelfItem = item,
                    pendingSelfEnable = true
                )
            }
        } else {
            executeTogglePermission(item, enable)
        }
    }

    fun requestGrantAllPermissions() {
        val target = _uiState.value.selectedApp ?: return
        if (target.isSelfApp) {
            _uiState.update {
                it.copy(
                    showSelfGrantDialog = true,
                    pendingSelfGrantBatch = true,
                    pendingSelfItem = null,
                    pendingSelfEnable = null
                )
            }
        } else {
            executeGrantAllPermissions()
        }
    }

    fun confirmSelfGrant() {
        val isBatch = _uiState.value.pendingSelfGrantBatch
        val item = _uiState.value.pendingSelfItem
        val enable = _uiState.value.pendingSelfEnable ?: true
        _uiState.update {
            it.copy(
                showSelfGrantDialog = false,
                pendingSelfGrantBatch = false,
                pendingSelfItem = null,
                pendingSelfEnable = null
            )
        }

        if (isBatch) {
            executeGrantAllPermissions()
        } else if (item != null) {
            executeTogglePermission(item, enable)
        }
    }

    fun dismissSelfGrantDialog() {
        _uiState.update {
            it.copy(
                showSelfGrantDialog = false,
                pendingSelfGrantBatch = false,
                pendingSelfItem = null,
                pendingSelfEnable = null
            )
        }
    }

    private fun executeTogglePermission(item: DynamicPermissionItem, enable: Boolean) {
        val target = _uiState.value.selectedApp ?: return
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    permissions = state.permissions.map {
                        if (it.name == item.name) it.copy(isProcessing = true) else it
                    }
                )
            }

            val (success, message) = permissionManager.togglePermission(target.packageName, item, enable)

            if (success) {
                // Cập nhật Optimistic tức thì để UI switch nhảy ngay lập tức không bị giật lùi
                _uiState.update { state ->
                    state.copy(
                        permissions = state.permissions.map {
                            if (it.name == item.name) it.copy(
                                status = if (enable) PermissionStatus.GRANTED else PermissionStatus.DENIED,
                                isProcessing = false
                            ) else it
                        }
                    )
                }
            }

            val toastMsg = if (success) {
                if (enable) "✅ Đã cấp: ${item.label}" else "⚠️ Đã thu hồi: ${item.label}"
            } else {
                "❌ Thất bại: $message"
            }
            _uiState.update { it.copy(statusFeedback = toastMsg) }

            // Chờ 150ms để Android SettingsProvider / AppOps commit DB rồi mới sync lại nền
            delay(150)
            refreshPermissionsForSelectedApp()
        }
    }

    private fun executeGrantAllPermissions() {
        val target = _uiState.value.selectedApp ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isBatchProcessing = true, statusFeedback = "⏳ Đang cấp toàn bộ quyền hợp lệ cho ${target.appName}...") }
            val result = permissionManager.grantAllValidPermissions(target.packageName, target.appName)
            refreshPermissionsForSelectedApp()
            _uiState.update {
                it.copy(
                    isBatchProcessing = false,
                    hasSnapshotToRollback = true,
                    statusFeedback = "✅ Hoàn tất: Cấp thành công ${result.succeeded}/${result.total} quyền trong ${result.elapsedMs}ms!"
                )
            }
        }
    }

    /**
     * Kích hoạt Kịch bản cấp quyền 1-chạm (Presets)
     */
    fun applyPreset(preset: PermissionPreset) {
        val target = _uiState.value.selectedApp ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isBatchProcessing = true,
                    statusFeedback = "⏳ Đang áp dụng kịch bản [${preset.title}] cho ${target.appName}..."
                )
            }
            val res = permissionManager.applyPreset(target.packageName, target.appName, preset)
            refreshPermissionsForSelectedApp()
            _uiState.update {
                it.copy(
                    isBatchProcessing = false,
                    hasSnapshotToRollback = true,
                    statusFeedback = "⚡ [${preset.title}]: Đã áp dụng ${res.succeeded}/${res.total} quyền (${res.elapsedMs}ms)."
                )
            }
        }
    }

    /**
     * Khôi phục (Rollback) quyền của ứng dụng về bản lưu Snapshot trước đó
     */
    fun rollbackLatestSnapshot() {
        val target = _uiState.value.selectedApp ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isRollingBack = true) }
            val (success, message) = permissionManager.rollbackLatestSnapshot(target.packageName)
            refreshPermissionsForSelectedApp()
            _uiState.update {
                it.copy(
                    isRollingBack = false,
                    statusFeedback = message
                )
            }
        }
    }

    fun fixXiaomiForSelectedApp() {
        val target = _uiState.value.selectedApp ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isXiaomiFixing = true) }
            val result = xiaomiFixManager.fixNotification(target.packageName, target.appName)
            _uiState.update {
                it.copy(
                    isXiaomiFixing = false,
                    selectedAppHasXiaomiSnapshot = true,
                    statusFeedback = result.getOrElse { err -> err.message ?: "Lỗi tối ưu thông báo" }
                )
            }
            refreshPermissionsForSelectedApp()
        }
    }

    fun restoreXiaomiForSelectedApp() {
        val target = _uiState.value.selectedApp ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isXiaomiFixing = true) }
            val result = xiaomiFixManager.restoreNotification(target.packageName)
            _uiState.update {
                it.copy(
                    isXiaomiFixing = false,
                    selectedAppHasXiaomiSnapshot = false,
                    statusFeedback = result.getOrElse { err -> err.message ?: "Lỗi khôi phục" }
                )
            }
            refreshPermissionsForSelectedApp()
        }
    }

    fun batchFixChatAndBankApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val targets = _uiState.value.allApps
                .filter { it.isChatOrBankApp && !it.isSystem }
                .map { Pair(it.packageName, it.appName) }

            if (targets.isEmpty()) {
                _uiState.update { it.copy(statusFeedback = "Không tìm thấy ứng dụng Chat/Ngân hàng nào cần sửa.") }
                return@launch
            }

            _uiState.update { it.copy(isBatchProcessing = true, batchProgress = Pair(0, targets.size)) }
            val (success, _) = xiaomiFixManager.batchFix(targets) { curr, total, _ ->
                _uiState.update { it.copy(batchProgress = Pair(curr, total)) }
            }
            _uiState.update {
                it.copy(
                    isBatchProcessing = false,
                    batchProgress = null,
                    statusFeedback = "⚡ Đã tối ưu thông báo cho $success/${targets.size} ứng dụng Chat & Ngân hàng."
                )
            }
            refreshPermissionsForSelectedApp()
        }
    }

    fun clearFeedback() {
        _uiState.update { it.copy(statusFeedback = null) }
    }
}
