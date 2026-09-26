package com.pin.batteryguard.permission.model

enum class PermissionType {
    RUNTIME_PM,
    APP_OPS
}

data class AdvancedPermission(
    val id: String,
    val title: String,
    val description: String,
    val permissionName: String,
    val type: PermissionType,
    val appOpName: String? = null,
    val appOpStr: String? = null
) {
    companion object {
        val PRESET_PERMISSIONS = listOf(
            AdvancedPermission(
                id = "write_secure_settings",
                title = "Ghi Cài đặt Hệ thống (Write Secure Settings)",
                description = "Cho phép ứng dụng thay đổi cài đặt hệ thống (GPS, NFC, Chế độ máy bay, ADB Wifi, v.v.)",
                permissionName = "android.permission.WRITE_SECURE_SETTINGS",
                type = PermissionType.RUNTIME_PM
            ),
            AdvancedPermission(
                id = "read_logs",
                title = "Đọc Nhật ký Toàn hệ thống (Read Logs)",
                description = "Cho phép đọc logcat của toàn bộ hệ thống để bắt sự kiện app, crash, toast thông báo",
                permissionName = "android.permission.READ_LOGS",
                type = PermissionType.RUNTIME_PM
            ),
            AdvancedPermission(
                id = "dump",
                title = "Truy xuất Dữ liệu Hệ thống (DUMP)",
                description = "Cho phép gọi dumpsys batterystats, dumpsys activity, dumpsys meminfo và xem tiến trình",
                permissionName = "android.permission.DUMP",
                type = PermissionType.RUNTIME_PM
            ),
            AdvancedPermission(
                id = "battery_stats",
                title = "Thống kê Năng lượng Chi tiết (Battery Stats)",
                description = "Cho phép đo lường chính xác lượng điện năng tiêu thụ (mAh) của từng UID",
                permissionName = "android.permission.BATTERY_STATS",
                type = PermissionType.RUNTIME_PM
            ),
            AdvancedPermission(
                id = "package_usage_stats",
                title = "Truy cập Dữ liệu Sử dụng (Usage Stats)",
                description = "Theo dõi thời gian sử dụng ứng dụng và lưu lượng dữ liệu mạng",
                permissionName = "android.permission.PACKAGE_USAGE_STATS",
                type = PermissionType.APP_OPS,
                appOpName = "GET_USAGE_STATS",
                appOpStr = "android:get_usage_stats"
            ),
            AdvancedPermission(
                id = "system_alert_window",
                title = "Hiển thị Đè lên Màn hình (Draw Over Apps)",
                description = "Cho phép vẽ giao diện nổi, bong bóng nổi trên màn hình các ứng dụng khác",
                permissionName = "android.permission.SYSTEM_ALERT_WINDOW",
                type = PermissionType.APP_OPS,
                appOpName = "SYSTEM_ALERT_WINDOW",
                appOpStr = "android:system_alert_window"
            ),
            AdvancedPermission(
                id = "project_media",
                title = "Bỏ qua Xác nhận Quay Màn hình (Project Media)",
                description = "Tự động cho phép chụp/quay màn hình mà không hiện hộp thoại xác nhận (Tasker/AutoInput)",
                permissionName = "",
                type = PermissionType.APP_OPS,
                appOpName = "PROJECT_MEDIA",
                appOpStr = "android:project_media"
            ),
            AdvancedPermission(
                id = "manage_external_storage",
                title = "Quản lý Toàn bộ Bộ nhớ (All Files Access)",
                description = "Cho phép đọc/ghi toàn bộ tệp tin trong bộ nhớ trong (Android 11+)",
                permissionName = "android.permission.MANAGE_EXTERNAL_STORAGE",
                type = PermissionType.APP_OPS,
                appOpName = "MANAGE_EXTERNAL_STORAGE",
                appOpStr = "android:manage_external_storage"
            ),
            AdvancedPermission(
                id = "interact_across_users",
                title = "Tương tác Đa Người dùng (Interact Across Users)",
                description = "Cho phép ứng dụng thao tác trên nhiều profile người dùng (User 0, Work Profile, Dual Apps)",
                permissionName = "android.permission.INTERACT_ACROSS_USERS",
                type = PermissionType.RUNTIME_PM
            ),
            AdvancedPermission(
                id = "change_configuration",
                title = "Thay đổi Cấu hình Hiển thị (Change Configuration)",
                description = "Cho phép đổi ngôn ngữ, tỉ lệ font chữ, mật độ điểm ảnh (DPI) của hệ thống",
                permissionName = "android.permission.CHANGE_CONFIGURATION",
                type = PermissionType.RUNTIME_PM
            ),
            AdvancedPermission(
                id = "set_volume_key_listener",
                title = "Lắng nghe Nhấn giữ Phím Âm lượng (Volume Key Listener)",
                description = "Bắt sự kiện nhấn giữ nút tăng/giảm âm lượng để kích hoạt tác vụ Tasker",
                permissionName = "android.permission.SET_VOLUME_KEY_LONG_PRESS_LISTENER",
                type = PermissionType.RUNTIME_PM
            )
        )
    }
}
