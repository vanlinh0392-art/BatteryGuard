package com.pin.batteryguard.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import com.pin.batteryguard.adb.AdbClient
import com.pin.batteryguard.util.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ShizukuAutoStarter"

@Singleton
class ShizukuAutoStarter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adbClient: AdbClient
) {
    private val startMutex = Mutex()
    @Volatile private var lastStartAttemptTime = 0L
    private val AUTO_START_COOLDOWN_MS = 10 * 60 * 1000L

    suspend fun startShizukuService(
        notifyOnSuccess: Boolean = true,
        isManual: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        // Nếu Shizuku đã đang chạy, không cần làm gì
        try {
            if (Shizuku.pingBinder()) {
                Log.d(TAG, "Shizuku binder is already alive.")
                return@withContext true
            }
        } catch (_: Exception) {}

        // Kiểm tra cooldown nếu chạy tự động
        val now = System.currentTimeMillis()
        if (!isManual) {
            if (now - lastStartAttemptTime < AUTO_START_COOLDOWN_MS) {
                Log.d(TAG, "Bỏ qua auto-start Shizuku do đang trong thời gian chờ cooldown.")
                return@withContext false
            }
            if (adbClient.isAuthDeclinedRecently()) {
                Log.d(TAG, "Bỏ qua auto-start Shizuku do người dùng chưa xác nhận ADB gần đây.")
                return@withContext false
            }
        } else {
            adbClient.resetAuthCooldown()
        }

        if (!startMutex.tryLock()) {
            Log.d(TAG, "Quá trình kích hoạt Shizuku đang diễn ra, bỏ qua yêu cầu trùng lặp.")
            return@withContext false
        }

        try {
            lastStartAttemptTime = System.currentTimeMillis()

            // 0. Chuẩn bị môi trường ADB
            prepareAdbEnvironment()

            // === CÁCH 1: Chạy trực tiếp qua ProcessBuilder (giống Tasker) ===
            // Không cần ADB TCP → không hỏi "cho phép kết nối" RSA key
            val directStarted = tryStartDirect()
            if (directStarted && waitForBinder(notifyOnSuccess)) {
                return@withContext true
            }

            // === CÁCH 2: Fallback qua ADB TCP (cần auth RSA) ===
            delay(1500L) // ADB daemon cần thời gian khởi tạo socket
            val starterCmd = buildShizukuStarterCommand()
            Log.d(TAG, "Fallback: Starting Shizuku via ADB TCP: $starterCmd")

            var executed = false
            val candidatePorts = mutableListOf(5555)
            getWirelessDebugPort()?.let { if (it > 0 && it != 5555) candidatePorts.add(it) }

            for (port in candidatePorts) {
                Log.d(TAG, "Thử gửi lệnh qua port $port...")
                val (success, output) = adbClient.executeCommand(starterCmd, port = port, timeoutMs = 8000)
                if (success) {
                    executed = true
                    Log.i(TAG, "✅ Lệnh gửi thành công qua port $port. Output: $output")
                    break
                } else {
                    Log.w(TAG, "Không thể gửi lệnh qua port $port: $output")
                    if (output.contains("timeout or cancelled") || adbClient.isAuthDeclinedRecently()) break
                }
            }

            if (executed && waitForBinder(notifyOnSuccess)) {
                return@withContext true
            }

            return@withContext false
        } finally {
            startMutex.unlock()
        }
    }

    /**
     * Chạy Shizuku starter trực tiếp bằng ProcessBuilder — giống cách Tasker làm.
     * Không cần ADB TCP, không hỏi RSA key authorization.
     */
    private fun tryStartDirect(): Boolean {
        val libPath = getShizukuLibPath() ?: return false

        return try {
            Log.i(TAG, "Thử khởi động trực tiếp: $libPath")
            val process = ProcessBuilder(libPath)
                .redirectErrorStream(true)
                .start()

            val finished = process.waitFor(10, TimeUnit.SECONDS)
            val output = process.inputStream.bufferedReader().use { it.readText() }.take(500)
            val exitCode = if (finished) process.exitValue() else -1

            if (!finished) process.destroyForcibly()

            Log.i(TAG, "Direct exec result: exit=$exitCode, output=$output")
            // exit 0 = thành công, output chứa "shizuku_starter exit with 0"
            finished && exitCode == 0
        } catch (e: Exception) {
            Log.w(TAG, "Direct exec failed: ${e.message}")
            false
        }
    }

    /**
     * Chờ Shizuku binder alive sau khi gửi lệnh.
     */
    private suspend fun waitForBinder(notifyOnSuccess: Boolean): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < 10000L) {
            delay(500L)
            try {
                if (Shizuku.pingBinder()) {
                    Log.i(TAG, "✅ Shizuku binder alive!")
                    if (notifyOnSuccess) NotificationHelper.showShizukuRestarted(context)
                    return true
                }
            } catch (_: Exception) {}
        }
        Log.w(TAG, "Shizuku binder did not respond within 10s")
        return false
    }

    /**
     * Tìm đường dẫn libshizuku.so trên thiết bị.
     */
    private fun getShizukuLibPath(): String? {
        return try {
            val appInfo = context.packageManager.getApplicationInfo("moe.shizuku.privileged.api", 0)
            val directFile = File(appInfo.nativeLibraryDir, "libshizuku.so")
            if (directFile.exists()) {
                directFile.absolutePath
            } else {
                // Fallback: tìm trong thư mục lib
                val baseDir = File(appInfo.sourceDir).parentFile
                baseDir?.walkTopDown()
                    ?.firstOrNull { it.name == "libshizuku.so" }
                    ?.absolutePath
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildShizukuStarterCommand(): String {
        val directLibPath = getShizukuLibPath()

        val dynamicCmd = "PKG=moe.shizuku.privileged.api; " +
                "LIB=\$(dirname \$(pm path --user 0 \$PKG 2>&1 </dev/null | sed 's|.*:||'))/lib/*/libshizuku.so; " +
                "([ -f \"\$LIB\" ] && exec \"\$LIB\") || " +
                "(CP=\$(pm path \$PKG 2>&1 | sed 's|.*:||') && [ -n \"\$CP\" ] && CLASSPATH=\$CP exec app_process /system/bin moe.shizuku.server.Starter)"

        return if (directLibPath != null) {
            "([ -f \"$directLibPath\" ] && exec \"$directLibPath\") || ($dynamicCmd)"
        } else {
            dynamicCmd
        }
    }

    private fun getWirelessDebugPort(): Int? {
        return try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val getMethod = systemPropertiesClass.getMethod("get", String::class.java, String::class.java)

            val tlsPortStr = getMethod.invoke(null, "service.adb.tls.port", "") as String
            if (tlsPortStr.isNotBlank()) {
                val port = tlsPortStr.toIntOrNull()
                if (port != null && port > 0) return port
            }

            val tcpPortStr = getMethod.invoke(null, "service.adb.tcp.port", "") as String
            if (tcpPortStr.isNotBlank()) {
                val port = tcpPortStr.toIntOrNull()
                if (port != null && port > 0) return port
            }

            null
        } catch (e: Exception) {
            Log.d(TAG, "Cannot read system properties for ADB port: ${e.message}")
            null
        }
    }

    fun prepareAdbEnvironment() {
        try {
            if (context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED) {
                val cr = context.contentResolver
                Settings.Global.putInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)
                Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
                Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
                Log.d(TAG, "✅ Đã chuẩn bị môi trường ADB")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Không thể ghi Secure Settings: ${e.message}")
        }
    }
}
