package com.pin.batteryguard.domain.model

/**
 * Cấu hình chế độ bảo vệ ứng dụng (Ẩn Developer Options / ADB / Accessibility / Overlay).
 */
data class AppShieldConfig(
    val isEnabled: Boolean = true,
    val autoDetectBanks: Boolean = true, // Tự động phát hiện ngân hàng/ví điện tử độc lập với danh sách chọn thêm
    val autoRevertMinutes: Int = 10, // Mặc định 10 phút theo yêu cầu
    val revertOnScreenOff: Boolean = true,
    val hideDevOptions: Boolean = true,
    val hideAdb: Boolean = true,
    val hideWirelessAdb: Boolean = true,
    val hideAccessibility: Boolean = false,
    val hideOverlay: Boolean = false,
    val relaunchApp: Boolean = true,
    val autoRestartShizuku: Boolean = true
)
