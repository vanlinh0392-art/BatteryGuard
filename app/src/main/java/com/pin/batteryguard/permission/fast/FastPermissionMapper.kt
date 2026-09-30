package com.pin.batteryguard.permission.fast

import java.util.concurrent.ConcurrentHashMap

/**
 * Bảng ánh xạ AppOps chuẩn 35+ quyền (AOSP) kết hợp mã độc quyền Xiaomi/HyperOS
 * và bộ đệm Pre-seeded LABEL_CACHE dịch nhãn quyền tiếng Việt siêu tốc.
 */
object FastPermissionMapper {

    // Bảng ánh xạ AppOps chuẩn AOSP (35+ quyền)
    val APPOPS_MAP = mapOf(
        // Cửa sổ & Hệ thống
        "android.permission.SYSTEM_ALERT_WINDOW" to "SYSTEM_ALERT_WINDOW",
        "android.permission.WRITE_SETTINGS" to "WRITE_SETTINGS",
        "android.permission.PACKAGE_USAGE_STATS" to "GET_USAGE_STATS",
        "android.permission.REQUEST_INSTALL_PACKAGES" to "REQUEST_INSTALL_PACKAGES",
        "android.permission.MANAGE_EXTERNAL_STORAGE" to "MANAGE_EXTERNAL_STORAGE",
        "android.permission.SCHEDULE_EXACT_ALARM" to "SCHEDULE_EXACT_ALARM",

        // Chạy ngầm & Nguồn điện (Bypass Doze)
        "android.permission.RUN_IN_BACKGROUND" to "RUN_IN_BACKGROUND",
        "android.permission.RUN_ANY_IN_BACKGROUND" to "RUN_ANY_IN_BACKGROUND",
        "android.permission.START_FOREGROUND" to "START_FOREGROUND",
        "android.permission.WAKE_LOCK" to "WAKE_LOCK",
        "android.permission.TURN_SCREEN_ON" to "TURN_SCREEN_ON",

        // Hiển thị, Media & Picture-in-Picture
        "android.permission.PICTURE_IN_PICTURE" to "PICTURE_IN_PICTURE",
        "android.permission.MANAGE_MEDIA" to "MANAGE_MEDIA",
        "android.permission.ACCESS_RESTRICTED_SETTINGS" to "ACCESS_RESTRICTED_SETTINGS",
        "android.permission.PROJECT_MEDIA" to "PROJECT_MEDIA",
        "android.permission.TOAST_WINDOW" to "TOAST_WINDOW",

        // Quyền riêng tư & Clipboard
        "android.permission.READ_CLIPBOARD" to "READ_CLIPBOARD",
        "android.permission.WRITE_CLIPBOARD" to "WRITE_CLIPBOARD",
        "android.permission.MOCK_LOCATION" to "MOCK_LOCATION",

        // Mạng & Kết nối
        "android.permission.CHANGE_WIFI_STATE" to "CHANGE_WIFI_STATE",
        "android.permission.BLUETOOTH_ADMIN" to "BLUETOOTH_ADMIN",
        "android.permission.ACTIVATE_VPN" to "ACTIVATE_VPN",
        "android.permission.ACTIVATE_PLATFORM_VPN" to "ACTIVATE_PLATFORM_VPN",

        // Thông báo
        "android.permission.POST_NOTIFICATIONS" to "POST_NOTIFICATION"
    )

    // Mã AppOps độc quyền cho thiết bị Xiaomi / HyperOS / MIUI
    val XIAOMI_OP_CODES = mapOf(
        "XIAOMI_AUTO_START_HYPEROS" to Triple("10053", "Tự khởi chạy HyperOS (10053)", "Mã AppOps 10053 hiển thị công tắc xanh trên HyperOS 1/2/3"),
        "XIAOMI_AUTO_START" to Triple("10008", "Tự khởi chạy MIUI (10008)", "Cho phép ứng dụng tự chạy khi khởi động máy và chạy ngầm (MIUI)"),
        "XIAOMI_SHOW_WHEN_LOCKED" to Triple("10021", "Hiển thị trên màn hình khóa", "Cho phép ứng dụng hiển thị thông báo/giao diện khi màn hình đang khóa"),
        "XIAOMI_BACKGROUND_START_ACTIVITY" to Triple("10022", "Cửa sổ Pop-up khi chạy ngầm", "Cho phép mở màn hình/cửa sổ khi ứng dụng đang chạy nền"),
        "XIAOMI_POPUP_WINDOW" to Triple("10027", "Cửa sổ nổi (Pop-up Window)", "Hiển thị cửa sổ nổi trên các ứng dụng khác"),
        "XIAOMI_BACKGROUND_BLUETOOTH" to Triple("10040", "Bluetooth trong nền", "Duy trì kết nối Bluetooth liên tục khi tắt màn hình")
    )

    // System-level permissions có thể cấp qua Shell
    val SYSTEM_PERMISSIONS = setOf(
        "android.permission.WRITE_SECURE_SETTINGS",
        "android.permission.DUMP",
        "android.permission.INTERACT_ACROSS_USERS",
        "android.permission.INTERACT_ACROSS_USERS_FULL",
        "android.permission.READ_LOGS",
        "android.permission.SET_ANIMATION_SCALE",
        "android.permission.CHANGE_CONFIGURATION",
        "android.permission.BATTERY_STATS"
    )

    // Quyền Android 13+ (API 33+)
    val ANDROID_13_PLUS_PERMISSIONS = setOf(
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_AUDIO",
        "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
        "android.permission.NEARBY_WIFI_DEVICES",
        "android.permission.BLUETOOTH_SCAN",
        "android.permission.BLUETOOTH_ADVERTISE",
        "android.permission.BLUETOOTH_CONNECT",
        "android.permission.BODY_SENSORS_BACKGROUND",
        "android.permission.UWB_RANGING"
    )

    // Dangerous (runtime) permissions chuẩn AOSP
    val DANGEROUS_PERMISSIONS = setOf(
        "android.permission.READ_CALENDAR",
        "android.permission.WRITE_CALENDAR",
        "android.permission.CAMERA",
        "android.permission.READ_CONTACTS",
        "android.permission.WRITE_CONTACTS",
        "android.permission.GET_ACCOUNTS",
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.RECORD_AUDIO",
        "android.permission.READ_PHONE_STATE",
        "android.permission.READ_PHONE_NUMBERS",
        "android.permission.CALL_PHONE",
        "android.permission.READ_CALL_LOG",
        "android.permission.WRITE_CALL_LOG",
        "android.permission.ADD_VOICEMAIL",
        "android.permission.USE_SIP",
        "android.permission.PROCESS_OUTGOING_CALLS",
        "android.permission.BODY_SENSORS",
        "android.permission.BODY_SENSORS_BACKGROUND",
        "android.permission.SEND_SMS",
        "android.permission.RECEIVE_SMS",
        "android.permission.READ_SMS",
        "android.permission.RECEIVE_WAP_PUSH",
        "android.permission.RECEIVE_MMS",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.READ_MEDIA_IMAGES",
        "android.permission.READ_MEDIA_VIDEO",
        "android.permission.READ_MEDIA_AUDIO",
        "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
        "android.permission.ACTIVITY_RECOGNITION",
        "android.permission.BLUETOOTH_SCAN",
        "android.permission.BLUETOOTH_ADVERTISE",
        "android.permission.BLUETOOTH_CONNECT",
        "android.permission.NEARBY_WIFI_DEVICES",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.ACCEPT_HANDOVER",
        "android.permission.UWB_RANGING"
    )

    // Bộ nhớ đệm nhãn tiếng Việt nạp sẵn hơn 50 quyền chuẩn để tránh gọi IPC Binder loadLabel
    val PRESEEDED_LABEL_CACHE = ConcurrentHashMap<String, String>().apply {
        put("android.permission.CAMERA", "Máy ảnh (Camera)")
        put("android.permission.RECORD_AUDIO", "Ghi âm & Micro")
        put("android.permission.ACCESS_FINE_LOCATION", "Vị trí chính xác (GPS)")
        put("android.permission.ACCESS_COARSE_LOCATION", "Vị trí tương đối (Mạng/Wifi)")
        put("android.permission.ACCESS_BACKGROUND_LOCATION", "Truy cập vị trí trong nền")
        put("android.permission.POST_NOTIFICATIONS", "Gửi thông báo (Notifications)")
        put("android.permission.READ_CONTACTS", "Đọc danh bạ")
        put("android.permission.WRITE_CONTACTS", "Sửa danh bạ")
        put("android.permission.READ_CALL_LOG", "Đọc nhật ký cuộc gọi")
        put("android.permission.WRITE_CALL_LOG", "Sửa nhật ký cuộc gọi")
        put("android.permission.READ_PHONE_STATE", "Trạng thái điện thoại (IMEI/SIM)")
        put("android.permission.CALL_PHONE", "Gọi điện thoại trực tiếp")
        put("android.permission.SEND_SMS", "Gửi tin nhắn SMS")
        put("android.permission.RECEIVE_SMS", "Nhận tin nhắn SMS")
        put("android.permission.READ_SMS", "Đọc tin nhắn SMS")
        put("android.permission.READ_EXTERNAL_STORAGE", "Đọc bộ nhớ ngoài (Files)")
        put("android.permission.WRITE_EXTERNAL_STORAGE", "Ghi bộ nhớ ngoài")
        put("android.permission.READ_MEDIA_IMAGES", "Đọc tệp hình ảnh")
        put("android.permission.READ_MEDIA_VIDEO", "Đọc tệp video")
        put("android.permission.READ_MEDIA_AUDIO", "Đọc tệp âm thanh")
        put("android.permission.MANAGE_EXTERNAL_STORAGE", "Quản lý toàn bộ tệp tin (All Files)")
        put("android.permission.SYSTEM_ALERT_WINDOW", "Hiển thị đè lên màn hình (Overlay)")
        put("android.permission.WRITE_SETTINGS", "Sửa đổi cài đặt hệ thống (System Settings)")
        put("android.permission.WRITE_SECURE_SETTINGS", "Ghi cài đặt bảo mật (Secure Settings)")
        put("android.permission.PACKAGE_USAGE_STATS", "Truy cập dữ liệu sử dụng ứng dụng")
        put("android.permission.REQUEST_INSTALL_PACKAGES", "Cài đặt ứng dụng không rõ nguồn gốc")
        put("android.permission.SCHEDULE_EXACT_ALARM", "Đặt báo thức & hẹn giờ chính xác")
        put("android.permission.READ_LOGS", "Đọc nhật ký toàn hệ thống (Logcat)")
        put("android.permission.DUMP", "Truy xuất trạng thái DUMP hệ thống")
        put("android.permission.BATTERY_STATS", "Thống kê năng lượng chi tiết (Battery Stats)")
        put("android.permission.INTERACT_ACROSS_USERS", "Tương tác đa người dùng (Dual Apps)")
        put("android.permission.CHANGE_CONFIGURATION", "Thay đổi cấu hình hiển thị / Ngôn ngữ")
        put("android.permission.BLUETOOTH_SCAN", "Quét thiết bị Bluetooth lân cận")
        put("android.permission.BLUETOOTH_CONNECT", "Kết nối thiết bị Bluetooth")
        put("android.permission.NEARBY_WIFI_DEVICES", "Thiết bị Wi-Fi lân cận")
        put("android.permission.RUN_IN_BACKGROUND", "Chạy ngầm liên tục (Run In Background)")
        put("android.permission.RUN_ANY_IN_BACKGROUND", "Chạy ngầm bất kỳ lúc nào")
        put("android.permission.WAKE_LOCK", "Giữ thiết bị thức (Wake Lock)")
        put("android.permission.PROJECT_MEDIA", "Tự động cho phép quay/chụp màn hình")
    }
}
