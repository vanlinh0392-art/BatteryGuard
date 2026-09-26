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
    private val AUTO_START_COOLDOWN_MS = 10 * 60 * 1000L // 10 phút giữa các lần auto-start chạy ngầm

    /**
     * Tự động khởi động dịch vụ Shizuku qua ADB localhost:5555 (hoặc fallback Wireless Debug port).
     * @param notifyOnSuccess Nếu true, bắn thông báo khi khởi chạy thành công.
     * @param isManual Nếu true (người dùng bấm trực tiếp), bỏ qua cooldown và thử lại ngay.
     * @return true nếu Shizuku đã sẵn sàng sau khi khởi chạy.
     */
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

            // 0. Chuẩn bị môi trường ADB: bật development_settings_enabled, adb_enabled, adb_wifi_enabled
            prepareAdbEnvironment()

            // daemon adbd cần thời gian khởi tạo socket sau khi bật ADB
            delay(1500L)

            val starterCmd = buildShizukuStarterCommand()
            Log.d(TAG, "Starting Shizuku with robust command: $starterCmd")

            // 1. Thử cổng 5555 trước, sau đó là Wireless Debugging port (mỗi cổng thử đúng 1 lần duy nhất)
            var executed = false
            val candidatePorts = mutableListOf(5555)
            getWirelessDebugPort()?.let { if (it > 0 && it != 5555) candidatePorts.add(it) }

            for (port in candidatePorts) {
                Log.d(TAG, "Thử gửi lệnh khởi động Shizuku qua port $port...")
                val (success, output) = adbClient.executeCommand(starterCmd, port = port, timeoutMs = 8000)
                if (success) {
                    executed = true
                    Log.i(TAG, "✅ Lệnh khởi động Shizuku đã gửi thành công qua port $port. Output: $output")
                    break
                } else {
                    Log.w(TAG, "Không thể gửi lệnh qua port $port: $output")
                    // Nếu user hủy hộp thoại hoặc timeout cấp quyền RSA, dừng ngay không thử port khác
                    if (output.contains("timeout or cancelled") || adbClient.isAuthDeclinedRecently()) {
                        break
                    }
                }
            }

            // 2. Nếu gửi lệnh thành công, đợi Shizuku khởi động và kiểm tra pingBinder
            if (executed) {
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < 10000L) {
                    delay(500L)
                    try {
                        if (Shizuku.pingBinder()) {
                            Log.i(TAG, "✅ Shizuku service successfully started and binder is alive!")
                            if (notifyOnSuccess) {
                                NotificationHelper.showShizukuRestarted(context)
                            }
                            return@withContext true
                        }
                    } catch (_: Exception) {}
                }
                Log.w(TAG, "Command executed but Shizuku binder did not respond within 10s")
            }

            return@withContext false
        } finally {
            startMutex.unlock()
        }
    }

    /**
     * Tạo chuỗi lệnh shell chuẩn xác để kích hoạt Shizuku.
     * TUYỆT ĐỐI KHÔNG DÙNG "sh" trước file libshizuku.so vì đây là file nhị phân ELF!
     */
    private fun buildShizukuStarterCommand(): String {
        val directLibPath = try {
            val appInfo = context.packageManager.getApplicationInfo("moe.shizuku.privileged.api", 0)
            val directFile = File(appInfo.nativeLibraryDir, "libshizuku.so")
            if (directFile.exists()) directFile.absolutePath else null
        } catch (_: Exception) {
            null
        }

        // Chuỗi script shell đa tầng tự động fallback
        val dynamicCmd = "PKG=moe.shizuku.privileged.api; " +
                "LIB=\$(dirname \$(pm path --user 0 \$PKG 2>&1 </dev/null | sed 's|.*:||'))/lib/*/libshizuku.so; " +
                "([ -f \"\$LIB\" ] && exec \"\$LIB\") || " +
                "(sh /sdcard/Android/data/\$PKG/starter.sh) || " +
                "(CP=\$(pm path \$PKG 2>&1 | sed 's|.*:||') && [ -n \"\$CP\" ] && CLASSPATH=\$CP exec app_process /system/bin moe.shizuku.server.Starter)"

        return if (directLibPath != null) {
            "([ -f \"$directLibPath\" ] && exec \"$directLibPath\") || ($dynamicCmd)"
        } else {
            dynamicCmd
        }
    }

    /**
     * Lấy cổng Wireless Debugging hiện tại của hệ thống nếu có.
     */
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

    /**
     * Chuẩn bị môi trường hệ thống: đảm bảo Tùy chọn nhà phát triển, Gỡ lỗi USB và Gỡ lỗi không dây đều BẬT.
     */
    fun prepareAdbEnvironment() {
        try {
            if (context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED) {
                val cr = context.contentResolver
                Settings.Global.putInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)
                Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
                Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
                Log.d(TAG, "✅ Đã chuẩn bị môi trường: development_settings_enabled=1, adb_enabled=1, adb_wifi_enabled=1")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Không thể ghi Secure Settings: ${e.message}")
        }
    }
}
