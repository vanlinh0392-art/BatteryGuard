package com.pin.batteryguard.data.settings

import android.app.SearchManager
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.pin.batteryguard.automation.SystemActionBridge
import com.pin.batteryguard.shizuku.executeShizukuCommandWithTimeout
import com.pin.batteryguard.shizuku.shizukuNewProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

private const val TAG = "SystemSettingsManager"

/**
 * Quản lý các tinh chỉnh hệ thống mở rộng (System Tuner / Extended Settings) trên Xiaomi 14.
 * Kiến trúc 2 tầng (Two-Tier):
 * - Tier 1: Native ContentResolver (nếu có quyền WRITE_SECURE_SETTINGS / WRITE_SETTINGS)
 * - Tier 2: Shizuku IPC Shell Fallback (UID 2000)
 */
object SystemSettingsManager {

    // RFC 1123 Hostname Regex an toàn chống shell injection
    private val RFC1123_HOSTNAME_PATTERN = Pattern.compile(
        "^(?=.{1,253}\$)(?!-)[A-Za-z0-9-]{1,63}(?<!-)(\\.(?!-)[A-Za-z0-9-]{1,63}(?<!-))*$"
    )
    // Pattern phát hiện địa chỉ IP thô (IPv4 / IPv6) - DoT Android bắt buộc Hostname/FQDN
    private val IPV4_PATTERN = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}\$")
    private val IPV6_PATTERN = Pattern.compile("^[0-9a-fA-F:]+\$")

    enum class PowerKeyAction(val actionCode: String, val label: String) {
        GEMINI_ASSISTANT("launch_google_search", "Google Gemini"),
        XIAO_AI("launch_voice_assistant", "XiaoAI (Gốc)"),
        POWER_MENU("none", "Menu Nguồn (Tắt)")
    }

    private const val PREFS_NAME = "system_settings_snapshot"
    private const val KEY_CAPTURED = "is_captured"

    // Snapshot lưu cấu hình gốc trước khi người dùng can thiệp
    private var initialSnapshot: SettingsSnapshot? = null

    data class SettingsSnapshot(
        val screenTimeoutMs: Int,
        val refreshRate: Int,
        val animationScale: Float,
        val isMobileDataAlwaysOn: Boolean,
        val privateDnsMode: String,
        val privateDnsSpecifier: String,
        val isOledDarkMode: Boolean,
        val isFastCharging: Boolean,
        val isMasterSync: Boolean,
        val powerKeyAction: String
    )

    /**
     * Chụp snapshot ban đầu (chỉ chụp 1 lần duy nhất và lưu vào SharedPreferences)
     * để phục vụ nút Khôi phục gốc mà không bị mất khi ứng dụng tắt tiến trình.
     */
    fun captureInitialSnapshotIfEmpty(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (initialSnapshot == null) {
            if (prefs.getBoolean(KEY_CAPTURED, false)) {
                initialSnapshot = SettingsSnapshot(
                    screenTimeoutMs = prefs.getInt("screenTimeoutMs", 60000),
                    refreshRate = prefs.getInt("refreshRate", 120),
                    animationScale = prefs.getFloat("animationScale", 1.0f),
                    isMobileDataAlwaysOn = prefs.getBoolean("isMobileDataAlwaysOn", true),
                    privateDnsMode = prefs.getString("privateDnsMode", "off") ?: "off",
                    privateDnsSpecifier = prefs.getString("privateDnsSpecifier", "") ?: "",
                    isOledDarkMode = prefs.getBoolean("isOledDarkMode", false),
                    isFastCharging = prefs.getBoolean("isFastCharging", true),
                    isMasterSync = prefs.getBoolean("isMasterSync", true),
                    powerKeyAction = prefs.getString("powerKeyAction", PowerKeyAction.XIAO_AI.name) ?: PowerKeyAction.XIAO_AI.name
                )
                Log.d(TAG, "📸 Đã khôi phục snapshot cấu hình gốc từ SharedPreferences: $initialSnapshot")
                return
            }

            val snapshot = SettingsSnapshot(
                screenTimeoutMs = getScreenTimeout(context),
                refreshRate = getRefreshRate(context),
                animationScale = getAnimationScale(context),
                isMobileDataAlwaysOn = isMobileDataAlwaysOn(context),
                privateDnsMode = getPrivateDnsMode(context),
                privateDnsSpecifier = getPrivateDnsSpecifier(context),
                isOledDarkMode = SystemActionBridge.isSystemDarkMode(context),
                isFastCharging = SystemActionBridge.isFastChargingEnabled(context),
                isMasterSync = isMasterSyncEnabled(),
                powerKeyAction = getPowerKeyAction(context).name
            )
            initialSnapshot = snapshot
            prefs.edit()
                .putBoolean(KEY_CAPTURED, true)
                .putInt("screenTimeoutMs", snapshot.screenTimeoutMs)
                .putInt("refreshRate", snapshot.refreshRate)
                .putFloat("animationScale", snapshot.animationScale)
                .putBoolean("isMobileDataAlwaysOn", snapshot.isMobileDataAlwaysOn)
                .putString("privateDnsMode", snapshot.privateDnsMode)
                .putString("privateDnsSpecifier", snapshot.privateDnsSpecifier)
                .putBoolean("isOledDarkMode", snapshot.isOledDarkMode)
                .putBoolean("isFastCharging", snapshot.isFastCharging)
                .putBoolean("isMasterSync", snapshot.isMasterSync)
                .putString("powerKeyAction", snapshot.powerKeyAction)
                .apply()
            Log.d(TAG, "📸 Đã lưu snapshot cấu hình gốc vào SharedPreferences: $initialSnapshot")
        }
    }

    // ==========================================
    // 1. MÀN HÌNH & HIỂN THỊ (DISPLAY & VISUALS)
    // ==========================================

    fun getScreenTimeout(context: Context): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 60000)
        } catch (_: Exception) {
            60000
        }
    }

    suspend fun setScreenTimeout(context: Context, timeoutMs: Int): Boolean = withContext(Dispatchers.IO) {
        // Clamp bounds: 15s (15000) đến MaxInt (Never lock)
        val clamped = timeoutMs.coerceIn(15000, Int.MAX_VALUE)
        captureInitialSnapshotIfEmpty(context)

        var success = false
        if (Settings.System.canWrite(context)) {
            try {
                success = Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, clamped)
            } catch (e: Exception) {
                Log.w(TAG, "Native putInt SCREEN_OFF_TIMEOUT thất bại: ${e.message}")
            }
        }
        if (!success) {
            val res = executeShizukuCommandWithTimeout(
                arrayOf("settings", "put", "system", "screen_off_timeout", clamped.toString()),
                2500L
            )
            success = res.isSuccess
        }
        success
    }

    fun getRefreshRate(context: Context): Int {
        return try {
            val peak = Settings.System.getFloat(context.contentResolver, "peak_refresh_rate", 120.0f)
            if (peak <= 60.0f) 60 else 120
        } catch (_: Exception) {
            120
        }
    }

    suspend fun setRefreshRate(context: Context, rate: Int): Boolean = withContext(Dispatchers.IO) {
        val targetPeak = if (rate <= 60) 60.0f else 120.0f
        val rateInt = if (rate <= 60) 60 else 120
        // Xiaomi 14 sở hữu tấm nền LTPO AMOLED (1-120Hz).
        // Khi chọn 120Hz: min_refresh_rate = 1.0f để hệ thống hạ xung nhịp xuống 1Hz lúc đọc sách/AOD, tối ưu pin.
        // Khi chọn 60Hz: min_refresh_rate = 60.0f để giữ ổn định tốc độ làm tươi 60Hz.
        val targetMin = if (rateInt == 60) 60.0f else 1.0f
        captureInitialSnapshotIfEmpty(context)

        var success = false
        if (SystemActionBridge.hasWriteSecureSettings(context)) {
            try {
                val cr = context.contentResolver
                Settings.System.putFloat(cr, "peak_refresh_rate", targetPeak)
                Settings.System.putFloat(cr, "min_refresh_rate", targetMin)
                Settings.Secure.putInt(cr, "user_refresh_rate", rateInt)
                success = true
            } catch (e: Exception) {
                Log.w(TAG, "Native write refresh rate failed: ${e.message}")
            }
        }
        if (!success) {
            val r1 = executeShizukuCommandWithTimeout(
                arrayOf("settings", "put", "system", "peak_refresh_rate", targetPeak.toString()),
                2000L
            )
            executeShizukuCommandWithTimeout(
                arrayOf("settings", "put", "secure", "user_refresh_rate", rateInt.toString()),
                2000L
            )
            executeShizukuCommandWithTimeout(
                arrayOf("settings", "put", "system", "min_refresh_rate", targetMin.toString()),
                2000L
            )
            success = r1.isSuccess
        }
        success
    }

    fun getAnimationScale(context: Context): Float {
        return try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.WINDOW_ANIMATION_SCALE, 1.0f)
        } catch (_: Exception) {
            1.0f
        }
    }

    suspend fun setAnimationScale(context: Context, scale: Float): Boolean = withContext(Dispatchers.IO) {
        val targetScale = scale.coerceIn(0.0f, 1.0f)
        captureInitialSnapshotIfEmpty(context)

        var success = false
        if (SystemActionBridge.hasWriteSecureSettings(context)) {
            try {
                val cr = context.contentResolver
                Settings.Global.putFloat(cr, Settings.Global.WINDOW_ANIMATION_SCALE, targetScale)
                Settings.Global.putFloat(cr, Settings.Global.TRANSITION_ANIMATION_SCALE, targetScale)
                Settings.Global.putFloat(cr, Settings.Global.ANIMATOR_DURATION_SCALE, targetScale)
                success = true
            } catch (e: Exception) {
                Log.w(TAG, "Native write animation scales failed: ${e.message}")
            }
        }
        if (!success) {
            val scaleStr = String.format(java.util.Locale.US, "%.1f", targetScale)
            val r1 = executeShizukuCommandWithTimeout(
                arrayOf("settings", "put", "global", "window_animation_scale", scaleStr),
                2000L
            )
            executeShizukuCommandWithTimeout(
                arrayOf("settings", "put", "global", "transition_animation_scale", scaleStr),
                2000L
            )
            executeShizukuCommandWithTimeout(
                arrayOf("settings", "put", "global", "animator_duration_scale", scaleStr),
                2000L
            )
            success = r1.isSuccess
        }
        success
    }

    // ==========================================
    // 2. MẠNG & BẢO MẬT (NETWORK & SECURITY)
    // ==========================================

    fun isMobileDataAlwaysOn(context: Context): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, "mobile_data_always_on", 1) == 1
        } catch (_: Exception) {
            true
        }
    }

    suspend fun setMobileDataAlwaysOn(context: Context, enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        val flag = if (enable) 1 else 0
        captureInitialSnapshotIfEmpty(context)

        var success = false
        if (SystemActionBridge.hasWriteSecureSettings(context)) {
            try {
                success = Settings.Global.putInt(context.contentResolver, "mobile_data_always_on", flag)
            } catch (e: Exception) {
                Log.w(TAG, "Native write mobile_data_always_on failed: ${e.message}")
            }
        }
        if (!success) {
            val res = executeShizukuCommandWithTimeout(
                arrayOf("settings", "put", "global", "mobile_data_always_on", flag.toString()),
                2500L
            )
            success = res.isSuccess
        }
        success
    }

    fun getPrivateDnsMode(context: Context): String {
        return try {
            Settings.Global.getString(context.contentResolver, "private_dns_mode") ?: "off"
        } catch (_: Exception) {
            "off"
        }
    }

    fun getPrivateDnsSpecifier(context: Context): String {
        return try {
            Settings.Global.getString(context.contentResolver, "private_dns_specifier") ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    suspend fun setPrivateDns(context: Context, mode: String, specifier: String = ""): Boolean = withContext(Dispatchers.IO) {
        val safeMode = when (mode.lowercase()) {
            "hostname" -> "hostname"
            "opportunistic" -> "opportunistic"
            else -> "off"
        }

        val safeSpecifier = specifier.trim()
        if (safeMode == "hostname") {
            // Android DoT bắt buộc FQDN/Domain name. Nhập IP thô (1.1.1.1) sẽ làm crash DNS handshake và mất mạng!
            if (IPV4_PATTERN.matcher(safeSpecifier).matches() || (safeSpecifier.contains(":") && IPV6_PATTERN.matcher(safeSpecifier).matches())) {
                Log.e(TAG, "❌ Private DNS không chấp nhận địa chỉ IP thô (chỉ hỗ trợ FQDN như dns.adguard-dns.com): $safeSpecifier")
                return@withContext false
            }
            if (!RFC1123_HOSTNAME_PATTERN.matcher(safeSpecifier).matches()) {
                Log.e(TAG, "❌ Hostname Private DNS không hợp lệ chuẩn RFC 1123: $safeSpecifier")
                return@withContext false
            }
        }

        captureInitialSnapshotIfEmpty(context)

        // Quy tắc an toàn: Luôn ghi specifier trước khi bật mode hostname để tránh lỗi lookup tức thời
        var success = false
        if (SystemActionBridge.hasWriteSecureSettings(context)) {
            try {
                val cr = context.contentResolver
                if (safeMode == "hostname") {
                    Settings.Global.putString(cr, "private_dns_specifier", safeSpecifier)
                }
                Settings.Global.putString(cr, "private_dns_mode", safeMode)
                success = true
            } catch (e: Exception) {
                Log.w(TAG, "Native write private DNS failed: ${e.message}")
            }
        }
        if (!success) {
            if (safeMode == "hostname") {
                executeShizukuCommandWithTimeout(
                    arrayOf("settings", "put", "global", "private_dns_specifier", safeSpecifier),
                    2500L
                )
            }
            val r1 = executeShizukuCommandWithTimeout(
                arrayOf("settings", "put", "global", "private_dns_mode", safeMode),
                2500L
            )
            success = r1.isSuccess
        }
        success
    }

    fun isMasterSyncEnabled(): Boolean {
        return try {
            ContentResolver.getMasterSyncAutomatically()
        } catch (e: SecurityException) {
            false
        }
    }

    suspend fun setMasterSyncEnabled(enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            ContentResolver.setMasterSyncAutomatically(enable)
            true
        } catch (_: Exception) {
            false
        }
    }

    // ==========================================
    // 3. HIỆU NĂNG & THÔNG TIN THIẾT BỊ XIAOMI 14
    // ==========================================

    fun getRamExtensionStatus(context: Context): String {
        return try {
            val isExternalRamOn = Settings.Global.getInt(context.contentResolver, "isExternalRamOn", -1)
            when (isExternalRamOn) {
                1 -> "Đang BẬT (Khuyến nghị: Tắt để tăng tuổi thọ chip nhớ UFS)"
                0 -> "Đã TẮT (Tối ưu nhất cho chip Snapdragon 8 Gen 3)"
                else -> "Không phát hiện RAM ảo"
            }
        } catch (_: Exception) {
            "Không xác định"
        }
    }

    // ==========================================
    // 4. NÚT NGUỒN & TRỢ LÝ ẢO (POWER KEY & ASSISTANT)
    // ==========================================

    fun getPowerKeyAction(context: Context): PowerKeyAction {
        return try {
            val cr = context.contentResolver
            val action = Settings.System.getString(cr, "long_press_power_key") ?: ""
            val xiaoAiVal = try {
                Settings.System.getInt(cr, "long_press_power_launch_xiaoai", -1)
            } catch (_: Exception) {
                -1
            }

            when {
                action == PowerKeyAction.GEMINI_ASSISTANT.actionCode -> PowerKeyAction.GEMINI_ASSISTANT
                action == PowerKeyAction.XIAO_AI.actionCode || xiaoAiVal == 1 -> PowerKeyAction.XIAO_AI
                action == PowerKeyAction.POWER_MENU.actionCode || action == "none" || action == "global_actions" || action.isEmpty() -> PowerKeyAction.POWER_MENU
                else -> PowerKeyAction.POWER_MENU
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi đọc trạng thái nút nguồn: ${e.message}")
            PowerKeyAction.POWER_MENU
        }
    }

    suspend fun setPowerKeyAction(context: Context, action: PowerKeyAction): Boolean = withContext(Dispatchers.IO) {
        captureInitialSnapshotIfEmpty(context)

        val xiaoAiFlag = if (action == PowerKeyAction.XIAO_AI) 1 else 0
        var success = false

        // Tier 1: Native ContentResolver (Settings.System)
        if (SystemActionBridge.hasWriteSecureSettings(context) || Settings.System.canWrite(context)) {
            try {
                val cr = context.contentResolver
                Settings.System.putString(cr, "long_press_power_key", action.actionCode)
                Settings.System.putInt(cr, "long_press_power_launch_xiaoai", xiaoAiFlag)
                success = true
            } catch (e: Exception) {
                Log.w(TAG, "Native write power key action failed: ${e.message}")
            }
        }

        // Tier 2: Shizuku IPC Shell Fallback
        if (!success) {
            val r1 = executeShizukuCommandWithTimeout(
                arrayOf("settings", "put", "system", "long_press_power_key", action.actionCode),
                2500L
            )
            val r2 = executeShizukuCommandWithTimeout(
                arrayOf("settings", "put", "system", "long_press_power_launch_xiaoai", xiaoAiFlag.toString()),
                2500L
            )
            success = r1.isSuccess || r2.isSuccess
        }

        // Khi gán GEMINI_ASSISTANT: cấu hình role assistant cho Google App và Gemini qua Shizuku
        if (action == PowerKeyAction.GEMINI_ASSISTANT) {
            try {
                executeShizukuCommandWithTimeout(
                    arrayOf("cmd", "role", "add-role-holder", "--user", "0", "android.app.role.ASSISTANT", "com.google.android.googlequicksearchbox"),
                    3000L
                )
                executeShizukuCommandWithTimeout(
                    arrayOf("cmd", "role", "add-role-holder", "--user", "0", "android.app.role.ASSISTANT", "com.google.android.apps.bard"),
                    3000L
                )
            } catch (e: Exception) {
                Log.w(TAG, "Gán Assistant role qua Shizuku thất bại: ${e.message}")
            }
        }

        success
    }

    fun isGeminiInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return try {
            pm.getPackageInfo("com.google.android.apps.bard", 0)
            true
        } catch (_: Exception) {
            try {
                pm.getPackageInfo("com.google.android.googlequicksearchbox", 0)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun launchGeminiTest(context: Context): Boolean {
        // Fallback 1: Bard Explicit Component
        try {
            val bardIntent = Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(
                    "com.google.android.apps.bard",
                    "com.google.android.apps.bard.MainActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(bardIntent)
            return true
        } catch (_: Exception) {
            Log.d(TAG, "Fallback 1 (Bard Component) không khả dụng, thử Fallback 2...")
        }

        // Fallback 2: ACTION_ASSIST Intent
        try {
            val assistIntent = Intent(Intent.ACTION_ASSIST).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(assistIntent)
            return true
        } catch (_: Exception) {
            Log.d(TAG, "Fallback 2 (ACTION_ASSIST) không khả dụng, thử Fallback 3...")
        }

        // Fallback 3: SearchManager.launchAssist (ẩn trong public SDK stubs, gọi an toàn qua reflection)
        try {
            val searchManager = context.getSystemService(Context.SEARCH_SERVICE) as? SearchManager
            if (searchManager != null) {
                val method = searchManager.javaClass.getMethod("launchAssist", Bundle::class.java)
                val launched = method.invoke(searchManager, Bundle()) as? Boolean ?: false
                if (launched) return true
            }
        } catch (_: Exception) {
            Log.d(TAG, "Fallback 3 (SearchManager.launchAssist) không khả dụng, thử Fallback 4...")
        }

        // Fallback 4: LaunchIntent (Bard hoặc Google App)
        try {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage("com.google.android.apps.bard")
                ?: pm.getLaunchIntentForPackage("com.google.android.googlequicksearchbox")
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tất cả 4 fallback khởi chạy Gemini đều thất bại: ${e.message}")
        }

        return false
    }

    fun openManageAssistSettings(context: Context): Boolean {
        // 1. Mở trang Quản lý ứng dụng Trợ lý ảo mặc định của Xiaomi / Android
        try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.android.settings",
                    "com.android.settings.Settings\$ManageAssistActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return true
        } catch (_: Exception) {
            Log.d(TAG, "Không thể mở ManageAssistActivity trực tiếp, thử fallback VOICE_INPUT_SETTINGS")
        }

        // 2. Fallback: Màn hình Cài đặt Nhập giọng nói & Trợ lý chuẩn của Android
        return try {
            val fallbackIntent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Không thể mở cài đặt Trợ lý ảo: ${e.message}")
            false
        }
    }

    // ==========================================
    // 4.1. AUTO-SENTINEL DUY TRÌ GEMINI (0% HAO PIN)
    // ==========================================

    private const val KEY_GEMINI_AUTO_SENTINEL = "gemini_auto_sentinel"

    fun isAutoSentinelEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_GEMINI_AUTO_SENTINEL, true)
    }

    fun setAutoSentinelEnabled(context: Context, enable: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_GEMINI_AUTO_SENTINEL, enable).apply()
    }

    suspend fun reapplyGeminiIfOverwritten(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!isAutoSentinelEnabled(context)) return@withContext false
        val currentAction = getPowerKeyAction(context)
        if (currentAction != PowerKeyAction.GEMINI_ASSISTANT) {
            Log.i(TAG, "🛡️ Gemini Auto-Sentinel: Phát hiện hành vi phím nguồn bị ghi đè ($currentAction), đang tự động khôi phục về Gemini...")
            return@withContext setPowerKeyAction(context, PowerKeyAction.GEMINI_ASSISTANT)
        }
        false
    }

    private var sentinelObserver: ContentObserver? = null
    private val sentinelScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val sentinelHandler = Handler(Looper.getMainLooper())
    private var pendingSentinelRunnable: Runnable? = null

    private class GeminiSentinelObserver(
        private val appContext: Context,
        private val handler: Handler
    ) : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            pendingSentinelRunnable?.let { handler.removeCallbacks(it) }
            val runnable = Runnable {
                sentinelScope.launch {
                    try {
                        reapplyGeminiIfOverwritten(appContext)
                    } catch (e: Exception) {
                        Log.e(TAG, "Lỗi trong GeminiSentinelObserver: ${e.message}")
                    }
                }
            }
            pendingSentinelRunnable = runnable
            handler.postDelayed(runnable, 300L)
        }
    }

    fun registerGeminiSentinelObserver(context: Context) {
        if (sentinelObserver != null) return
        val appContext = context.applicationContext
        val observer = GeminiSentinelObserver(appContext, sentinelHandler)
        sentinelObserver = observer
        try {
            val cr = appContext.contentResolver
            cr.registerContentObserver(
                Settings.System.getUriFor("long_press_power_key"),
                false,
                observer
            )
            cr.registerContentObserver(
                Settings.System.getUriFor("long_press_power_launch_xiaoai"),
                false,
                observer
            )
            Log.d(TAG, "🛡️ Đã đăng ký GeminiSentinelObserver thành công")
        } catch (e: Exception) {
            Log.w(TAG, "Đăng ký GeminiSentinelObserver thất bại: ${e.message}")
        }
    }

    fun unregisterGeminiSentinelObserver(context: Context) {
        sentinelObserver?.let { observer ->
            try {
                pendingSentinelRunnable?.let { sentinelHandler.removeCallbacks(it) }
                pendingSentinelRunnable = null
                context.applicationContext.contentResolver.unregisterContentObserver(observer)
                Log.d(TAG, "🛡️ Đã hủy đăng ký GeminiSentinelObserver")
            } catch (e: Exception) {
                Log.w(TAG, "Hủy đăng ký GeminiSentinelObserver thất bại: ${e.message}")
            }
            sentinelObserver = null
        }
    }

    // ==========================================
    // 5. NHẤN GIỮ THANH HOME & DỊCH MÀN HÌNH (HOME LONG PRESS & SCREEN SEARCH)
    // ==========================================

    fun isHomeLongPressSearchEnabled(context: Context): Boolean {
        return try {
            val cr = context.contentResolver
            val cts = Settings.Secure.getInt(cr, "search_press_hold_nav_handle", 0) == 1
            val homeAction = Settings.System.getString(cr, "long_press_home_key") ?: ""
            val inputGesture = Settings.System.getString(cr, "system_input_gesture") ?: ""
            val twoGesture = Settings.System.getString(cr, "two_gesture_long_press") ?: ""
            cts || homeAction == "launch_google_search" || inputGesture == "launch_google_search" || twoGesture == "launch_google_search"
        } catch (_: Exception) {
            false
        }
    }

    fun isAssistContextEnabled(context: Context): Boolean {
        return try {
            val cr = context.contentResolver
            val struct = Settings.Secure.getInt(cr, "assist_structure_enabled", 0) == 1
            val screen = Settings.Secure.getInt(cr, "assist_screenshot_enabled", 0) == 1
            struct && screen
        } catch (_: Exception) {
            false
        }
    }

    suspend fun setHomeLongPressSearch(context: Context, enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        var success = false

        // Tier 1: Native ContentResolver (nếu có WRITE_SECURE_SETTINGS)
        if (SystemActionBridge.hasWriteSecureSettings(context)) {
            try {
                val cr = context.contentResolver
                if (enable) {
                    Settings.Secure.putInt(cr, "search_press_hold_nav_handle", 1)
                    Settings.System.putString(cr, "long_press_home_key", "launch_google_search")
                    Settings.System.putString(cr, "system_input_gesture", "launch_google_search")
                    Settings.System.putString(cr, "two_gesture_long_press", "launch_google_search")
                    Settings.Secure.putInt(cr, "assist_structure_enabled", 1)
                    Settings.Secure.putInt(cr, "assist_screenshot_enabled", 1)
                    Settings.Global.putInt(cr, "hide_gesture_line", 0)
                } else {
                    Settings.Secure.putInt(cr, "search_press_hold_nav_handle", 0)
                    Settings.System.putString(cr, "long_press_home_key", "none")
                    Settings.System.putString(cr, "system_input_gesture", "none")
                    Settings.System.putString(cr, "two_gesture_long_press", "none")
                }
                success = true
            } catch (e: Exception) {
                Log.w(TAG, "Native write home long press search failed: ${e.message}")
            }
        }

        // Tier 2: Shizuku IPC Shell Fallback
        if (!success) {
            if (enable) {
                val r1 = executeShizukuCommandWithTimeout(
                    arrayOf("settings", "put", "secure", "search_press_hold_nav_handle", "1"),
                    2000L
                )
                executeShizukuCommandWithTimeout(
                    arrayOf("settings", "put", "system", "long_press_home_key", "launch_google_search"),
                    2000L
                )
                executeShizukuCommandWithTimeout(
                    arrayOf("settings", "put", "system", "system_input_gesture", "launch_google_search"),
                    2000L
                )
                executeShizukuCommandWithTimeout(
                    arrayOf("settings", "put", "system", "two_gesture_long_press", "launch_google_search"),
                    2000L
                )
                executeShizukuCommandWithTimeout(
                    arrayOf("settings", "put", "secure", "assist_structure_enabled", "1"),
                    2000L
                )
                executeShizukuCommandWithTimeout(
                    arrayOf("settings", "put", "secure", "assist_screenshot_enabled", "1"),
                    2000L
                )
                executeShizukuCommandWithTimeout(
                    arrayOf("settings", "put", "global", "hide_gesture_line", "0"),
                    2000L
                )
                success = r1.isSuccess
            } else {
                val r1 = executeShizukuCommandWithTimeout(
                    arrayOf("settings", "put", "secure", "search_press_hold_nav_handle", "0"),
                    2000L
                )
                executeShizukuCommandWithTimeout(
                    arrayOf("settings", "put", "system", "long_press_home_key", "none"),
                    2000L
                )
                executeShizukuCommandWithTimeout(
                    arrayOf("settings", "put", "system", "system_input_gesture", "none"),
                    2000L
                )
                executeShizukuCommandWithTimeout(
                    arrayOf("settings", "put", "system", "two_gesture_long_press", "none"),
                    2000L
                )
                success = r1.isSuccess
            }
        }

        // Khi bật: củng cố role ASSISTANT qua Shizuku nếu cần
        if (enable) {
            try {
                executeShizukuCommandWithTimeout(
                    arrayOf("cmd", "role", "add-role-holder", "--user", "0", "android.app.role.ASSISTANT", "com.google.android.googlequicksearchbox"),
                    2500L
                )
            } catch (e: Exception) {
                Log.w(TAG, "Cấp role Assistant cho Circle to Search thất bại: ${e.message}")
            }
        }

        success
    }

    fun isMiCTSInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.parallelc.micts", 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun launchMiCTS(context: Context): Boolean {
        // Attempt 1: Explicit Intent
        try {
            val intent = Intent().apply {
                component = ComponentName("com.parallelc.micts", "com.parallelc.micts.ui.activity.MainActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            context.startActivity(intent)
            return true
        } catch (e: Exception) {
            Log.d(TAG, "Mở MiCTS bằng Intent thất bại: ${e.message}")
        }

        // Attempt 2: Fallback to Shizuku shell
        try {
            val process = shizukuNewProcess(
                arrayOf("am", "start", "-n", "com.parallelc.micts/.ui.activity.MainActivity")
            )
            val exited = process.waitFor(1500L, TimeUnit.MILLISECONDS)
            if (exited) {
                return process.exitValue() == 0
            }
            return true
        } catch (e: Exception) {
            Log.d(TAG, "Mở MiCTS qua Shizuku shell thất bại: ${e.message}")
        }

        return false
    }

    fun launchScreenSearchTest(context: Context): Boolean {
        if (isMiCTSInstalled(context)) {
            val launched = launchMiCTS(context)
            if (launched) return true
        }

        // Fallback 1: ACTION_ASSIST với cờ ngữ cảnh màn hình
        try {
            val assistIntent = Intent(Intent.ACTION_ASSIST).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(assistIntent)
            return true
        } catch (e: Exception) {
            Log.d(TAG, "Fallback 1 (ACTION_ASSIST) thất bại: ${e.message}")
        }

        // Fallback 2: Thử mở Google Lens / Search Activity
        try {
            val lensIntent = Intent().apply {
                component = ComponentName(
                    "com.google.android.googlequicksearchbox",
                    "com.google.android.apps.search.lens.LensActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(lensIntent)
            return true
        } catch (e: Exception) {
            Log.d(TAG, "Fallback 2 (LensActivity) thất bại: ${e.message}")
        }

        // Fallback 3: SearchManager.launchAssist
        try {
            val searchManager = context.getSystemService(Context.SEARCH_SERVICE) as? SearchManager
            if (searchManager != null) {
                val method = searchManager.javaClass.getMethod("launchAssist", Bundle::class.java)
                val launched = method.invoke(searchManager, Bundle()) as? Boolean ?: false
                if (launched) return true
            }
        } catch (e: Exception) {
            Log.d(TAG, "Fallback 3 (SearchManager.launchAssist) thất bại: ${e.message}")
        }

        return false
    }

    fun launchLensTranslateDirect(context: Context): Boolean {
        // Fallback 1: Intent URI googlelens://translate
        try {
            val uriIntent = Intent(Intent.ACTION_VIEW, Uri.parse("googlelens://translate")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(uriIntent)
            return true
        } catch (_: Exception) {
            Log.d(TAG, "Fallback 1 (googlelens://translate) không khả dụng")
        }

        // Fallback 2: Google Lens Translate Explicit Action
        try {
            val actionIntent = Intent("com.google.android.apps.search.lens.action.TRANSLATE").apply {
                setPackage("com.google.android.googlequicksearchbox")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(actionIntent)
            return true
        } catch (_: Exception) {
            Log.d(TAG, "Fallback 2 (Lens action TRANSLATE) không khả dụng")
        }

        // Fallback 3: LensActivity với Extra TRANSLATE
        try {
            val lensIntent = Intent().apply {
                component = ComponentName(
                    "com.google.android.googlequicksearchbox",
                    "com.google.android.apps.search.lens.LensActivity"
                )
                putExtra("lens_intent_type", "TRANSLATE")
                putExtra("source", "batteryguard")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(lensIntent)
            return true
        } catch (_: Exception) {
            Log.d(TAG, "Fallback 3 (LensActivity TRANSLATE) không khả dụng")
        }

        // Fallback 4: LaunchIntent cho app Google Lens độc lập (com.google.ar.lens)
        try {
            val pm = context.packageManager
            val lensPkgIntent = pm.getLaunchIntentForPackage("com.google.ar.lens")
            if (lensPkgIntent != null) {
                lensPkgIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(lensPkgIntent)
                return true
            }
        } catch (_: Exception) {
            Log.d(TAG, "Fallback 4 (com.google.ar.lens) không khả dụng")
        }

        return false
    }

    suspend fun restoreInitialState(context: Context): Boolean = withContext(Dispatchers.IO) {
        val snapshot = initialSnapshot ?: return@withContext false
        try {
            setScreenTimeout(context, snapshot.screenTimeoutMs)
            setRefreshRate(context, snapshot.refreshRate)
            setAnimationScale(context, snapshot.animationScale)
            setMobileDataAlwaysOn(context, snapshot.isMobileDataAlwaysOn)
            setPrivateDns(context, snapshot.privateDnsMode, snapshot.privateDnsSpecifier)
            SystemActionBridge.setSystemDarkMode(context, snapshot.isOledDarkMode)
            SystemActionBridge.setFastChargingEnabled(context, snapshot.isFastCharging)
            setMasterSyncEnabled(snapshot.isMasterSync)
            val action = try {
                PowerKeyAction.valueOf(snapshot.powerKeyAction)
            } catch (_: Exception) {
                PowerKeyAction.XIAO_AI
            }
            setPowerKeyAction(context, action)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Khôi phục snapshot thất bại: ${e.message}")
            false
        }
    }
}
