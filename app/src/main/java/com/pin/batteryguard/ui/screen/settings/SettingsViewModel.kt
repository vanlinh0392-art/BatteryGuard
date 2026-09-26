package com.pin.batteryguard.ui.screen.settings

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pin.batteryguard.data.preferences.SettingsDataStore
import com.pin.batteryguard.data.repository.BatteryRepository
import com.pin.batteryguard.domain.model.MonitoringConfig
import com.pin.batteryguard.service.BatteryMonitorService
import com.pin.batteryguard.shizuku.ShizukuManager
import com.pin.batteryguard.shizuku.ShizukuStatus
import com.pin.batteryguard.updater.AppUpdateInfo
import com.pin.batteryguard.updater.AppUpdateManager
import com.pin.batteryguard.util.XiaomiHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val config: MonitoringConfig = MonitoringConfig(),
    val shizukuStatus: ShizukuStatus = ShizukuStatus.NOT_INSTALLED,
    val isXiaomi: Boolean = false,
    val appVersion: String = com.pin.batteryguard.BuildConfig.VERSION_NAME,
    val isCheckingUpdate: Boolean = false,
    val updateInfo: AppUpdateInfo? = null,
    val updateMessage: String? = null,
    val isDownloadingUpdate: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val shizukuManager: ShizukuManager,
    private val batteryRepository: BatteryRepository,
    private val frozenAppDao: com.pin.batteryguard.data.db.dao.FrozenAppDao,
    private val forceStopManager: com.pin.batteryguard.shizuku.ForceStopManager,
    private val appUpdateManager: AppUpdateManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(isXiaomi = XiaomiHelper.isXiaomi()))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Collect config
        viewModelScope.launch {
            settingsDataStore.configFlow.collectLatest { config ->
                _uiState.update { it.copy(config = config) }
            }
        }

        // Collect Shizuku status
        viewModelScope.launch {
            shizukuManager.status.collectLatest { status ->
                _uiState.update { it.copy(shizukuStatus = status) }
            }
        }
    }

    fun updateConfig(config: MonitoringConfig) {
        viewModelScope.launch {
            settingsDataStore.updateConfig(config)
            
            // Khởi động lại service nếu cần áp dụng chu kỳ quét mới
            if (config.isMonitoringEnabled) {
                val serviceIntent = Intent(context, BatteryMonitorService::class.java)
                context.startForegroundService(serviceIntent)
            }
        }
    }

    fun refreshShizuku() {
        viewModelScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(context, "🔄 Đang gửi lệnh khởi chạy Shizuku qua ADB...", android.widget.Toast.LENGTH_SHORT).show()
            }
            val result = shizukuManager.forceRefresh()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (result) {
                    android.widget.Toast.makeText(context, "✅ Shizuku đã kết nối và sẵn sàng!", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, "⚠️ Không thể kết nối ADB (cổng 5555). Hãy kiểm tra gỡ lỗi USB/WiFi.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            batteryRepository.cleanupOldData(0) // Xóa sạch log
        }
    }

    fun resetSetup() {
        viewModelScope.launch {
            settingsDataStore.saveSetupCompleted(false)
        }
    }

    fun clearFrozenApps() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val apps = frozenAppDao.getAll()
            for (app in apps) {
                forceStopManager.unfreezePackage(app.packageName, app.userId)
                frozenAppDao.delete(app.packageName, app.userId)
            }
        }
    }

    fun checkForUpdate(isUserInitiated: Boolean = true) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCheckingUpdate = true, updateMessage = null) }
            val result = appUpdateManager.checkUpdate()
            result.onSuccess { info ->
                _uiState.update {
                    it.copy(
                        isCheckingUpdate = false,
                        updateInfo = if (info.hasUpdate) info else null,
                        updateMessage = if (!info.hasUpdate && isUserInitiated) "Bạn đang sử dụng phiên bản mới nhất (${info.currentVersion})!" else null
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isCheckingUpdate = false,
                        updateMessage = if (isUserInitiated) "Không thể kiểm tra cập nhật: ${err.message}" else null
                    )
                }
            }
        }
    }

    fun downloadAndInstallUpdate(updateInfo: AppUpdateInfo) {
        _uiState.update { it.copy(isDownloadingUpdate = true) }
        appUpdateManager.downloadAndInstall(updateInfo) {
            _uiState.update { state -> state.copy(isDownloadingUpdate = false, updateInfo = null, updateMessage = "Bắt đầu tải bản cập nhật...") }
        }
    }

    fun dismissUpdateDialog() {
        _uiState.update { it.copy(updateInfo = null) }
    }

    fun clearUpdateMessage() {
        _uiState.update { it.copy(updateMessage = null) }
    }
}
