package com.pin.batteryguard.security

/**
 * Trình thẩm định an ninh Zero-Trust cho các thao tác cấp quyền và thực thi Shell qua Shizuku/ADB.
 * Triệt tiêu hoàn toàn nguy cơ Command Injection (tránh metacharacters) và kiểm soát các gói hệ thống cốt lõi.
 */
object SecurityValidator {

    private val PACKAGE_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
    private val PERMISSION_REGEX = Regex("^[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+$")
    private val OP_CODE_REGEX = Regex("^[a-zA-Z0-9_]{1,32}$")
    private val STANDBY_BUCKET_SET = setOf(
        "active", "working_set", "frequent", "rare", "restricted", "never",
        "10", "20", "30", "40", "45", "50"
    )

    /**
     * Danh sách các ứng dụng cốt lõi hệ thống được bảo vệ tuyệt đối không cho phép thu hồi quyền (Critical Guard).
     */
    val CRITICAL_SYSTEM_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.settings",
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.miui.home",
        "com.sec.android.app.launcher",
        "com.huawei.android.launcher",
        "com.google.android.tvlauncher",
        "com.google.android.apps.tv.launcherx",
        "com.miui.securitycenter",
        "com.miui.securityadd",
        "com.miui.powerkeeper",
        "com.xiaomi.finddevice",
        "com.xiaomi.xmsf"
    )

    fun isValidPackageName(packageName: String): Boolean {
        if (packageName.isBlank() || packageName.length > 128) return false
        return PACKAGE_REGEX.matches(packageName)
    }

    fun sanitizePackageNameOrThrow(packageName: String): String {
        require(isValidPackageName(packageName)) { "Tên gói không hợp lệ hoặc chứa ký tự nguy hiểm: $packageName" }
        return packageName
    }

    fun isValidPermissionName(permissionName: String): Boolean {
        if (permissionName.isBlank() || permissionName.length > 256) return false
        return PERMISSION_REGEX.matches(permissionName)
    }

    fun sanitizePermissionNameOrThrow(permissionName: String): String {
        require(isValidPermissionName(permissionName)) { "Tên quyền không hợp lệ: $permissionName" }
        return permissionName
    }

    fun isValidOpCode(opCode: String): Boolean {
        if (opCode.isBlank() || opCode.length > 32) return false
        return OP_CODE_REGEX.matches(opCode)
    }

    fun isValidStandbyBucket(bucket: String): Boolean {
        return STANDBY_BUCKET_SET.contains(bucket.lowercase())
    }

    fun isCriticalSystemApp(packageName: String): Boolean {
        return CRITICAL_SYSTEM_PACKAGES.contains(packageName) ||
                packageName == "android" ||
                packageName.startsWith("com.android.systemui")
    }
}
