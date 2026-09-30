package com.pin.batteryguard.permission.model

/**
 * Phân loại danh mục quyền chuẩn mực và dễ hiểu.
 */
enum class PermissionCategory(
    val displayName: String,
    val emoji: String
) {
    RUNTIME("Quyền Truy cập (Runtime)", "📋"),
    APPOPS("Quyền Hệ thống (AppOps)", "⚙️"),
    BACKGROUND_BATTERY("Nền & Pin (Doze)", "🔋"),
    SYSTEM("Quyền Cài đặt & Hệ thống", "🔒"),
    OEM_XIAOMI("Xiaomi / HyperOS", "⚡"),
    SIGNATURE_SYSTEM("Quyền Chữ ký ROM (Khóa)", "🛑"),
    NORMAL("Quyền Tự động cấp", "✅")
}

enum class PermissionStatus {
    GRANTED,
    DENIED
}

/**
 * Thông tin chi tiết của một quyền thực tế mà ứng dụng CÓ KHAI BÁO trong AndroidManifest.
 */
data class DynamicPermissionItem(
    val name: String,
    val label: String,
    val description: String?,
    val category: PermissionCategory,
    val status: PermissionStatus,
    val isGrantable: Boolean,
    val opCode: String? = null,
    val isDeclared: Boolean = true,
    val isOemSpecific: Boolean = false,
    val isProcessing: Boolean = false
)

/**
 * Các kịch bản cấp quyền 1-chạm (1-Tap Permission Presets) cho thiết bị di động.
 */
enum class PermissionPreset(
    val title: String,
    val emoji: String,
    val subtitle: String
) {
    SAFE_RUNTIME("Full Quyền An toàn", "🛡️", "Cấp toàn bộ quyền Runtime còn thiếu (Camera, Mic, Vị trí, Thông báo...)"),
    UNLIMITED_BACKGROUND("Chạy nền Bất tử", "🚀", "Doze Whitelist + Standby ACTIVE + Quyền chạy ngầm liên tục"),
    TOOLS_OVERLAY("Quyền Tool & Mod Nổi", "⚙️", "Cửa sổ nổi + Ghi cài đặt + Quản lý tất cả tệp tin (nếu app có khai báo)"),
    XIAOMI_FULL("Tối ưu Xiaomi / HyperOS", "📱", "Tự khởi chạy 10053/10008 + Pop-up ngầm + Màn hình khóa"),
    REVOKE_ALL("Thu hồi & Hoàn tác", "⏪", "Thu hồi các quyền đã cấp và trả lại trạng thái an toàn")
}

/**
 * Kết quả thực thi cấp quyền cho 1 app
 */
data class BatchGrantResult(
    val packageName: String,
    val total: Int,
    val succeeded: Int,
    val failed: List<String>,
    val elapsedMs: Long
)
