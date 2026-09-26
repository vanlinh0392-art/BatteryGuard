package com.pin.batteryguard.permission

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.pin.batteryguard.adb.AdbClient
import com.pin.batteryguard.permission.model.AdvancedPermission
import com.pin.batteryguard.permission.model.PermissionType
import com.pin.batteryguard.shizuku.ShizukuManager
import com.pin.batteryguard.shizuku.executeShizukuCommandWithTimeout
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UniversalPermissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager,
    private val adbClient: AdbClient
) {

    /**
     * Kiểm tra xem ứng dụng mục tiêu có khai báo quyền này trong AndroidManifest không.
     */
    fun isPermissionDeclared(packageName: String, permissionName: String): Boolean {
        if (permissionName.isBlank()) return true // AppOps có thể không cần <uses-permission> rõ ràng
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            }
            packageInfo.requestedPermissions?.contains(permissionName) == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Kiểm tra trạng thái đã được cấp (Granted) hay chưa.
     */
    suspend fun checkPermissionStatus(packageName: String, permission: AdvancedPermission): Boolean = withContext(Dispatchers.IO) {
        when (permission.type) {
            PermissionType.RUNTIME_PM -> {
                context.packageManager.checkPermission(permission.permissionName, packageName) == PackageManager.PERMISSION_GRANTED
            }
            PermissionType.APP_OPS -> {
                checkAppOpStatus(packageName, permission)
            }
        }
    }

    private fun checkAppOpStatus(packageName: String, permission: AdvancedPermission): Boolean {
        val opStr = permission.appOpStr ?: permission.appOpName ?: return false
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val uid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageUid(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageUid(packageName, 0)
            }

            val candidates = listOfNotNull(
                permission.appOpStr,
                if (!opStr.startsWith("android:")) "android:${opStr.lowercase()}" else null,
                permission.appOpName
            )

            for (cand in candidates) {
                try {
                    val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        appOps.unsafeCheckOpNoThrow(cand, uid, packageName)
                    } else {
                        @Suppress("DEPRECATION")
                        appOps.checkOpNoThrow(cand, uid, packageName)
                    }
                    if (mode == AppOpsManager.MODE_ALLOWED) return true
                } catch (_: Exception) {}
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun validatePackageName(packageName: String) {
        require(packageName.matches(Regex("^[a-zA-Z0-9_.]+$"))) { "Tên package không hợp lệ: $packageName" }
    }

    /**
     * Cấp quyền nâng cao cho ứng dụng được chỉ định qua Shizuku hoặc Local ADB.
     */
    suspend fun grantPermission(packageName: String, permission: AdvancedPermission): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        validatePackageName(packageName)

        // Rào chắn 1: Pre-flight Manifest Check
        if (permission.type == PermissionType.RUNTIME_PM && !isPermissionDeclared(packageName, permission.permissionName)) {
            return@withContext Pair(
                false,
                "Ứng dụng chưa khai báo quyền '${permission.permissionName.substringAfterLast('.')}' trong AndroidManifest.xml nên hệ điều hành Android từ chối cấp."
            )
        }

        // Nếu đã được cấp rồi thì không cần chạy lại
        if (checkPermissionStatus(packageName, permission)) {
            return@withContext Pair(true, "Quyền đã được cấp từ trước")
        }

        val commands = when (permission.type) {
            PermissionType.RUNTIME_PM -> listOf("pm grant $packageName ${permission.permissionName}")
            PermissionType.APP_OPS -> {
                val list = mutableListOf("cmd appops set --user 0 $packageName ${permission.appOpName} allow")
                if (permission.id == "project_media") {
                    list.add("cmd appops set --user 0 $packageName 46 allow")
                }
                list
            }
        }

        var lastOutput = ""
        for (cmd in commands) {
            val (success, output) = executeCommandWithFallback(cmd)
            lastOutput = output
            if (!success) {
                return@withContext Pair(false, output)
            }
        }

        // Rào chắn 3: Post-flight Real-State Verification
        delay(200)
        val isNowGranted = checkPermissionStatus(packageName, permission)
        if (!isNowGranted) {
            return@withContext Pair(
                false,
                "Hệ thống không áp dụng quyền này (có thể do ROM từ chối hoặc ứng dụng không hỗ trợ): $lastOutput"
            )
        }

        Pair(true, "Đã cấp quyền thành công")
    }

    /**
     * Thu hồi quyền nâng cao của ứng dụng.
     */
    suspend fun revokePermission(packageName: String, permission: AdvancedPermission): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        validatePackageName(packageName)

        val command = when (permission.type) {
            PermissionType.RUNTIME_PM -> "pm revoke $packageName ${permission.permissionName}"
            PermissionType.APP_OPS -> "cmd appops set --user 0 $packageName ${permission.appOpName} default"
        }

        val (success, output) = executeCommandWithFallback(command)
        if (!success) {
            return@withContext Pair(false, output)
        }

        delay(200)
        val isStillGranted = checkPermissionStatus(packageName, permission)
        if (isStillGranted) {
            return@withContext Pair(false, "Không thể thu hồi quyền này (hệ thống vẫn giữ trạng thái cho phép).")
        }

        Pair(true, "Đã thu hồi quyền thành công")
    }

    /**
     * Cấp toàn bộ các quyền có thể cho ứng dụng được chỉ định.
     */
    suspend fun grantAllPermissions(packageName: String, permissions: List<AdvancedPermission>): Map<String, Boolean> = withContext(Dispatchers.IO) {
        validatePackageName(packageName)
        val results = mutableMapOf<String, Boolean>()
        for (perm in permissions) {
            val (success, _) = grantPermission(packageName, perm)
            results[perm.id] = success
        }
        results
    }

    /**
     * Thực thi lệnh shell: Ưu tiên qua Shizuku Binder -> Fallback qua Local ADB TCP Socket.
     */
    private suspend fun executeCommandWithFallback(command: String): Pair<Boolean, String> {
        // Tầng 1: Thử qua Shizuku nếu đang Ready
        if (shizukuManager.isReady()) {
            try {
                val cmdArray = command.split(" ").filter { it.isNotBlank() }.toTypedArray()
                val result = executeShizukuCommandWithTimeout(cmdArray, timeoutMs = 5000L)
                
                // Rào chắn 2: Quét lỗi text trong stdout/stderr
                val errorKeywords = listOf("Security exception", "SecurityException", "Error:", "Exception", "Unknown", "not requested", "Failure", "Usage:")
                val hasError = errorKeywords.any { 
                    result.stdout.contains(it, ignoreCase = true) || result.stderr.contains(it, ignoreCase = true) 
                }

                if (result.isSuccess && !hasError) {
                    return Pair(true, result.stdout.ifBlank { "Thành công (qua Shizuku)" })
                } else {
                    val errMsg = result.stderr.ifBlank { result.stdout }.ifBlank { "Lệnh thực thi thất bại (exitCode: ${result.exitCode})" }
                    android.util.Log.w("UniversalPermissionManager", "Shizuku cmd failed: $errMsg")
                    return Pair(false, errMsg)
                }
            } catch (e: Exception) {
                android.util.Log.w("UniversalPermissionManager", "Shizuku execution error: ${e.message}")
            }
        }

        // Tầng 2: Fallback qua Local ADB Client (localhost:5555)
        val adbResult = adbClient.executeCommand(command, port = 5555)
        if (adbResult.first) {
            val output = adbResult.second
            val errorKeywords = listOf("Security exception", "SecurityException", "Error:", "Exception", "Unknown", "not requested", "Failure", "Usage:")
            val hasError = errorKeywords.any { output.contains(it, ignoreCase = true) }
            if (!hasError) {
                return Pair(true, output.ifBlank { "Thành công (qua ADB Local)" })
            } else {
                return Pair(false, output)
            }
        }

        return Pair(false, "Không thể cấp quyền: Shizuku chưa sẵn sàng và ADB cổng 5555 không phản hồi.")
    }
}
