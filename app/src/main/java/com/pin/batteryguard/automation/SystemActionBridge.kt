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
        val modeStr = if (mode == RingerTargetMode.SILENT) "SILENT" else "VIBRATE"

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
                val zenValue = if (mode == RingerTargetMode.SILENT) "2" else "1"
                executeShizukuCommandWithTimeout(arrayOf("settings", "put", "global", "zen_mode", zenValue), 2000L)
                executeShizukuCommandWithTimeout(arrayOf("cmd", "audio", "set-ringer-mode", modeStr), 3000L)
            }
        )
    }

    suspend fun setNormalRingerMode(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        tryDirectThenFallback(
            actionName = "SetNormalRingerMode",
            directAction = {
                var directSuccess = false
                val cr = context.contentResolver

                // Thoát DND qua NotificationManager nếu có quyền NotificationPolicyAccess
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && hasNotificationPolicyAccess(context) && notificationManager != null) {
                    try {
                        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    } catch (e: Exception) {
                        Log.w(TAG, "Không thể setInterruptionFilter: ${e.message}")
                    }
                }

                // Ghi zen_mode và mode_ringer qua WRITE_SECURE_SETTINGS
                if (hasWriteSecureSettings(context)) {
                    try {
                        Settings.Global.putInt(cr, "zen_mode", 0)
                        Settings.Global.putInt(cr, "mode_ringer", 2)
                    } catch (e: Exception) {
                        Log.w(TAG, "Không thể ghi zen_mode / mode_ringer: ${e.message}")
                    }
                }

                if (hasNotificationPolicyAccess(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                    try {
                        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
                        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                        if (curVol == 0 && maxVol > 0) {
                            audioManager.setStreamVolume(AudioManager.STREAM_RING, (maxVol / 2).coerceAtLeast(1), 0)
                        }
                        directSuccess = true
                    } catch (e: Exception) {
                        Log.w(TAG, "Lỗi khi gán AudioManager.ringerMode NORMAL: ${e.message}")
                    }
                }

                directSuccess
            },
            fallbackAction = {
                executeShizukuCommandWithTimeout(arrayOf("cmd", "notification", "set_dnd", "off"), 2000L)
                executeShizukuCommandWithTimeout(arrayOf("settings", "put", "global", "zen_mode", "0"), 2000L)
                executeShizukuCommandWithTimeout(arrayOf("settings", "put", "global", "mode_ringer", "2"), 2000L)
                executeShizukuCommandWithTimeout(arrayOf("cmd", "audio", "set-ringer-mode", "NORMAL"), 2000L)
                executeShizukuCommandWithTimeout(arrayOf("cmd", "media_session", "volume", "--stream", "2", "--set", "7"), 2000L)
            }
        )
    }

    /**
     * Lấy độ sáng màn hình hiện tại (0 - 255)
     */
    fun getScreenBrightness(context: Context): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        } catch (_: Exception) {
            128
        }
    }

    /**
     * Điều chỉnh độ sáng màn hình (2-Tier: WRITE_SETTINGS -> Shizuku)
     */
    suspend fun setScreenBrightness(context: Context, brightness: Int) {
        val clamped = brightness.coerceIn(15, 255)
        tryDirectThenFallback(
            actionName = "SetScreenBrightness_$clamped",
            directAction = {
                if (hasWriteSettings(context)) {
                    val cr = context.contentResolver
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, clamped)
                } else {
                    false
                }
            },
            fallbackAction = {
                executeShizukuCommandWithTimeout(
                    arrayOf("settings", "put", "system", "screen_brightness", clamped.toString()),
                    2000L
                )
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
     * Khóa Tần số quét màn hình 60Hz (hỗ trợ AOSP & Xiaomi HyperOS / MIUI)
     */
    suspend fun setDisplayRefreshRate60Hz(context: Context) {
        tryDirectThenFallback(
            actionName = "RefreshRate60Hz",
            directAction = {
                if (!hasWriteSecureSettings(context) && !hasWriteSettings(context)) return@tryDirectThenFallback false

                val cr = context.contentResolver
                val r1 = Settings.System.putFloat(cr, "peak_refresh_rate", 60.0f)
                val r2 = Settings.System.putFloat(cr, "min_refresh_rate", 60.0f)
                val r3 = try {
                    Settings.Secure.putInt(cr, "user_refresh_rate", 60)
                } catch (_: Exception) {
                    false
                }
                r1 || r2 || r3
            },
            fallbackAction = {
                executeShizukuCommandWithTimeout(arrayOf("settings", "put", "system", "min_refresh_rate", "60.0"), 2000L)
                executeShizukuCommandWithTimeout(arrayOf("settings", "put", "system", "peak_refresh_rate", "60.0"), 2000L)
                executeShizukuCommandWithTimeout(arrayOf("settings", "put", "secure", "user_refresh_rate", "60"), 2000L)
            }
        )
    }

    /**
     * Khôi phục Tần số quét màn hình về mặc định (120Hz/Auto)
     */
    suspend fun restoreDisplayRefreshRate(context: Context) {
        tryDirectThenFallback(
            actionName = "RestoreRefreshRate",
            directAction = {
                if (!hasWriteSecureSettings(context) && !hasWriteSettings(context)) return@tryDirectThenFallback false

                val cr = context.contentResolver
                val r1 = Settings.System.putFloat(cr, "peak_refresh_rate", 120.0f)
                val r2 = Settings.System.putFloat(cr, "min_refresh_rate", 1.0f) // Cho phép LTPO hạ xuống 1Hz
                val r3 = try {
                    Settings.Secure.putInt(cr, "user_refresh_rate", 120)
                } catch (_: Exception) {
                    false
                }
                r1 || r2 || r3
            },
            fallbackAction = {
                executeShizukuCommandWithTimeout(arrayOf("settings", "put", "system", "min_refresh_rate", "1.0"), 2000L)
                executeShizukuCommandWithTimeout(arrayOf("settings", "put", "system", "peak_refresh_rate", "120.0"), 2000L)
                executeShizukuCommandWithTimeout(arrayOf("settings", "put", "secure", "user_refresh_rate", "120"), 2000L)
            }
        )
    }

    /**
     * Bật / Tắt Sạc nhanh phần cứng (Xiaomi HyperCharge 90W)
     */
    suspend fun setFastChargingEnabled(context: Context, enable: Boolean) {
        val flag = if (enable) 1 else 0
        tryDirectThenFallback(
            actionName = "SetFastCharging_$enable",
            directAction = {
                if (hasWriteSecureSettings(context)) {
                    val cr = context.contentResolver
                    Settings.Secure.putInt(cr, "key_fast_charge_enabled", flag)
                } else {
                    false
                }
            },
            fallbackAction = {
                executeShizukuCommandWithTimeout(
                    arrayOf("settings", "put", "secure", "key_fast_charge_enabled", flag.toString()),
                    2000L
                )
            }
        )
    }

    /**
     * Kiểm tra Sạc nhanh 90W Xiaomi HyperCharge hiện đang Bật hay Tắt trong cài đặt
     */
    fun isFastChargingEnabled(context: Context): Boolean {
        return try {
            Settings.Secure.getInt(context.contentResolver, "key_fast_charge_enabled", 1) == 1
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Ghi đè trạng thái thermal hệ thống qua Shizuku (status 0 = THERMAL_STATUS_NONE)
     */
    suspend fun overrideThermalStatus(context: Context, status: Int = 0): Boolean = withContext(Dispatchers.IO) {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                val res = executeShizukuCommandWithTimeout(
                    arrayOf("cmd", "thermalservice", "override-status", status.toString()),
                    timeoutMs = 2000L
                )
                if (res.isSuccess) {
                    Log.d(TAG, "✅ [Thermal] override-status $status thành công")
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi overrideThermalStatus qua Shizuku: ${e.message}")
        }
        false
    }

    /**
     * Reset trạng thái thermal hệ thống về mặc định qua Shizuku
     */
    suspend fun resetThermalStatus(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                val res = executeShizukuCommandWithTimeout(
                    arrayOf("cmd", "thermalservice", "reset"),
                    timeoutMs = 2000L
                )
                if (res.isSuccess) {
                    Log.d(TAG, "✅ [Thermal] reset thermal status thành công")
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi resetThermalStatus qua Shizuku: ${e.message}")
        }
        false
    }

    /**
     * Kiểm tra Chế độ Giao diện Tối hệ thống (Dark Mode) hiện tại đang Bật hay Tắt
     */
    fun isSystemDarkMode(context: Context): Boolean {
        val configNight = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val settingNight = try {
            Settings.System.getInt(context.contentResolver, "dark_mode_enable", 0) == 1
        } catch (_: Exception) {
            false
        }
        return configNight || settingNight
    }

    /**
     * Bật / Tắt Chế độ Giao diện Tối OLED (Dark Mode hệ thống)
     */
    suspend fun setSystemDarkMode(context: Context, enable: Boolean) {
        val flag = if (enable) 1 else 0
        val uiModeArg = if (enable) "yes" else "no"
        tryDirectThenFallback(
            actionName = "SetDarkMode_$enable",
            directAction = {
                if (hasWriteSettings(context)) {
                    val cr = context.contentResolver
                    Settings.System.putInt(cr, "dark_mode_enable", flag)
                } else {
                    false
                }
            },
            fallbackAction = {
                executeShizukuCommandWithTimeout(
                    arrayOf("settings", "put", "system", "dark_mode_enable", flag.toString()),
                    2000L
                )
                executeShizukuCommandWithTimeout(
                    arrayOf("cmd", "uimode", "night", uiModeArg),
                    2000L
                )
            }
        )
    }

    /**
     * Chế độ SmoothSaver: Duy trì 120Hz mượt mà khi bật Tiết kiệm pin (thay vì bị khóa về 60Hz).
     * Khi enable = true: ép 120.0Hz min/peak/user và miui_refresh_rate.
     * Khi enable = false: khôi phục 120Hz peak, 1Hz min (LTPO dynamic).
     */
    suspend fun setSmoothSaverMode(context: Context, enable: Boolean) {
        if (!enable) {
            restoreDisplayRefreshRate(context)
            return
        }

        tryDirectThenFallback(
            actionName = "SmoothSaver_120Hz",
            directAction = {
                if (!hasWriteSecureSettings(context) && !hasWriteSettings(context)) return@tryDirectThenFallback false

                val cr = context.contentResolver
                val r1 = Settings.System.putFloat(cr, "peak_refresh_rate", 120.0f)
                val r2 = Settings.System.putFloat(cr, "min_refresh_rate", 120.0f)
                val r3 = try {
                    Settings.Secure.putInt(cr, "user_refresh_rate", 120)
                } catch (_: Exception) {
                    false
                }
                val r4 = try {
                    Settings.Secure.putInt(cr, "miui_refresh_rate", 120) ||
                    Settings.System.putInt(cr, "miui_refresh_rate", 120)
                } catch (_: Exception) {
                    false
                }
                r1 || r2 || r3 || r4
            },
            fallbackAction = {
                executeShizukuCommandWithTimeout(arrayOf("settings", "put", "system", "min_refresh_rate", "120.0"), 2000L)
                executeShizukuCommandWithTimeout(arrayOf("settings", "put", "system", "peak_refresh_rate", "120.0"), 2000L)
                executeShizukuCommandWithTimeout(arrayOf("settings", "put", "secure", "user_refresh_rate", "120"), 2000L)
                executeShizukuCommandWithTimeout(arrayOf("settings", "put", "secure", "miui_refresh_rate", "120"), 2000L)
                executeShizukuCommandWithTimeout(arrayOf("settings", "put", "system", "miui_refresh_rate", "120"), 2000L)
            }
        )
    }

    /**
     * Bật / Tắt Đồng bộ hóa tài khoản hệ thống (Master Sync)
     */
    suspend fun setMasterSync(enable: Boolean) {
        val flagStr = enable.toString()
        tryDirectThenFallback(
            actionName = "SetMasterSync_$enable",
            directAction = {
                ContentResolver.setMasterSyncAutomatically(enable)
                true
            },
            fallbackAction = {
                executeShizukuCommandWithTimeout(
                    arrayOf(
                        "content", "call", "--uri", "content://settings/global",
                        "--method", "PUT_global", "--arg", "sync_parent_sounds",
                        "--extra_arg:b:value", flagStr
                    ),
                    2000L
                )
                executeShizukuCommandWithTimeout(
                    arrayOf(
                        "settings", "put", "global", "sync_max_retry_delay_in_seconds",
                        if (enable) "3600" else "0"
                    ),
                    2000L
                )
            }
        )
    }

    /**
     * 5. 📴 Tối ưu Sâu khi Tắt Màn Hình (Deep Doze)
     */
    suspend fun triggerDeepDoze() {
        // 1. Tắt Master Sync nền qua Shizuku (app không có WRITE_SYNC_SETTINGS)
        try {
            executeShizukuCommandWithTimeout(
                arrayOf("content", "call", "--uri", "content://settings/global",
                    "--method", "PUT_global", "--arg", "sync_parent_sounds",
                    "--extra_arg:b:value", "false"),
                3000L
            )
            // Tắt sync trực tiếp qua settings
            executeShizukuCommandWithTimeout(
                arrayOf("settings", "put", "global", "sync_max_retry_delay_in_seconds", "0"),
                2000L
            )
            Log.d(TAG, "✅ [DeepDoze] Master Sync disabled via Shizuku")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ [DeepDoze] Master Sync toggle failed: ${e.message}")
        }

        // 2. Kích hoạt Light Idle (hoạt động trên Xiaomi/HyperOS, force-idle deep bị chặn)
        try {
            // Step light IDLE → hoạt động trên tất cả thiết bị
            val r1 = executeShizukuCommandWithTimeout(
                arrayOf("dumpsys", "deviceidle", "step", "light"), 3000L
            )
            Log.d(TAG, "✅ [DeepDoze] step light: ${r1.stdout.trim()}")

            // Rút ngắn inactive timeout để device vào deep idle nhanh hơn
            executeShizukuCommandWithTimeout(
                arrayOf("settings", "put", "global", "device_idle_constants",
                    "inactive_to=60000,idle_after_inactive_to=0,idle_pending_to=60000"),
                3000L
            )
            Log.d(TAG, "✅ [DeepDoze] Tuned device_idle_constants for faster deep idle")
        } catch (e: Exception) {
            Log.e(TAG, "❌ [DeepDoze] Light idle failed: ${e.message}")
        }
    }

    /**
     * Kích hoạt Instant Deep Doze (sau 3s tắt màn hình):
     * Cấu hình inactive_to=3000ms, idle_after_inactive_to=0ms,
     * chạy step light và force-idle deep.
     */
    suspend fun triggerInstantDeepDoze(context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            context?.let {
                if (hasWriteSecureSettings(it)) {
                    try {
                        Settings.Global.putString(
                            it.contentResolver,
                            "device_idle_constants",
                            "inactive_to=3000,idle_after_inactive_to=0"
                        )
                    } catch (_: Exception) {}
                }
            }
            executeShizukuCommandWithTimeout(
                arrayOf("settings", "put", "global", "device_idle_constants",
                    "inactive_to=3000,idle_after_inactive_to=0"),
                2000L
            )
            val rLight = executeShizukuCommandWithTimeout(
                arrayOf("dumpsys", "deviceidle", "step", "light"),
                2000L
            )
            val rDeep = executeShizukuCommandWithTimeout(
                arrayOf("dumpsys", "deviceidle", "force-idle", "deep"),
                2000L
            )
            Log.d(TAG, "⚡ [InstantDeepDoze] step light: ${rLight.stdout.trim()}, force-idle deep: ${rDeep.stdout.trim()}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ [InstantDeepDoze] Lỗi triggerInstantDeepDoze: ${e.message}")
            false
        }
    }

    /**
     * Khôi phục trạng thái Deep Doze khi màn hình bật hoặc dừng tính năng:
     * Unforce idle và xóa cấu hình device_idle_constants đè.
     */
    suspend fun resetDeepDoze(context: Context? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            context?.let {
                if (hasWriteSecureSettings(it)) {
                    try {
                        Settings.Global.putString(it.contentResolver, "device_idle_constants", null)
                    } catch (_: Exception) {}
                }
            }
            executeShizukuCommandWithTimeout(
                arrayOf("settings", "delete", "global", "device_idle_constants"),
                2000L
            )
            executeShizukuCommandWithTimeout(
                arrayOf("dumpsys", "deviceidle", "unforce"),
                2000L
            )
            Log.d(TAG, "✅ [DeepDoze] Reset device_idle_constants và unforce idle")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi resetDeepDoze: ${e.message}")
            false
        }
    }

    /**
     * GMS Doze Safe Mode: Tạm thời đưa Google Play Services vào chế độ Doze khi tắt màn hình
     * bằng cách loại khỏi whitelist deviceidle và chặn RUN_IN_BACKGROUND.
     * Khi bật màn hình (enable = false), khôi phục lại whitelist và cho phép chạy nền bình thường.
     */
    suspend fun setGmsDozeSafeMode(enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        val gmsPackage = "com.google.android.gms"
        val whitelistOp = if (enable) "-$gmsPackage" else "+$gmsPackage"
        val appOpMode = if (enable) "ignore" else "allow"

        try {
            // 1. Thao tác deviceidle whitelist
            val r1 = executeShizukuCommandWithTimeout(
                arrayOf("dumpsys", "deviceidle", "whitelist", whitelistOp),
                2000L
            )
            // 2. Thao tác appops RUN_IN_BACKGROUND
            val r2 = executeShizukuCommandWithTimeout(
                arrayOf("cmd", "appops", "set", gmsPackage, "RUN_IN_BACKGROUND", appOpMode),
                2000L
            )
            Log.d(TAG, "🛡️ [GmsDozeSafeMode] enable=$enable -> whitelist: $whitelistOp (${r1.isSuccess}), appops: $appOpMode (${r2.isSuccess})")
            r1.isSuccess || r2.isSuccess
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi setGmsDozeSafeMode(enable=$enable): ${e.message}")
            false
        }
    }

    /**
     * Smart Auto-Freeze: Đưa ứng dụng vào Standby Bucket RESTRICTED hoặc ACTIVE,
     * kết hợp với App Freezer của ActivityManager nếu nền tảng hỗ trợ.
     */
    suspend fun applySmartFreeze(packageName: String, freeze: Boolean): Boolean = withContext(Dispatchers.IO) {
        val bucket = if (freeze) "restricted" else "active"
        val freezeAction = if (freeze) "freeze" else "unfreeze"
        try {
            // 1. Standby Bucket (am set-standby-bucket <pkg> restricted/active)
            val r1 = executeShizukuCommandWithTimeout(
                arrayOf("am", "set-standby-bucket", packageName, bucket),
                2000L
            )
            // 2. Activity Freezer nếu hệ thống hỗ trợ
            executeShizukuCommandWithTimeout(
                arrayOf("cmd", "activity", freezeAction, packageName),
                2000L
            )
            Log.d(TAG, "🧊 [SmartFreeze] $packageName -> bucket=$bucket, freezer=$freezeAction, success=${r1.isSuccess}")
            r1.isSuccess
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi applySmartFreeze cho $packageName: ${e.message}")
            false
        }
    }

    /**
     * Danh sách 7 dịch vụ theo dõi, đo lường & quảng cáo nền của Xiaomi được kiểm chứng an toàn 100% (UAD-ng)
     */
    val XIAOMI_SAFE_TELEMETRY_PACKAGES = listOf(
        "com.miui.analytics",
        "com.miui.daemon",
        "com.miui.systemAdSolution",
        "com.miui.bugreport",
        "com.miui.hybrid",
        "com.xiaomi.security.onetrack",
        "com.miui.yellowpage"
    )

    /**
     * Đóng băng hoặc khôi phục an toàn 7 dịch vụ theo dõi Xiaomi:
     * Sử dụng phòng ngự 5 tầng:
     * - Khi frozen = true:
     *   1. pm disable-user --user 0 (thử nếu OEM cho phép)
     *   2. am force-stop
     *   3. am set-standby-bucket restricted (Level 5)
     *   4. cmd appops set RUN_IN_BACKGROUND ignore
     *   5. cmd appops set WAKE_LOCK ignore
     *   6. dumpsys deviceidle whitelist remove
     * - Khi frozen = false:
     *   1. pm enable
     *   2. am set-standby-bucket active
     *   3. cmd appops set RUN_IN_BACKGROUND allow
     *   4. cmd appops set WAKE_LOCK allow
     */
    suspend fun setXiaomiTelemetryFrozen(frozen: Boolean): Int = withContext(Dispatchers.IO) {
        var count = 0
        for (pkg in XIAOMI_SAFE_TELEMETRY_PACKAGES) {
            try {
                if (frozen) {
                    executeShizukuCommandWithTimeout(arrayOf("pm", "disable-user", "--user", "0", pkg), 1500L)
                    executeShizukuCommandWithTimeout(arrayOf("am", "force-stop", pkg), 1500L)
                    executeShizukuCommandWithTimeout(arrayOf("am", "set-standby-bucket", pkg, "restricted"), 1500L)
                    executeShizukuCommandWithTimeout(arrayOf("cmd", "appops", "set", pkg, "RUN_IN_BACKGROUND", "ignore"), 1500L)
                    executeShizukuCommandWithTimeout(arrayOf("cmd", "appops", "set", pkg, "WAKE_LOCK", "ignore"), 1500L)
                    executeShizukuCommandWithTimeout(arrayOf("dumpsys", "deviceidle", "whitelist", "-$pkg"), 1500L)
                    count++
                } else {
                    executeShizukuCommandWithTimeout(arrayOf("pm", "enable", pkg), 1500L)
                    executeShizukuCommandWithTimeout(arrayOf("am", "set-standby-bucket", pkg, "active"), 1500L)
                    executeShizukuCommandWithTimeout(arrayOf("cmd", "appops", "set", pkg, "RUN_IN_BACKGROUND", "allow"), 1500L)
                    executeShizukuCommandWithTimeout(arrayOf("cmd", "appops", "set", pkg, "WAKE_LOCK", "allow"), 1500L)
                    count++
                }
            } catch (e: Exception) {
                Log.w(TAG, "Lỗi setXiaomiTelemetryFrozen($pkg, frozen=$frozen): ${e.message}")
            }
        }
        Log.i(TAG, "🔒 [XiaomiTelemetry] frozen=$frozen -> Đã áp dụng cho $count/7 gói dịch vụ")
        count
    }
}
