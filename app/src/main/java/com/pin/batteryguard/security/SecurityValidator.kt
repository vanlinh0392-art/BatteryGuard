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
    private val FORBIDDEN_SHELL_CHARS_REGEX = Regex("[;&|`$\\n\\r<>\"'\\\\]")

    /**
     * Giới hạn nhiệt độ tối đa an toàn (Hard Fail-Safe: <= 48.0°C).
     * Tuyệt đối không cho phép ghi đè thermalservice nếu nhiệt độ vượt ngưỡng trần này.
     */
    const val MAX_SAFE_THERMAL_LIMIT: Float = 48.0f
    const val MIN_SAFE_THERMAL_LIMIT: Float = 30.0f
    const val DEFAULT_CRITICAL_THERMAL_LIMIT: Float = 48.0f

    /**
     * Danh sách các ứng dụng cốt lõi hệ thống được bảo vệ tuyệt đối không cho phép thu hồi quyền hoặc đóng băng (Critical Guard).
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

    /**
     * Danh sách các gói liên kết / telemetry / theo dõi chạy ngầm của Xiaomi được kiểm chứng an toàn 100% để đóng băng (Freeze Whitelist).
     */
    val XIAOMI_FREEZE_WHITELIST = setOf(
        "com.milink.service",
        "com.milink.crossdeviceservice",
        "com.xiaomi.cameratools",
        "com.xiaomi.vtcamera",
        "com.miui.analytics",
        "com.miui.daemon",
        "com.miui.systemAdSolution",
        "com.miui.bugreport",
        "com.miui.hybrid",
        "com.xiaomi.security.onetrack",
        "com.miui.yellowpage"
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

    /**
     * Kiểm tra xem một gói có nằm trong Whitelist an toàn để đóng băng trên thiết bị Xiaomi hay không.
     */
    fun isSafeXiaomiFreezePackage(packageName: String): Boolean {
        return isValidPackageName(packageName) &&
                XIAOMI_FREEZE_WHITELIST.contains(packageName) &&
                !isCriticalSystemApp(packageName)
    }

    /**
     * Xác thực gói đóng băng Xiaomi hoặc ném ngoại lệ nếu không an toàn.
     */
    fun sanitizeXiaomiFreezePackageOrThrow(packageName: String): String {
        require(isSafeXiaomiFreezePackage(packageName)) {
            "Gói không thuộc Whitelist an toàn Xiaomi hoặc là ứng dụng cốt lõi hệ thống: $packageName"
        }
        return packageName
    }

    /**
     * Kiểm tra ứng dụng bất kỳ có an toàn để đóng băng (App Freezer / Standby Bucket) hay không:
     * - Phải là package name hợp lệ
     * - Tuyệt đối không phải ứng dụng cốt lõi hệ thống
     */
    fun isSafeToFreeze(packageName: String): Boolean {
        return isValidPackageName(packageName) && !isCriticalSystemApp(packageName)
    }

    fun sanitizeFreezePackageOrThrow(packageName: String): String {
        sanitizePackageNameOrThrow(packageName)
        require(!isCriticalSystemApp(packageName)) {
            "Không được phép đóng băng ứng dụng hệ thống cốt lõi: $packageName"
        }
        return packageName
    }

    /**
     * Thẩm định điều kiện can thiệp Thermal Override:
     * Chỉ cho phép ghi đè trạng thái nhiệt khi nhiệt độ hiện tại ở dưới ngưỡng Hard Fail-Safe (<= 48.0°C).
     */
    fun isThermalOverrideAllowed(currentTemperatureCelsius: Float): Boolean {
        return currentTemperatureCelsius <= MAX_SAFE_THERMAL_LIMIT
    }

    /**
     * Chuẩn hóa và ép ngưỡng nhiệt độ bảo vệ trong khoảng an toàn cho phép [30°C - 48°C].
     */
    fun enforceSafeThermalLimit(targetLimitCelsius: Float): Float {
        return targetLimitCelsius.coerceIn(MIN_SAFE_THERMAL_LIMIT, MAX_SAFE_THERMAL_LIMIT)
    }

    /**
     * Kiểm tra mã trạng thái thermal hợp lệ theo chuẩn AOSP Temperature / ThermalStatus (0..6).
     */
    fun isValidThermalStatus(status: Int): Boolean {
        return status in 0..6
    }

    /**
     * Kiểm tra token đối số lệnh shell nhằm triệt tiêu hoàn toàn nguy cơ Command Injection.
     * Ngăn chặn các ký tự điều khiển lệnh (; & | ` $ \n \r < > " ' \).
     */
    fun isSafeShellArgument(arg: String): Boolean {
        if (arg.isBlank() || arg.length > 512) return false
        return !FORBIDDEN_SHELL_CHARS_REGEX.containsMatchIn(arg)
    }

    fun sanitizeShellArgumentOrThrow(arg: String): String {
        require(isSafeShellArgument(arg)) { "Đối số shell chứa ký tự không an toàn hoặc ký tự điều khiển: $arg" }
        return arg
    }

    /**
     * Kiểm tra toàn bộ mảng lệnh shell truyền vào ProcessBuilder / Shizuku / ADB.
     */
    fun isSafeCommandArray(cmd: Array<String>): Boolean {
        if (cmd.isEmpty()) return false
        return cmd.all { isSafeShellArgument(it) }
    }

    fun assertSafeCommandArray(cmd: Array<String>) {
        require(cmd.isNotEmpty()) { "Mảng lệnh shell không được rỗng" }
        for (token in cmd) {
            sanitizeShellArgumentOrThrow(token)
        }
    }
}
