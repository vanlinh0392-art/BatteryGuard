package com.pin.batteryguard.ui.screen.extended

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pin.batteryguard.automation.SystemActionBridge
import com.pin.batteryguard.data.settings.SystemSettingsManager
import com.pin.batteryguard.data.settings.SystemSettingsManager.PowerKeyAction
import com.pin.batteryguard.shizuku.ShizukuManager
import com.pin.batteryguard.shizuku.ShizukuStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class SettingSection {
    POWER_GEMINI,
    SCREEN_TRANSLATE,
    DISPLAY,
    PERFORMANCE,
    NETWORK_DNS
}

data class ExtendedSettingsUiState(
    val isLoading: Boolean = false,
    val isShizukuReady: Boolean = false,
    val screenTimeoutMs: Int = 60000,
    val refreshRate: Int = 120,
    val animationScale: Float = 1.0f,
    val isOledDarkMode: Boolean = false,
    val isFastCharging: Boolean = true,
    val isMobileDataAlwaysOn: Boolean = true,
    val privateDnsMode: String = "off",
    val privateDnsHost: String = "",
    val isMasterSync: Boolean = true,
    val ramExtensionStatus: String = "",
    val powerKeyAction: PowerKeyAction = PowerKeyAction.XIAO_AI,
    val isGeminiInstalled: Boolean = false,
    val isMiCTSInstalled: Boolean = false,
    val isAutoSentinelEnabled: Boolean = true,
    val isHomeLongPressSearchEnabled: Boolean = false,
    val isAssistContextEnabled: Boolean = false,
    val message: String? = null,
    val expandedSection: SettingSection? = null
)

@HiltViewModel
class ExtendedSettingsViewModel @Inject constructor(
    application: Application,
    private val shizukuManager: ShizukuManager
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ExtendedSettingsUiState())
    val uiState: StateFlow<ExtendedSettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val isReady = shizukuManager.isReady()
                val timeout = SystemSettingsManager.getScreenTimeout(context)
                val rate = SystemSettingsManager.getRefreshRate(context)
                val anim = SystemSettingsManager.getAnimationScale(context)
                val dark = SystemActionBridge.isSystemDarkMode(context)
                val fastCharge = SystemActionBridge.isFastChargingEnabled(context)
                val mobileAlways = SystemSettingsManager.isMobileDataAlwaysOn(context)
                val dnsMode = SystemSettingsManager.getPrivateDnsMode(context)
                val dnsHost = SystemSettingsManager.getPrivateDnsSpecifier(context)
                val masterSync = SystemSettingsManager.isMasterSyncEnabled()
                val ramStatus = SystemSettingsManager.getRamExtensionStatus(context)
                val powerKey = SystemSettingsManager.getPowerKeyAction(context)
                val geminiInstalled = SystemSettingsManager.isGeminiInstalled(context)
                val mictsInstalled = SystemSettingsManager.isMiCTSInstalled(context)
                val autoSentinel = SystemSettingsManager.isAutoSentinelEnabled(context)
                val homeLongPress = SystemSettingsManager.isHomeLongPressSearchEnabled(context)

                _uiState.update {
                    it.copy(
                        isShizukuReady = isReady,
                        screenTimeoutMs = timeout,
                        refreshRate = rate,
                        animationScale = anim,
                        isOledDarkMode = dark,
                        isFastCharging = fastCharge,
                        isMobileDataAlwaysOn = mobileAlways,
                        privateDnsMode = dnsMode,
                        privateDnsHost = dnsHost,
                        isMasterSync = masterSync,
                        ramExtensionStatus = ramStatus,
                        powerKeyAction = powerKey,
                        isGeminiInstalled = geminiInstalled,
                        isMiCTSInstalled = mictsInstalled,
                        isAutoSentinelEnabled = autoSentinel,
                        isHomeLongPressSearchEnabled = homeLongPress
                    )
                }
            }
        }
    }

    fun setScreenTimeout(timeoutMs: Int) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update { it.copy(screenTimeoutMs = timeoutMs) }
            val ok = SystemSettingsManager.setScreenTimeout(context, timeoutMs)
            val label = when (timeoutMs) {
                15000 -> "15 giây"
                30000 -> "30 giây"
                60000 -> "1 phút"
                120000 -> "2 phút"
                300000 -> "5 phút"
                600000 -> "10 phút"
                1800000 -> "30 phút"
                Int.MAX_VALUE -> "Không bao giờ"
                else -> "${timeoutMs / 1000}s"
            }
            if (ok) {
                _uiState.update { it.copy(message = "Đã chỉnh khóa màn hình: $label") }
            } else {
                _uiState.update { it.copy(message = "Không thể lưu cài đặt khóa màn hình!") }
                loadSettings()
            }
        }
    }

    fun setRefreshRate(rate: Int) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update { it.copy(refreshRate = rate) }
            val ok = SystemSettingsManager.setRefreshRate(context, rate)
            if (ok) {
                _uiState.update { it.copy(message = "Đã chuyển tần số quét: ${rate}Hz") }
            } else {
                _uiState.update { it.copy(message = "Lỗi khi chuyển tần số quét!") }
                loadSettings()
            }
        }
    }

    fun setAnimationScale(scale: Float) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update { it.copy(animationScale = scale) }
            val ok = SystemSettingsManager.setAnimationScale(context, scale)
            val label = when (scale) {
                0.0f -> "Tắt (0x)"
                0.5f -> "Siêu nhanh (0.5x)"
                else -> "Mặc định (1x)"
            }
            if (ok) {
                _uiState.update { it.copy(message = "Đã chỉnh hiệu ứng hoạt họa: $label") }
            } else {
                _uiState.update { it.copy(message = "Lỗi khi đổi tốc độ hiệu ứng!") }
                loadSettings()
            }
        }
    }

    fun setOledDarkMode(enable: Boolean) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update { it.copy(isOledDarkMode = enable) }
            SystemActionBridge.setSystemDarkMode(context, enable)
            _uiState.update { it.copy(message = if (enable) "Đã bật Dark Mode OLED" else "Đã tắt Dark Mode") }
        }
    }

    fun setFastCharging(enable: Boolean) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update { it.copy(isFastCharging = enable) }
            SystemActionBridge.setFastChargingEnabled(context, enable)
            _uiState.update { it.copy(message = if (enable) "Đã bật Sạc nhanh 90W" else "Đã tắt Sạc nhanh 90W (Bảo vệ pin)") }
        }
    }

    fun triggerDeepDoze() {
        viewModelScope.launch {
            SystemActionBridge.triggerDeepDoze()
            _uiState.update { it.copy(message = "⚡ Đã kích hoạt Deep Doze hệ thống!") }
        }
    }

    fun setMobileDataAlwaysOn(enable: Boolean) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update { it.copy(isMobileDataAlwaysOn = enable) }
            val ok = SystemSettingsManager.setMobileDataAlwaysOn(context, enable)
            if (ok) {
                _uiState.update {
                    it.copy(message = if (enable) "Đã bật Giữ mạng di động khi có Wi-Fi" else "Đã tắt Giữ mạng di động (Tiết kiệm pin chờ)") }
            } else {
                _uiState.update { it.copy(message = "Không thể thay đổi cài đặt mạng!") }
                loadSettings()
            }
        }
    }

    fun setPrivateDns(mode: String, host: String = "") {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val ok = SystemSettingsManager.setPrivateDns(context, mode, host)
            if (ok) {
                _uiState.update {
                    it.copy(
                        privateDnsMode = mode,
                        privateDnsHost = host,
                        message = when (mode) {
                            "hostname" -> "Đã cấu hình Private DNS: $host"
                            "opportunistic" -> "Đã bật Private DNS Tự động"
                            else -> "Đã tắt Private DNS"
                        }
                    )
                }
            } else {
                _uiState.update { it.copy(message = "Lỗi thiết lập Private DNS (Kiểm tra lại hostname)!") }
            }
        }
    }

    fun setMasterSync(enable: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isMasterSync = enable) }
            val ok = SystemSettingsManager.setMasterSyncEnabled(enable)
            if (ok) {
                _uiState.update { it.copy(message = if (enable) "Đã bật Đồng bộ tự động" else "Đã tắt Đồng bộ tự động") }
            }
        }
    }

    fun setPowerKeyAction(action: PowerKeyAction) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update { it.copy(powerKeyAction = action) }
            val ok = SystemSettingsManager.setPowerKeyAction(context, action)
            if (ok) {
                val msg = when (action) {
                    PowerKeyAction.GEMINI_ASSISTANT -> "⚡ Đã chuyển phím Nguồn sang Google Gemini!"
                    PowerKeyAction.XIAO_AI -> "🇨🇳 Đã khôi phục phím Nguồn về XiaoAI gốc!"
                    PowerKeyAction.POWER_MENU -> "📴 Đã chuyển phím Nguồn về Menu nguồn truyền thống!"
                }
                _uiState.update { it.copy(message = msg) }
            } else {
                _uiState.update { it.copy(message = "Lỗi khi đổi hành vi phím Nguồn! Cần cấp quyền Shizuku.") }
                loadSettings()
            }
        }
    }

    fun launchGeminiTest() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val ok = SystemSettingsManager.launchGeminiTest(context)
            if (!ok) {
                _uiState.update { it.copy(message = "Không thể khởi chạy Gemini. Vui lòng kiểm tra đã cài đặt ứng dụng!") }
            }
        }
    }

    fun openAssistSettings() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val ok = SystemSettingsManager.openManageAssistSettings(context)
            if (!ok) {
                _uiState.update { it.copy(message = "Không thể mở trang cài đặt Trợ lý hệ thống!") }
            }
        }
    }

    fun setAutoSentinel(enable: Boolean) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update { it.copy(isAutoSentinelEnabled = enable) }
            SystemSettingsManager.setAutoSentinelEnabled(context, enable)
            val msg = if (enable) {
                "🛡️ Đã bật Auto-Sentinel: Tự động duy trì và bảo vệ Gemini!"
            } else {
                "Đã tắt Auto-Sentinel"
            }
            _uiState.update { it.copy(message = msg) }
        }
    }

    fun setHomeLongPressSearch(enable: Boolean) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update { it.copy(isHomeLongPressSearchEnabled = enable) }
            val ok = SystemSettingsManager.setHomeLongPressSearch(context, enable)
            if (ok) {
                val msg = if (enable) {
                    "🔍 Đã bật Circle to Search & Dịch màn hình khi đè giữ thanh Home!"
                } else {
                    "Đã tắt tìm kiếm thanh Home"
                }
                _uiState.update { it.copy(message = msg) }
            } else {
                _uiState.update { it.copy(message = "Lỗi khi cấu hình thanh Home! Cần quyền Shizuku.") }
                loadSettings()
            }
        }
    }

    fun launchScreenSearchTest() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val ok = SystemSettingsManager.launchScreenSearchTest(context)
            if (!ok) {
                _uiState.update { it.copy(message = "Không thể kích hoạt tìm kiếm màn hình. Vui lòng kiểm tra Google App!") }
            }
        }
    }

    fun launchLensTranslateDirect() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val ok = SystemSettingsManager.launchLensTranslateDirect(context)
            if (!ok) {
                _uiState.update { it.copy(message = "Không thể mở Google Lens. Vui lòng kiểm tra ứng dụng Google/Lens!") }
            }
        }
    }

    fun launchMiCTS() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val ok = SystemSettingsManager.launchMiCTS(context)
            if (ok) {
                _uiState.update { it.copy(message = "🔍 Đang mở MiCTS (Circle to Search)...") }
            } else {
                _uiState.update { it.copy(message = "Không thể khởi chạy MiCTS. Vui lòng kiểm tra đã cài đặt com.parallelc.micts!") }
            }
        }
    }

    fun restoreDefaults() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val ok = SystemSettingsManager.restoreInitialState(context)
            loadSettings()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    message = if (ok) "Đã khôi phục toàn bộ cấu hình ban đầu!" else "Không có cấu hình snapshot trước đó!"
                )
            }
        }
    }

    fun toggleSection(section: SettingSection) {
        _uiState.update {
            it.copy(expandedSection = if (it.expandedSection == section) null else section)
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
