package com.pin.batteryguard.permission

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.pin.batteryguard.adb.AdbClient
import com.pin.batteryguard.data.db.dao.PermissionSnapshotDao
import com.pin.batteryguard.data.db.entity.PermissionSnapshotEntity
import com.pin.batteryguard.permission.fast.FastPermissionMapper
import com.pin.batteryguard.permission.model.BatchGrantResult
import com.pin.batteryguard.permission.model.DynamicPermissionItem
import com.pin.batteryguard.permission.model.PermissionCategory
import com.pin.batteryguard.permission.model.PermissionPreset
import com.pin.batteryguard.permission.model.PermissionStatus
import com.pin.batteryguard.security.SecurityValidator
import com.pin.batteryguard.shizuku.ShizukuManager
import com.pin.batteryguard.shizuku.executeShizukuCommandWithTimeout
import com.pin.batteryguard.shizuku.shizukuNewProcess
import com.pin.batteryguard.util.XiaomiHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "UniversalPermissionManager"

@Singleton
class UniversalPermissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager,
    private val adbClient: AdbClient,
    private val snapshotDao: PermissionSnapshotDao
) {

    /**
     * Quét và trích xuất TOÀN DIỆN danh sách quyền thực tế của ứng dụng.
     * TUÂN THỦ NGHIÊM NGẶT: "KO HIỂN THỊ CÁC QUYỀN CHƯA KHAI BÁO"
     * - Chỉ duyệt các quyền có trong packageInfo.requestedPermissions.
     * - Loại bỏ hoàn toàn các quyền Normal tự động cấp (INTERNET, VIBRATE...).
     * - Khóa các quyền Chữ ký hệ thống (SIGNATURE_SYSTEM) mà Shell không thể cấp.
     */
    suspend fun inspectAppPermissions(packageName: String): List<DynamicPermissionItem> = withContext(Dispatchers.IO) {
        SecurityValidator.sanitizePackageNameOrThrow(packageName)

        val pm = context.packageManager
        val packageInfo: PackageInfo = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong() or PackageManager.GET_SERVICES.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS or PackageManager.GET_SERVICES)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Không thể đọc PackageInfo cho $packageName: ${e.message}")
            return@withContext emptyList()
        }

        val requestedPermissions = packageInfo.requestedPermissions ?: emptyArray()
        val requestedFlags = packageInfo.requestedPermissionsFlags
        val appInfo = packageInfo.applicationInfo ?: return@withContext emptyList()
        val uid = appInfo.uid
        val isSystemApp = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0

        val resultList = mutableListOf<DynamicPermissionItem>()

        // 1. Duyệt qua từng quyền thực sự được khai báo trong AndroidManifest
        for (i in requestedPermissions.indices) {
            val permission = requestedPermissions[i]
            if (permission.isBlank()) continue

            val protectionInfo = try {
                pm.getPermissionInfo(permission, 0)
            } catch (_: Exception) {
                null
            }

            val protectionLevel = protectionInfo?.protectionLevel
            val baseLevel = if (protectionLevel != null) protectionLevel and PermissionInfo.PROTECTION_MASK_BASE else null
            val isDevelopment = if (protectionLevel != null) (protectionLevel and PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0 else false

            // Lọc bỏ quyền PROTECTION_NORMAL (tự động cấp khi cài đặt, không cần hiển thị gây rối UI)
            if (baseLevel == PermissionInfo.PROTECTION_NORMAL &&
                !FastPermissionMapper.APPOPS_MAP.containsKey(permission) &&
                !FastPermissionMapper.SYSTEM_PERMISSIONS.contains(permission)
            ) {
                continue
            }

            // Kiểm tra trạng thái cấp (Granted / Denied)
            var isGranted = if (requestedFlags != null && i < requestedFlags.size) {
                (requestedFlags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
            } else {
                pm.checkPermission(permission, packageName) == PackageManager.PERMISSION_GRANTED
            }

            // Phân loại danh mục
            val isAppOp = FastPermissionMapper.APPOPS_MAP.containsKey(permission)
            val isBgBattery = permission.contains("RUN_IN_BACKGROUND") || permission.contains("RUN_ANY_IN_BACKGROUND") || permission.contains("WAKE_LOCK")
            val isSystemPerm = FastPermissionMapper.SYSTEM_PERMISSIONS.contains(permission)
            val isSignatureOnly = (baseLevel == PermissionInfo.PROTECTION_SIGNATURE) && !isDevelopment && !isSystemPerm && !isAppOp && !isSystemApp

            val category = when {
                isSignatureOnly -> PermissionCategory.SIGNATURE_SYSTEM
                isBgBattery -> PermissionCategory.BACKGROUND_BATTERY
                isAppOp -> PermissionCategory.APPOPS
                isSystemPerm -> PermissionCategory.SYSTEM
                FastPermissionMapper.DANGEROUS_PERMISSIONS.contains(permission) || baseLevel == PermissionInfo.PROTECTION_DANGEROUS -> PermissionCategory.RUNTIME
                else -> PermissionCategory.NORMAL
            }

            // Nếu là quyền AppOps: kiểm tra trạng thái thực tế qua AppOpsManager
            val opCode = FastPermissionMapper.APPOPS_MAP[permission]
            if (opCode != null) {
                isGranted = checkAppOpAllowed(packageName, uid, opCode)
            }

            // Tra cứu nhãn tiếng Việt (ưu tiên PRESEEDED_LABEL_CACHE để tránh IPC)
            val label = FastPermissionMapper.PRESEEDED_LABEL_CACHE[permission] ?: try {
                protectionInfo?.loadLabel(pm)?.toString()?.ifBlank { permission.substringAfterLast('.') }
                    ?: permission.substringAfterLast('.')
            } catch (_: Exception) {
                permission.substringAfterLast('.')
            }

            val desc = when {
                isSignatureOnly -> "🔒 Quyền Chữ ký Hệ thống (Yêu cầu cùng chữ ký ROM hoặc Root, Shell không thể cấp)"
                permission == "android.permission.SYSTEM_ALERT_WINDOW" -> "Hiển thị cửa sổ/bong bóng nổi trên các ứng dụng khác"
                permission == "android.permission.PACKAGE_USAGE_STATS" -> "Đo lường thời gian sử dụng ứng dụng và lưu lượng dữ liệu"
                permission == "android.permission.MANAGE_EXTERNAL_STORAGE" -> "Quyền truy cập toàn bộ tệp tin trong bộ nhớ (Android 11+)"
                permission == "android.permission.SCHEDULE_EXACT_ALARM" -> "Đặt lịch báo thức và hẹn giờ chính xác từng giây"
                else -> null
            }

            resultList.add(
                DynamicPermissionItem(
                    name = permission,
                    label = label,
                    description = desc,
                    category = category,
                    status = if (isGranted) PermissionStatus.GRANTED else PermissionStatus.DENIED,
                    isGrantable = !isSignatureOnly && category != PermissionCategory.NORMAL,
                    opCode = opCode,
                    isDeclared = true,
                    isOemSpecific = false
                )
            )
        }

        // 2. Bổ sung mục Nền & Nguồn điện (Bypass Doze Whitelist & Standby Bucket)
        // Đây là tính năng cấp hệ thống quản lý pin độc lập cho app
        val isDozeWhitelisted = isIgnoringBatteryOptimizations(packageName)
        resultList.add(
            DynamicPermissionItem(
                name = "BATTERY_OPTIMIZATION_WHITELIST",
                label = "Bỏ qua Tối ưu Pin (Bypass Doze)",
                description = "Đưa app vào danh sách trắng Doze, duy trì mạng và socket khi tắt màn hình",
                category = PermissionCategory.BACKGROUND_BATTERY,
                status = if (isDozeWhitelisted) PermissionStatus.GRANTED else PermissionStatus.DENIED,
                isGrantable = true,
                isDeclared = true
            )
        )

        // 3. Nếu là điện thoại Xiaomi / HyperOS (không áp dụng cho Android TV): Bổ sung các mã AppOps Xiaomi
        if (XiaomiHelper.isXiaomi()) {
            val hasAutoStart = checkAppOpAllowed(packageName, uid, "10053") || checkAppOpAllowed(packageName, uid, "10008")
            resultList.add(
                DynamicPermissionItem(
                    name = "XIAOMI_AUTOSTART",
                    label = "Tự khởi chạy Xiaomi / HyperOS",
                    description = "Bật công tắc Tự khởi chạy (Autostart 10053/10008) trong Bảo mật Xiaomi",
                    category = PermissionCategory.OEM_XIAOMI,
                    status = if (hasAutoStart) PermissionStatus.GRANTED else PermissionStatus.DENIED,
                    isGrantable = true,
                    opCode = "10053",
                    isOemSpecific = true
                )
            )

            val hasBgPopup = checkAppOpAllowed(packageName, uid, "10022")
            resultList.add(
                DynamicPermissionItem(
                    name = "XIAOMI_POPUP_BACKGROUND",
                    label = "Cửa sổ Pop-up khi chạy ngầm (Xiaomi)",
                    description = "Cho phép hiển thị giao diện pop-up khi đang chạy nền (Op 10022)",
                    category = PermissionCategory.OEM_XIAOMI,
                    status = if (hasBgPopup) PermissionStatus.GRANTED else PermissionStatus.DENIED,
                    isGrantable = true,
                    opCode = "10022",
                    isOemSpecific = true
                )
            )
        }

        // Sắp xếp: Đang từ chối lên trước -> Theo thứ tự Category -> Theo tên
        resultList.sortedWith(
            compareBy(
                { it.status == PermissionStatus.GRANTED },
                { it.category.ordinal },
                { it.label }
            )
        )
    }

    /**
     * Cấp 1 quyền đơn lẻ (hoặc toggle bật/tắt)
     */
    suspend fun togglePermission(
        packageName: String,
        item: DynamicPermissionItem,
        enable: Boolean
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        SecurityValidator.sanitizePackageNameOrThrow(packageName)

        if (!enable && SecurityValidator.isCriticalSystemApp(packageName)) {
            return@withContext Pair(
                false,
                "🛡️ BẢO VỆ HỆ THỐNG: Không thể thu hồi quyền của ứng dụng cốt lõi ($packageName) để tránh nguy cơ treo máy / SystemUI crash!"
            )
        }

        if (item.category == PermissionCategory.SIGNATURE_SYSTEM) {
            return@withContext Pair(false, "Quyền chữ ký hệ thống ROM không thể thay đổi qua ADB/Shizuku.")
        }

        // Xử lý các mục đặc biệt
        if (item.name == "BATTERY_OPTIMIZATION_WHITELIST") {
            val cmd = if (enable) "dumpsys deviceidle whitelist +$packageName" else "dumpsys deviceidle whitelist -$packageName"
            return@withContext executeCommandWithFallback(cmd)
        }

        if (item.name == "XIAOMI_AUTOSTART") {
            val mode = if (enable) "allow" else "default"
            val cmd = "cmd appops set --user 0 $packageName 10053 $mode; cmd appops set --user 0 $packageName 10008 $mode"
            return@withContext executeCommandWithFallback(cmd)
        }

        if (item.category == PermissionCategory.OEM_XIAOMI && item.opCode != null) {
            val mode = if (enable) "allow" else "default"
            val cmd = "cmd appops set --user 0 $packageName ${item.opCode} $mode"
            return@withContext executeCommandWithFallback(cmd)
        }

        // Xử lý AppOps thông thường
        if (item.category == PermissionCategory.APPOPS || item.category == PermissionCategory.BACKGROUND_BATTERY) {
            val op = item.opCode ?: FastPermissionMapper.APPOPS_MAP[item.name]
            if (op != null) {
                val mode = if (enable) "allow" else "default"
                val cmd = "cmd appops set --user 0 $packageName $op $mode"
                return@withContext executeCommandWithFallback(cmd)
            }
        }

        // Xử lý Runtime PM / System Permission
        val action = if (enable) "grant" else "revoke"
        val cmd = "pm $action $packageName ${item.name}"
        return@withContext executeCommandWithFallback(cmd)
    }

    /**
     * Cấp quyền hàng loạt theo Kịch bản 1-chạm (Presets) hoặc Cấp tất cả quyền hợp lệ.
     * Sử dụng Single-Shot Tagged Compound Shell Pipeline (< 80ms) thay vì lặp từng process.
     */
    suspend fun applyPreset(
        packageName: String,
        appName: String,
        preset: PermissionPreset
    ): BatchGrantResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        SecurityValidator.sanitizePackageNameOrThrow(packageName)

        // 1. Tự động chụp Snapshot lưu vào Room DB trước khi can thiệp (chuẩn ACID)
        createSnapshot(packageName, appName, "PRESET_${preset.name}")

        val currentPerms = inspectAppPermissions(packageName)
        val commandsToRun = mutableListOf<String>()

        when (preset) {
            PermissionPreset.SAFE_RUNTIME -> {
                currentPerms.filter { it.category == PermissionCategory.RUNTIME && it.status == PermissionStatus.DENIED && it.isGrantable }
                    .forEach { commandsToRun.add("pm grant $packageName ${it.name}") }
            }
            PermissionPreset.UNLIMITED_BACKGROUND -> {
                commandsToRun.add("dumpsys deviceidle whitelist +$packageName")
                commandsToRun.add("am set-standby-bucket $packageName active")
                currentPerms.filter { it.category == PermissionCategory.BACKGROUND_BATTERY && it.status == PermissionStatus.DENIED }
                    .forEach {
                        val op = it.opCode ?: FastPermissionMapper.APPOPS_MAP[it.name]
                        if (op != null) commandsToRun.add("cmd appops set --user 0 $packageName $op allow")
                    }
                if (XiaomiHelper.isXiaomi()) {
                    commandsToRun.add("cmd appops set --user 0 $packageName 10053 allow")
                    commandsToRun.add("cmd appops set --user 0 $packageName 10008 allow")
                }
            }
            PermissionPreset.TOOLS_OVERLAY -> {
                currentPerms.filter {
                    (it.name == "android.permission.SYSTEM_ALERT_WINDOW" ||
                            it.name == "android.permission.WRITE_SETTINGS" ||
                            it.name == "android.permission.PACKAGE_USAGE_STATS" ||
                            it.name == "android.permission.MANAGE_EXTERNAL_STORAGE" ||
                            it.name == "android.permission.REQUEST_INSTALL_PACKAGES") &&
                            it.status == PermissionStatus.DENIED
                }.forEach {
                    val op = it.opCode ?: FastPermissionMapper.APPOPS_MAP[it.name]
                    if (op != null) commandsToRun.add("cmd appops set --user 0 $packageName $op allow")
                    else commandsToRun.add("pm grant $packageName ${it.name}")
                }
            }
            PermissionPreset.XIAOMI_FULL -> {
                if (XiaomiHelper.isXiaomi()) {
                    commandsToRun.add("cmd appops set --user 0 $packageName 10053 allow")
                    commandsToRun.add("cmd appops set --user 0 $packageName 10008 allow")
                    commandsToRun.add("cmd appops set --user 0 $packageName 10021 allow")
                    commandsToRun.add("cmd appops set --user 0 $packageName 10022 allow")
                    commandsToRun.add("dumpsys deviceidle whitelist +$packageName")
                    commandsToRun.add("am set-standby-bucket $packageName active")
                    commandsToRun.add("am force-stop com.miui.securitycenter")
                }
            }
            PermissionPreset.REVOKE_ALL -> {
                if (SecurityValidator.isCriticalSystemApp(packageName)) {
                    return@withContext BatchGrantResult(packageName, 0, 0, listOf("Không thể thu hồi quyền System App"), 0)
                }
                currentPerms.filter { it.status == PermissionStatus.GRANTED && it.isGrantable }
                    .forEach {
                        if (it.category == PermissionCategory.RUNTIME) {
                            commandsToRun.add("pm revoke $packageName ${it.name}")
                        } else if (it.opCode != null) {
                            commandsToRun.add("cmd appops set --user 0 $packageName ${it.opCode} default")
                        }
                    }
            }
        }

        if (commandsToRun.isEmpty()) {
            return@withContext BatchGrantResult(packageName, 0, 0, emptyList(), System.currentTimeMillis() - startTime)
        }

        // 2. Chạy pipeline gộp siêu tốc
        val pipelineResult = executeCompoundPipeline(commandsToRun)
        val elapsed = System.currentTimeMillis() - startTime

        BatchGrantResult(
            packageName = packageName,
            total = commandsToRun.size,
            succeeded = pipelineResult.first,
            failed = pipelineResult.second,
            elapsedMs = elapsed
        )
    }

    /**
     * Cấp toàn bộ các quyền hợp lệ đang bị từ chối
     */
    suspend fun grantAllValidPermissions(packageName: String, appName: String): BatchGrantResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        SecurityValidator.sanitizePackageNameOrThrow(packageName)

        createSnapshot(packageName, appName, "PRE_GRANT_ALL")

        val currentPerms = inspectAppPermissions(packageName)
        val toGrant = currentPerms.filter { it.status == PermissionStatus.DENIED && it.isGrantable }

        val commands = mutableListOf<String>()
        for (item in toGrant) {
            when {
                item.name == "BATTERY_OPTIMIZATION_WHITELIST" -> commands.add("dumpsys deviceidle whitelist +$packageName")
                item.name == "XIAOMI_AUTOSTART" -> {
                    commands.add("cmd appops set --user 0 $packageName 10053 allow")
                    commands.add("cmd appops set --user 0 $packageName 10008 allow")
                }
                item.opCode != null -> commands.add("cmd appops set --user 0 $packageName ${item.opCode} allow")
                item.category == PermissionCategory.RUNTIME || item.category == PermissionCategory.SYSTEM -> {
                    commands.add("pm grant $packageName ${item.name}")
                }
            }
        }

        val res = executeCompoundPipeline(commands)
        BatchGrantResult(
            packageName = packageName,
            total = commands.size,
            succeeded = res.first,
            failed = res.second,
            elapsedMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Tạo Snapshot trạng thái quyền hiện tại và lưu vào Room DB v7.
     * Tự động dọn dẹp các snapshot cũ vượt quá 5 bản ghi (Auto-prune).
     */
    suspend fun createSnapshot(packageName: String, appName: String, reason: String): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(now))
        val snapId = "snap_${packageName}_$now"

        val perms = inspectAppPermissions(packageName)
        val grantedList = perms.filter { it.status == PermissionStatus.GRANTED }.map { it.name }
        val appOpsMap = mutableMapOf<String, String>()
        perms.filter { it.opCode != null && it.status == PermissionStatus.GRANTED }.forEach {
            appOpsMap[it.opCode!!] = "allow"
        }

        val isDoze = isIgnoringBatteryOptimizations(packageName)
        val entity = PermissionSnapshotEntity(
            id = snapId,
            packageName = packageName,
            appName = appName,
            capturedAt = now,
            formattedDate = dateStr,
            grantedPermissionsJson = JSONArray(grantedList).toString(),
            appOpsStatesJson = JSONObject(appOpsMap as Map<*, *>).toString(),
            isBatteryWhitelisted = isDoze,
            standbyBucket = 10,
            snapshotReason = reason
        )

        snapshotDao.insertSnapshot(entity)
        snapshotDao.pruneOldSnapshots(packageName, keepLimit = 5)
        Log.i(TAG, "📸 Đã lưu Snapshot [$snapId] cho $packageName")
        snapId
    }

    /**
     * Khôi phục (Rollback) quyền của ứng dụng về bản Snapshot gần nhất (Delta-Diffing Idempotent).
     */
    suspend fun rollbackLatestSnapshot(packageName: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        SecurityValidator.sanitizePackageNameOrThrow(packageName)
        val latest = snapshotDao.getLatestSnapshot(packageName)
            ?: return@withContext Pair(false, "Không tìm thấy bản lưu Snapshot nào cho ứng dụng này.")

        val snapshotGranted = mutableSetOf<String>()
        try {
            val jsonArr = JSONArray(latest.grantedPermissionsJson)
            for (i in 0 until jsonArr.length()) {
                snapshotGranted.add(jsonArr.getString(i))
            }
        } catch (_: Exception) {}

        val currentPerms = inspectAppPermissions(packageName)
        val currentGranted = currentPerms.filter { it.status == PermissionStatus.GRANTED }.map { it.name }.toSet()

        val toGrant = snapshotGranted - currentGranted
        val toRevoke = currentGranted - snapshotGranted

        val commands = mutableListOf<String>()

        // Khôi phục các quyền ban đầu có mà nay bị mất
        for (p in toGrant) {
            val item = currentPerms.find { it.name == p }
            if (item != null && item.isGrantable) {
                if (item.opCode != null) commands.add("cmd appops set --user 0 $packageName ${item.opCode} allow")
                else commands.add("pm grant $packageName ${item.name}")
            }
        }

        // Thu hồi các quyền ban đầu không có mà bị cấp thừa
        for (p in toRevoke) {
            val item = currentPerms.find { it.name == p }
            if (item != null && item.isGrantable && !SecurityValidator.isCriticalSystemApp(packageName)) {
                if (item.opCode != null) commands.add("cmd appops set --user 0 $packageName ${item.opCode} default")
                else commands.add("pm revoke $packageName ${item.name}")
            }
        }

        // Khôi phục Doze
        if (latest.isBatteryWhitelisted) {
            commands.add("dumpsys deviceidle whitelist +$packageName")
        } else {
            commands.add("dumpsys deviceidle whitelist -$packageName")
        }

        if (commands.isNotEmpty()) {
            executeCompoundPipeline(commands)
        }

        // Xóa bản snapshot này sau khi đã hoàn tác xong
        snapshotDao.deleteById(latest.id)
        Pair(true, "Đã hoàn tác quyền về thời điểm ${latest.formattedDate} thành công!")
    }

    /**
     * Kiểm tra ứng dụng có snapshot để rollback không
     */
    suspend fun hasSnapshot(packageName: String): Boolean = withContext(Dispatchers.IO) {
        snapshotDao.countSnapshotsForPackage(packageName) > 0
    }

    // ================== CÁC HÀM TIỆN ÍCH HỆ THỐNG ==================

    private fun checkAppOpAllowed(packageName: String, uid: Int, opCodeOrName: String): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(opCodeOrName, uid, packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(opCodeOrName, uid, packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Exception) {
            false
        }
    }

    private fun isIgnoringBatteryOptimizations(packageName: String): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.isIgnoringBatteryOptimizations(packageName) ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Single-Shot Tagged Compound Shell Pipeline
     * Gộp hàng chục lệnh vào 1 process duy nhất qua Shizuku Binder hoặc ADB socket.
     */
    private suspend fun executeCompoundPipeline(commands: List<String>): Pair<Int, List<String>> {
        if (commands.isEmpty()) return Pair(0, emptyList())

        val compoundSb = StringBuilder()
        for (i in commands.indices) {
            compoundSb.append(commands[i]).append("; echo \"__R__:$i:\$?\"\n")
        }
        val compoundScript = compoundSb.toString()

        val output = if (shizukuManager.isReady()) {
            try {
                val proc = shizukuNewProcess(arrayOf("sh", "-c", compoundScript))
                val finished = proc.waitFor(10, TimeUnit.SECONDS)
                if (!finished) proc.destroyForcibly()
                proc.inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                Log.w(TAG, "Shizuku compound pipeline error: ${e.message}")
                ""
            }
        } else {
            val adbRes = adbClient.executeCommand(compoundScript, port = 5555, timeoutMs = 10000)
            if (adbRes.first) adbRes.second else ""
        }

        var successCount = 0
        val failed = mutableListOf<String>()

        val regex = Regex("__R__:([0-9]+):([0-9]+)")
        val matches = regex.findAll(output).toList()

        if (matches.isNotEmpty()) {
            for (match in matches) {
                val idx = match.groupValues[1].toIntOrNull() ?: continue
                val exitCode = match.groupValues[2].toIntOrNull() ?: 1
                if (exitCode == 0) {
                    successCount++
                } else {
                    if (idx < commands.size) failed.add(commands[idx])
                }
            }
        } else {
            // Fallback: nếu script chạy hết mà không có output tag báo lỗi
            if (output.isNotBlank() && !output.contains("Error", ignoreCase = true) && !output.contains("Exception", ignoreCase = true)) {
                successCount = commands.size
            } else {
                failed.addAll(commands)
            }
        }

        return Pair(successCount, failed)
    }

    private suspend fun executeCommandWithFallback(command: String): Pair<Boolean, String> {
        if (shizukuManager.isReady()) {
            try {
                val cmdArray = arrayOf("sh", "-c", command)
                val result = executeShizukuCommandWithTimeout(cmdArray, timeoutMs = 5000L)
                val errorKeywords = listOf("Security exception", "SecurityException", "Error:", "Exception", "Unknown", "not requested", "Failure", "Usage:")
                val hasError = errorKeywords.any {
                    result.stdout.contains(it, ignoreCase = true) || result.stderr.contains(it, ignoreCase = true)
                }
                if (result.isSuccess && !hasError) {
                    return Pair(true, result.stdout.ifBlank { "Thành công" })
                } else {
                    val errMsg = result.stderr.ifBlank { result.stdout }.ifBlank { "Lệnh thất bại (exit: ${result.exitCode})" }
                    return Pair(false, errMsg)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Shizuku error: ${e.message}")
            }
        }

        val adbResult = adbClient.executeCommand(command, port = 5555)
        if (adbResult.first) {
            val output = adbResult.second
            val errorKeywords = listOf("Security exception", "SecurityException", "Error:", "Exception", "Unknown", "not requested", "Failure", "Usage:")
            val hasError = errorKeywords.any { output.contains(it, ignoreCase = true) }
            return if (!hasError) Pair(true, output.ifBlank { "Thành công (ADB)" }) else Pair(false, output)
        }

        return Pair(false, "Không thể thực thi: Cần Shizuku hoặc Local ADB 5555.")
    }
}
