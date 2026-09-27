package com.pin.batteryguard.automation

import android.Manifest
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import com.pin.batteryguard.adb.AdbClient
import com.pin.batteryguard.shizuku.executeShizukuCommandWithTimeout
import com.pin.batteryguard.ui.screen.automation.RingerTargetMode
import com.pin.batteryguard.ui.screen.automation.TargetSimSelection

private const val TAG = "SystemActionBridge"

/**
 * Cầu nối thực thi hành động hệ thống thông minh (2-Tier Architecture):
 * Tier 1: Ưu tiên Android Framework Native API / Settings.Global / ContentResolver (0.1ms, Không cần ADB)
 * Tier 2: Tự động Fallback sang Shizuku Shell IPC chỉ khi Tier 1 thất bại hoặc bị chặn bởi OEM.
 */
object SystemActionBridge {

    /**
     * Kiểm tra quyền ghi Secure Settings (Global / Secure tables)
     */
    fun hasWriteSecureSettings(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Tự động kích hoạt cấp WRITE_SECURE_SETTINGS khi đủ điều kiện ADB:
     * - Cách 1: Shizuku Ready (pingBinder + permission granted).
     * - Cách 2: Local ADB socket (cổng Wireless Debugging hoặc 5555).
     * @return true nếu đã có hoặc vừa được cấp thành công quyền WRITE_SECURE_SETTINGS.
     */
    suspend fun autoGrantWriteSecureSettingsIfAdbAvailable(
        context: Context,
        adbClient: AdbClient? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (hasWriteSecureSettings(context)) return@withContext true

        val packageName = context.packageName
        val perm = Manifest.permission.WRITE_SECURE_SETTINGS

        // 1. Thử qua Shizuku nếu Shizuku đã chạy và đã cấp quyền
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                Log.i(TAG, "⚡ Phát hiện Shizuku sẵn sàng: Tự động cấp $perm qua Shizuku...")
                val res = executeShizukuCommandWithTimeout(arrayOf("pm", "grant", packageName, perm), timeoutMs = 4000L)
                if (res.isSuccess) {
                    Log.d(TAG, "✅ Đã tự động cấp $perm qua Shizuku thành công!")
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Không thể cấp qua Shizuku: ${e.message}")
        }

        if (hasWriteSecureSettings(context)) return@withContext true

        // 2. Thử qua Local ADB Socket (Wireless Debugging port hoặc port 5555)
        try {
            val client = adbClient ?: AdbClient(context.applicationContext)
            val candidatePorts = mutableListOf(5555)
            getWirelessDebugPort()?.let { if (it > 0 && it != 5555) candidatePorts.add(it) }

            for (port in candidatePorts) {
                Log.d(TAG, "Thử tự động cấp $perm qua ADB Local port $port...")
                val (success, output) = client.executeCommand("pm grant $packageName $perm", port = port, timeoutMs = 4000)
                if (success && !output.contains("SecurityException", ignoreCase = true)) {
                    Log.d(TAG, "✅ Đã tự động cấp $perm qua ADB Local port $port: $output")
                    break
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Không thể cấp qua ADB Local socket: ${e.message}")
        }

        hasWriteSecureSettings(context)
    }

    private fun getWirelessDebugPort(): Int? {
        return try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val getMethod = systemPropertiesClass.getMethod("get", String::class.java, String::class.java)
            val tlsPortStr = getMethod.invoke(null, "service.adb.tls.port", "") as String
            if (tlsPortStr.isNotBlank()) tlsPortStr.toIntOrNull()?.takeIf { it > 0 }
            else {
                val tcpPortStr = getMethod.invoke(null, "service.adb.tcp.port", "") as String
                tcpPortStr.toIntOrNull()?.takeIf { it > 0 }
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Kiểm tra quyền sửa cài đặt hệ thống (System table)
     */
    fun hasWriteSettings(context: Context): Boolean {
        return Settings.System.canWrite(context)
    }

    /**
     * Kiểm tra quyền truy cập chính sách Không làm phiền (Do Not Disturb)
     */
    fun hasNotificationPolicyAccess(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        return nm?.isNotificationPolicyAccessGranted == true
    }

    /**
     * Khung thực thi Ponytail: Ưu tiên Direct API, chỉ Fallback Shizuku khi cần
     */
    suspend fun tryDirectThenFallback(
        actionName: String,
        directAction: suspend () -> Boolean,
        fallbackAction: suspend () -> Unit
    ) {
        var directSuccess = false
        try {
            directSuccess = directAction()
        } catch (e: SecurityException) {
            Log.w(TAG, "⚡ [$actionName] Direct SecurityException: ${e.message}, chuyển Fallback Shizuku...")
        } catch (e: Exception) {
            Log.w(TAG, "⚡ [$actionName] Direct thất bại: ${e.message}, chuyển Fallback Shizuku...")
        }

        if (directSuccess) {
            Log.d(TAG, "✅ [$actionName] Thực thi thành công qua Native System API (~0ms, No-ADB)")
        } else {
            Log.i(TAG, "🔄 [$actionName] Đang kích hoạt Fallback qua Shizuku Shell...")
            try {
                fallbackAction()
                Log.d(TAG, "🛡️ [$actionName] Fallback Shizuku hoàn tất")
            } catch (e: Exception) {
                Log.e(TAG, "❌ [$actionName] Fallback Shizuku cũng thất bại: ${e.message}")
            }
        }
    }

    /**
     * 1. 📶 Tự động Bật/Tắt Dữ liệu Di động (4G/5G)
     * Lưu ý: Settings.Global.putInt("mobile_data") chỉ cập nhật DB hiển thị nhưng không ngắt packet service
     * của modem radio trên Android 10+. Do đó, hàm sẽ:
     * - Cập nhật Settings.Global để đồng bộ icon/UI nếu có quyền.
     * - Bắt buộc thực thi lệnh shell phần cứng (cmd phone data / svc data) qua Shizuku hoặc Local ADB.
     */
    suspend fun toggleMobileData(context: Context, enable: Boolean, targetSim: TargetSimSelection) {
        val flag = if (enable) 1 else 0
        val subId = SimHelper.getTargetSubId(context, targetSim)

        // 1. Cập nhật Settings.Global (đồng bộ UI icon hệ thống)
        try {
            if (hasWriteSecureSettings(context)) {
                val cr = context.contentResolver
                Settings.Global.putInt(cr, "mobile_data", flag)
                if (subId != null && subId > 0) {
                    Settings.Global.putInt(cr, "mobile_data$subId", flag)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Không thể cập nhật Settings.Global.mobile_data: ${e.message}")
        }

        // 2. Kích hoạt lệnh phần cứng qua Shizuku Shell hoặc Local ADB
        val cmds = SimHelper.buildToggleDataCommands(enable, targetSim, context)
        var executed = false

        // Tầng 1: Ưu tiên thực thi qua Shizuku Shell nếu Shizuku READY
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                for (cmd in cmds) {
                    val parts = cmd.split(" ").toTypedArray()
                    val res = executeShizukuCommandWithTimeout(parts, timeoutMs = 3000L)
                    if (res.isSuccess) {
                        Log.d(TAG, "✅ [ToggleMobileData] Đã thực thi qua Shizuku: $cmd")
                        executed = true
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi thực thi Mobile Data qua Shizuku: ${e.message}")
        }

        // Tầng 2: Fallback qua Local ADB Client (port 5555 hoặc Wireless Debugging port)
        if (!executed) {
            try {
                val client = AdbClient(context.applicationContext)
                val ports = mutableListOf(5555)
                getWirelessDebugPort()?.let { if (it > 0 && it != 5555) ports.add(it) }

                for (port in ports) {
                    for (cmd in cmds) {
                        val (success, _) = client.executeCommand(cmd, port = port, timeoutMs = 3000)
                        if (success) {
                            Log.d(TAG, "✅ [ToggleMobileData] Đã thực thi qua Local ADB port $port: $cmd")
                            executed = true
                            break
                        }
                    }
                    if (executed) break
                }
            } catch (e: Exception) {
                Log.w(TAG, "Lỗi thực thi Mobile Data qua Local ADB: ${e.message}")
            }
        }

        if (!executed) {
            Log.w(TAG, "⚠️ [ToggleMobileData] Chưa thể ngắt dữ liệu (cần Shizuku hoặc ADB kết nối)")
        }
    }

    /**
     * 2. 🔔 Chuyển Chế độ Chuông / Im lặng Ngày & Đêm
     */
    suspend fun setRingerMode(context: Context, mode: RingerTargetMode) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val targetMode = if (mode == RingerTargetMode.SILENT) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_VIBRATE
        val modeCode = if (mode == RingerTargetMode.SILENT) "0" else "1"

        tryDirectThenFallback(
            actionName = "SetRingerMode_$mode",
            directAction = {
                // Tier 1a: AudioManager native nếu có quyền DND
                if (hasNotificationPolicyAccess(context)) {
                    audioManager.ringerMode = targetMode
                    return@tryDirectThenFallback true
                }

                // Tier 1b: Ghi zen_mode qua WRITE_SECURE_SETTINGS
                if (hasWriteSecureSettings(context)) {
                    val zenValue = if (mode == RingerTargetMode.SILENT) 2 else 1
                    return@tryDirectThenFallback Settings.Global.putInt(context.contentResolver, "zen_mode", zenValue)
                }

                false
            },
            fallbackAction = {
                executeShizukuCommandWithTimeout(arrayOf("cmd", "audio", "set-ringer-mode", modeCode), 3000L)
            }
        )
    }

    suspend fun setNormalRingerMode(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        tryDirectThenFallback(
            actionName = "SetNormalRingerMode",
            directAction = {
                // Tắt Zen Mode nếu có quyền
                if (hasWriteSecureSettings(context)) {
                    Settings.Global.putInt(context.contentResolver, "zen_mode", 0)
                }

                if (hasNotificationPolicyAccess(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                    true
                } else {
                    false
                }
            },
            fallbackAction = {
                executeShizukuCommandWithTimeout(arrayOf("cmd", "audio", "set-ringer-mode", "2"), 3000L)
            }
        )
    }

    /**
     * 3. 🔋 Bảo vệ Pin Sạc Qua Đêm: Áp dụng cài đặt sạc an toàn OEM (nếu có)
     */
    fun applySmartChargingLimit(context: Context, limitPercent: Int) {
        if (!hasWriteSecureSettings(context)) return
        try {
            val cr = context.contentResolver
            // Samsung Protect Battery (80% / 85%)
            Settings.Global.putInt(cr, "protect_battery", 1)
            // Google Pixel Charging Optimization
            Settings.Secure.putInt(cr, "charging_optimization_enabled", 1)
            // Xiaomi / HyperOS Night Charging
            Settings.System.putInt(cr, "night_charge_protection", 1)
            Log.d(TAG, "Đã áp dụng giới hạn sạc OEM qua Secure Settings")
        } catch (_: Exception) {}
    }

    /**
     * 4. ⚡ Tiết kiệm Pin khi Pin Yếu
     */
    suspend fun setPowerSaveMode(context: Context, enable: Boolean) {
        val flag = if (enable) 1 else 0

        tryDirectThenFallback(
            actionName = "PowerSaver_$enable",
            directAction = {
                if (!hasWriteSecureSettings(context)) return@tryDirectThenFallback false

                val cr = context.contentResolver
                // PowerManagerService AOSP lắng nghe key "low_power"
                val r1 = Settings.Global.putInt(cr, "low_power", flag)
                Settings.Global.putInt(cr, "low_power_sticky", flag)

                // Tắt Auto-Sync nếu bật tiết kiệm pin
                if (enable) {
                    ContentResolver.setMasterSyncAutomatically(false)
                }

                r1
            },
            fallbackAction = {
                executeShizukuCommandWithTimeout(arrayOf("cmd", "power", "set-mode", flag.toString()), 3000L)
            }
        )
    }

    /**
     * Khóa Tần số quét màn hình 60Hz
     */
    suspend fun setDisplayRefreshRate60Hz(context: Context) {
        tryDirectThenFallback(
            actionName = "RefreshRate60Hz",
            directAction = {
                if (!hasWriteSecureSettings(context) && !hasWriteSettings(context)) return@tryDirectThenFallback false

                val cr = context.contentResolver
                val r1 = Settings.System.putFloat(cr, "peak_refresh_rate", 60.0f)
                val r2 = Settings.System.putFloat(cr, "min_refresh_rate", 60.0f)
                r1 || r2
            },
            fallbackAction = {
                executeShizukuCommandWithTimeout(arrayOf("settings", "put", "system", "min_refresh_rate", "60.0"), 2000L)
                executeShizukuCommandWithTimeout(arrayOf("settings", "put", "system", "peak_refresh_rate", "60.0"), 2000L)
            }
        )
    }

    /**
     * 5. 📴 Tối ưu Sâu khi Tắt Màn Hình (Deep Doze)
     */
    suspend fun triggerDeepDoze() {
        tryDirectThenFallback(
            actionName = "DeepDoze",
            directAction = {
                // Tắt Master Sync nền tức thì
                ContentResolver.setMasterSyncAutomatically(false)
                false // Trả về false để tiếp tục chạy Shizuku force-idle nếu Shizuku sẵn sàng
            },
            fallbackAction = {
                executeShizukuCommandWithTimeout(arrayOf("dumpsys", "deviceidle", "force-idle"), 3000L)
            }
        )
    }
}
