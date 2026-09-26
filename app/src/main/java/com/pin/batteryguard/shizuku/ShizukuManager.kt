package com.pin.batteryguard.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import javax.inject.Inject
import javax.inject.Singleton

enum class ShizukuStatus {
    NOT_INSTALLED,
    INSTALLED_NOT_RUNNING,
    RUNNING_NO_PERMISSION,
    READY
}

@Singleton
class ShizukuManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val autoStarter: ShizukuAutoStarter
) {
    private val _status = MutableStateFlow(ShizukuStatus.NOT_INSTALLED)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    // ========== Anti-spam: Cache + Cooldown ==========
    
    /** Cache kết quả trong 30 giây — tránh gọi pingBinder() nhiều lần trong 1 chu kỳ quét */
    private val CACHE_TTL_MS = 30 * 1000L
    @Volatile private var cachedReadyResult: Boolean = false
    @Volatile private var cachedReadyTime: Long = 0

    /** Cooldown giữa các lần toggle Wireless Debugging: 5 phút */
    private val RECONNECT_COOLDOWN_MS = 5 * 60 * 1000L
    @Volatile private var lastReconnectAttemptTime: Long = 0

    /** BUG #2 FIX: Flag để chỉ grant quyền 1 lần duy nhất mỗi session */
    @Volatile private var permissionsGranted = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        android.util.Log.d("ShizukuManager", "🔗 Binder received! Shizuku đã kết nối lại.")
        invalidateCache()
        updateStatus()
        // Tắt thông báo mất kết nối
        com.pin.batteryguard.util.NotificationHelper.dismissShizukuDisconnected(context)
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        android.util.Log.w("ShizukuManager", "💀 Binder dead! Mất kết nối Shizuku.")
        invalidateCache()
        permissionsGranted = false  // Reset flag khi binder chết để grant lại lần sau
        updateStatus()
        // Push thông báo mất kết nối 1 lần
        com.pin.batteryguard.util.NotificationHelper.showShizukuDisconnected(context)
    }

    init {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            updateStatus()
        } catch (e: Throwable) {
            _status.value = ShizukuStatus.NOT_INSTALLED
        }
    }

    fun updateStatus() {
        if (!isShizukuInstalled()) {
            _status.value = ShizukuStatus.NOT_INSTALLED
            return
        }

        if (!Shizuku.pingBinder()) {
            _status.value = ShizukuStatus.INSTALLED_NOT_RUNNING
            return
        }

        if (checkPermission()) {
            _status.value = ShizukuStatus.READY
            // Tự động grant các quyền hệ thống khi Shizuku READY
            grantSystemPermissions()
        } else {
            _status.value = ShizukuStatus.RUNNING_NO_PERMISSION
        }
    }

    private fun isShizukuInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun checkPermission(): Boolean {
        if (!Shizuku.pingBinder()) return false
        return if (Shizuku.isPreV11()) {
            context.checkSelfPermission("moe.shizuku.manager.permission.API_V23") == PackageManager.PERMISSION_GRANTED
        } else {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestPermission(requestCode: Int): Boolean {
        if (!Shizuku.pingBinder()) return false
        return try {
            if (Shizuku.isPreV11()) {
                false
            } else {
                Shizuku.requestPermission(requestCode)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isReady(): Boolean {
        return _status.value == ShizukuStatus.READY && Shizuku.pingBinder()
    }

    /**
     * User bấm nút Reload/Refresh hoặc bấm thông báo mất kết nối.
     * Flow:
     * 1. Reset mọi cache/cooldown/flag
     * 2. Nếu WD đang TẮT + có quyền WRITE_SECURE_SETTINGS → tự bật WD
     * 3. Kiểm tra xem Shizuku đã sống lại chưa
     * 4. Nếu chưa → Gửi lệnh khởi động Shizuku qua ADB localhost:5555 trực tiếp
     * 5. Cập nhật và trả về trạng thái
     */
    suspend fun forceRefresh(): Boolean {
        android.util.Log.d("ShizukuManager", "🔄 User bấm Reload — gửi lệnh khởi chạy qua ADB...")
        invalidateCache()
        permissionsGranted = false
        lastReconnectAttemptTime = 0  // Reset cooldown để cho phép toggle ngay

        // Bước 1: Tự bật Wireless Debugging nếu đang TẮT
        if (!isWirelessDebuggingEnabled() && hasWriteSecureSettings()) {
            android.util.Log.d("ShizukuManager", "📡 WD đang TẮT → tự bật lên")
            enableWirelessDebugging()
            delay(1500) // Đợi hệ thống khởi tạo adb wifi
        }

        // Bước 2: Thử kiểm tra trạng thái
        updateStatus()
        if (isReady()) {
            android.util.Log.d("ShizukuManager", "✅ Shizuku đã sẵn sàng!")
            return true
        }

        // Bước 3: Gửi lệnh khởi động Shizuku qua ADB trực tiếp (không mở app Shizuku)
        android.util.Log.d("ShizukuManager", "⚡ Gửi lệnh ADB localhost:5555 khởi chạy dịch vụ Shizuku...")
        autoStarter.startShizukuService(notifyOnSuccess = true, isManual = true)
        updateStatus()

        return isReady()
    }

    /**
     * Đảm bảo Shizuku sẵn sàng trước khi thực thi lệnh.
     *
     * Flow 3 tầng nhẹ nhàng (KHÔNG tự ý gọi ADB để tránh spam popup hệ thống):
     * 1. Fast-path: isReady() → return ngay (0ms)
     * 2. Cache hit: trả kết quả cũ trong 30s (0ms, tránh spam trong 1 chu kỳ quét)
     * 3. Slow-path: pingBinder() trực tiếp + thử toggle Wireless Debugging nếu có quyền WRITE_SECURE_SETTINGS
     */
    suspend fun ensureReady(): Boolean {
        // Tầng 1: Fast-path
        if (isReady()) {
            cachedReadyResult = true
            cachedReadyTime = System.currentTimeMillis()
            return true
        }

        val now = System.currentTimeMillis()

        // Tầng 2: Cache hit
        if (now - cachedReadyTime < CACHE_TTL_MS) {
            return cachedReadyResult
        }

        // Tầng 3: Ping trực tiếp
        try {
            if (Shizuku.pingBinder()) {
                updateStatus()
                if (isReady()) {
                    android.util.Log.d("ShizukuManager", "✅ Shizuku sẵn sàng (phát hiện qua ping)")
                    cachedReadyResult = true
                    cachedReadyTime = System.currentTimeMillis()
                    return true
                }
            }
        } catch (e: Exception) {
            // Ignore
        }

        // Thử kích hoạt lại Shizuku qua toggle Wireless Debugging
        // Chỉ thử nếu ngoài cooldown và có quyền WRITE_SECURE_SETTINGS
        if (now - lastReconnectAttemptTime > RECONNECT_COOLDOWN_MS) {
            if (tryToggleWirelessDebugging()) {
                // Đợi Shizuku tự khởi động lại (tối đa 4 giây)
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < 4000L) {
                    delay(500)
                    try {
                        if (Shizuku.pingBinder()) {
                            updateStatus()
                            if (isReady()) {
                                android.util.Log.d("ShizukuManager", "✅ Shizuku đã READY sau toggle Wireless Debugging!")
                                cachedReadyResult = true
                                cachedReadyTime = System.currentTimeMillis()
                                return true
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
                android.util.Log.w("ShizukuManager", "⏳ Toggle Wireless Debugging xong nhưng Shizuku chưa sống lại sau 4s")
            }
        }

        // Shizuku chưa sẵn sàng - trả về false nhẹ nhàng, không kích hoạt ADB socket ngầm
        val wdStatus = if (isWirelessDebuggingEnabled()) "BẬT" else "TẮT"
        android.util.Log.d("ShizukuManager", "⏳ Shizuku chưa sẵn sàng. Wireless Debugging: $wdStatus")
        cachedReadyResult = false
        cachedReadyTime = System.currentTimeMillis()
        return false
    }

    // ========== Wireless Debugging Toggle (WRITE_SECURE_SETTINGS) ==========

    /**
     * Kiểm tra Wireless Debugging đang bật hay tắt.
     * Cần quyền READ access vào Settings.Global (thường app nào cũng có).
     */
    fun isWirelessDebuggingEnabled(): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, "adb_wifi_enabled", 0) == 1
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Kiểm tra app có quyền WRITE_SECURE_SETTINGS hay không.
     * Quyền này được grant bởi Shizuku khi READY và tồn tại vĩnh viễn.
     */
    fun hasWriteSecureSettings(): Boolean {
        return context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Bật các thiết lập ADB cần thiết: Tùy chọn nhà phát triển, Gỡ lỗi USB, Gỡ lỗi không dây.
     */
    fun enableWirelessDebugging(): Boolean {
        if (!hasWriteSecureSettings()) return false
        return try {
            val cr = context.contentResolver
            Settings.Global.putInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)
            Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
            Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
            android.util.Log.d("ShizukuManager", "✅ Đã bật development_settings_enabled, adb_enabled và adb_wifi_enabled")
            true
        } catch (e: Exception) {
            android.util.Log.e("ShizukuManager", "❌ Lỗi bật ADB/WD: ${e.message}")
            false
        }
    }

    /**
     * Toggle Wireless Debugging OFF → ON để kích Shizuku tự khởi động lại.
     * 
     * Điều kiện:
     * - Phải có quyền WRITE_SECURE_SETTINGS (đã được Shizuku grant lúc đầu)
     * - Wireless Debugging phải đang BẬT (nếu user chủ ý tắt thì ta không can thiệp)
     * - Cooldown 5 phút giữa các lần thử
     * 
     * Hoạt động cả khi khóa màn hình vì Settings.Global.putInt() là API hệ thống.
     * 
     * @return true nếu đã thực hiện toggle thành công, false nếu không thể
     */
    private suspend fun tryToggleWirelessDebugging(): Boolean {
        lastReconnectAttemptTime = System.currentTimeMillis()

        if (!hasWriteSecureSettings()) {
            android.util.Log.d("ShizukuManager", "❌ Không có quyền WRITE_SECURE_SETTINGS, không thể toggle")
            return false
        }

        if (!isWirelessDebuggingEnabled()) {
            android.util.Log.d("ShizukuManager", "❌ Wireless Debugging đang TẮT, không toggle (dùng forceRefresh để bật)")
            return false
        }

        android.util.Log.d("ShizukuManager", "🔄 Toggle Wireless Debugging OFF → ON để kích Shizuku restart...")
        try {
            // Tắt Wireless Debugging
            Settings.Global.putInt(context.contentResolver, "adb_wifi_enabled", 0)
            delay(1500) // Đợi hệ thống xử lý
            
            // Bật lại Wireless Debugging & các flags ADB
            val cr = context.contentResolver
            Settings.Global.putInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)
            Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 1)
            Settings.Global.putInt(cr, "adb_wifi_enabled", 1)
            android.util.Log.d("ShizukuManager", "✅ Đã toggle Wireless Debugging thành công")
            return true
        } catch (e: SecurityException) {
            android.util.Log.e("ShizukuManager", "❌ SecurityException khi toggle: ${e.message}")
            return false
        } catch (e: Exception) {
            android.util.Log.e("ShizukuManager", "❌ Lỗi khi toggle Wireless Debugging: ${e.message}")
            return false
        }
    }

    // ========== Permission Grants ==========

    /** Xóa cache — gọi khi binder thay đổi trạng thái */
    private fun invalidateCache() {
        cachedReadyResult = false
        cachedReadyTime = 0
    }

    private fun grantSystemPermissions() {
        // BUG #2 FIX: Chỉ chạy 1 lần mỗi session, tránh spam shell mỗi lần updateStatus()
        if (permissionsGranted) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val packageName = context.packageName
                val permsToGrant = mutableListOf<String>()

                // Chỉ grant quyền nào chưa có — tránh spam shell command mỗi lần Shizuku reconnect
                if (context.checkSelfPermission("android.permission.BATTERY_STATS") != PackageManager.PERMISSION_GRANTED) {
                    permsToGrant.add("android.permission.BATTERY_STATS")
                }
                if (context.checkSelfPermission("android.permission.DUMP") != PackageManager.PERMISSION_GRANTED) {
                    permsToGrant.add("android.permission.DUMP")
                }
                if (!hasWriteSecureSettings()) {
                    permsToGrant.add("android.permission.WRITE_SECURE_SETTINGS")
                }

                if (permsToGrant.isEmpty()) {
                    android.util.Log.d("ShizukuManager", "✅ Tất cả quyền đã được cấp, bỏ qua grant")
                    permissionsGranted = true
                    return@launch
                }

                for (perm in permsToGrant) {
                    val result = executeShizukuCommandWithTimeout(arrayOf("pm", "grant", packageName, perm), timeoutMs = 5000L)
                    if (!result.isSuccess) {
                        android.util.Log.w("ShizukuManager", "⚠️ Không thể cấp quyền $perm: ${result.stderr}")
                    }
                }
                android.util.Log.d("ShizukuManager", "✅ Đã grant ${permsToGrant.size} quyền: ${permsToGrant.joinToString { it.substringAfterLast('.') }}")
                permissionsGranted = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onDestroy() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
        } catch (e: Exception) {
            // Ignore
        }
    }
}

fun shizukuNewProcess(cmd: Array<String>, env: Array<String>? = null, dir: String? = null): java.lang.Process {
    val clazz = Class.forName("rikka.shizuku.Shizuku")
    val method = clazz.getDeclaredMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
    method.isAccessible = true
    return method.invoke(null, cmd, env, dir) as java.lang.Process
}
