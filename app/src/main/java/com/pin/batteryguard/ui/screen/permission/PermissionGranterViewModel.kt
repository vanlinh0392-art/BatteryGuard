package com.pin.batteryguard.ui.screen.permission

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pin.batteryguard.permission.UniversalPermissionManager
import com.pin.batteryguard.permission.model.AdvancedPermission
import com.pin.batteryguard.util.PackageHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class AppFilterCategory {
    AUTOMATION, // Tasker, MacroDroid, AutoApps, Automate,...
    CHAT_AND_BANKING, // Zalo, Messenger, Telegram, Ngân hàng, Ví...
    USER_INSTALLED,
    ALL
}

data class TargetAppInfo(
    val packageName: String,
    val appName: String,
    val isAutomationApp: Boolean,
    val isChatOrBankApp: Boolean = false,
    val isSystem: Boolean,
    val isSelfApp: Boolean = false,
    val icon: Drawable? = null
)

data class PermissionEntryUi(
    val permission: AdvancedPermission,
    val isGranted: Boolean,
    val isDeclared: Boolean,
    val isProcessing: Boolean = false
)

data class PermissionGranterUiState(
    val allApps: List<TargetAppInfo> = emptyList(),
    val filteredApps: List<TargetAppInfo> = emptyList(),
    val selectedApp: TargetAppInfo? = null,
    val permissions: List<PermissionEntryUi> = emptyList(),
    val searchQuery: String = "",
    val activeCategory: AppFilterCategory = AppFilterCategory.AUTOMATION,
    val isLoading: Boolean = true,
    val isBatchProcessing: Boolean = false,
    val statusFeedback: String? = null,
    val showSelfGrantDialog: Boolean = false,
    val pendingSelfGrantBatch: Boolean = false,
    val pendingSelfPermission: AdvancedPermission? = null,
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
        // Danh sách các package tự động hóa phổ biến
        val KNOWN_AUTOMATION_PACKAGES = setOf(
            "net.dinglisch.android.taskerm",         // Tasker
            "com.joaomgcd.taskersettings",           // Tasker Settings
            "com.joaomgcd.autoinput",                // AutoInput
            "com.joaomgcd.autonotification",         // AutoNotification
            "com.joaomgcd.autotools",                // AutoTools
            "com.joaomgcd.autowear",                 // AutoWear
            "com.joaomgcd.autoshare",                // AutoShare
            "com.joaomgcd.join",                     // Join by joaomgcd
            "com.arlosoft.macrodroid",               // MacroDroid
            "com.llamalab.automate",                 // Automate
            "ch.gridvision.ppam.androidautomator",   // AutomateIt
            "com.kieronquinn.app.darq",               // DarQ
            "com.catchingnow.icebox",                // IceBox
            "com.aistra.hail",                       // Hail
            "samhaik.shizukushare",                  // Shizuku Runner
            "rikka.shizuku",                         // Shizuku
            "com.pin.batteryguard"                   // BatteryGuard
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
                    isSelfApp = isSelf,
                    icon = PackageHelper.getAppIcon(context, pkg)
                )
            }.sortedWith(compareByDescending<TargetAppInfo> { it.isAutomationApp }
                .thenByDescending { it.isChatOrBankApp }
                .thenBy { it.appName.lowercase() })

            _uiState.update { state ->
                state.copy(
                    allApps = appList,
                    isLoading = false
                )
            }

            filterApps()

            // Mặc định chọn app đầu tiên (ưu tiên Tasker nếu có)
            val defaultSelection = appList.firstOrNull { it.isAutomationApp } ?: appList.firstOrNull()
            if (defaultSelection != null && _uiState.value.selectedApp == null) {
                selectApp(defaultSelection)
            }
        }
    }

    fun selectCategory(category: AppFilterCategory) {
        _uiState.update { it.copy(activeCategory = category) }
        filterApps()
    }

    fun updateSearchQuery(query: String) {
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
            val hasSnap = xiaomiFixManager.hasSnapshot(app.packageName)
            _uiState.update { it.copy(selectedAppHasXiaomiSnapshot = hasSnap) }
        }
        refreshPermissionsForSelectedApp()
    }

    fun refreshPermissionsForSelectedApp() {
        val target = _uiState.value.selectedApp ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val list = AdvancedPermission.PRESET_PERMISSIONS.map { perm ->
                val isDeclared = permissionManager.isPermissionDeclared(target.packageName, perm.permissionName)
                val isGranted = permissionManager.checkPermissionStatus(target.packageName, perm)
                PermissionEntryUi(
                    permission = perm,
                    isGranted = isGranted,
                    isDeclared = isDeclared,
                    isProcessing = false
                )
            }
            _uiState.update { it.copy(permissions = list) }
        }
    }

    fun requestTogglePermission(permission: AdvancedPermission, enable: Boolean) {
        val target = _uiState.value.selectedApp ?: return
        if (target.isSelfApp && enable) {
            _uiState.update {
                it.copy(
                    showSelfGrantDialog = true,
                    pendingSelfGrantBatch = false,
                    pendingSelfPermission = permission,
                    pendingSelfEnable = true
                )
            }
        } else {
            executeTogglePermission(permission, enable)
        }
    }

    fun requestGrantAllPermissions() {
        val target = _uiState.value.selectedApp ?: return
        if (target.isSelfApp) {
            _uiState.update {
                it.copy(
                    showSelfGrantDialog = true,
                    pendingSelfGrantBatch = true,
                    pendingSelfPermission = null,
                    pendingSelfEnable = null
                )
            }
        } else {
            executeGrantAllPermissions()
        }
    }

    fun confirmSelfGrant() {
        val isBatch = _uiState.value.pendingSelfGrantBatch
        val perm = _uiState.value.pendingSelfPermission
        val enable = _uiState.value.pendingSelfEnable ?: true
        _uiState.update {
            it.copy(
                showSelfGrantDialog = false,
                pendingSelfGrantBatch = false,
                pendingSelfPermission = null,
                pendingSelfEnable = null
            )
        }

        if (isBatch) {
            executeGrantAllPermissions()
        } else if (perm != null) {
            executeTogglePermission(perm, enable)
        }
    }

    fun dismissSelfGrantDialog() {
        _uiState.update {
            it.copy(
                showSelfGrantDialog = false,
                pendingSelfGrantBatch = false,
                pendingSelfPermission = null,
                pendingSelfEnable = null
            )
        }
    }

    private fun executeTogglePermission(permission: AdvancedPermission, enable: Boolean) {
        val target = _uiState.value.selectedApp ?: return
        viewModelScope.launch {
            // Set processing state
            _uiState.update { state ->
                state.copy(
                    permissions = state.permissions.map {
                        if (it.permission.id == permission.id) it.copy(isProcessing = true) else it
                    }
                )
            }

            val (success, message) = if (enable) {
                permissionManager.grantPermission(target.packageName, permission)
            } else {
                permissionManager.revokePermission(target.packageName, permission)
            }

            refreshPermissionsForSelectedApp()

            val toastMsg = if (success) {
                if (enable) "✅ Đã cấp quyền: ${permission.title}" else "⚠️ Đã thu hồi quyền: ${permission.title}"
            } else {
                "❌ Thất bại: $message"
            }

            _uiState.update { it.copy(statusFeedback = toastMsg) }
        }
    }

    private fun executeGrantAllPermissions() {
        val target = _uiState.value.selectedApp ?: return
        viewModelScope.launch {
            val eligible = _uiState.value.permissions.filter {
                !it.isGranted && (it.isDeclared || it.permission.type == com.pin.batteryguard.permission.model.PermissionType.APP_OPS)
            }.map { it.permission }

            if (eligible.isEmpty()) {
                val allCount = _uiState.value.permissions.size
                val grantedCount = _uiState.value.permissions.count { it.isGranted }
                _uiState.update {
                    it.copy(
                        statusFeedback = "ℹ️ Đã cấp $grantedCount/$allCount quyền. Các quyền còn lại chưa khai báo trong Manifest."
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(isBatchProcessing = true, statusFeedback = "⏳ Đang cấp ${eligible.size} quyền hợp lệ cho ${target.appName}...") }

            val results = permissionManager.grantAllPermissions(target.packageName, eligible)
            val successCount = results.values.count { it }

            refreshPermissionsForSelectedApp()

            _uiState.update {
                it.copy(
                    isBatchProcessing = false,
                    statusFeedback = "✅ Hoàn tất: Đã cấp $successCount / ${eligible.size} quyền cho ${target.appName}!"
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
