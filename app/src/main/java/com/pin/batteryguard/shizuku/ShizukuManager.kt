package com.pin.batteryguard.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
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

    // ========== Auto-recovery ==========
    private val recoveryScope = CoroutineScope(Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    /** Tối đa 3 lần tự hồi sinh liên tiếp trước khi dừng (reset khi binder sống lại) */
    private val MAX_AUTO_REVIVE = 3
    @Volatile private var autoReviveCount = 0

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

    /**
     * Cờ bảo vệ: Khi AppShield đang kích hoạt (ẩn Developer Options/ADB để bảo vệ app ngân hàng),
     * TUYỆT ĐỐI KHÔNG tự động bật lại Wireless Debugging hoặc tự hồi sinh Shizuku.
     */
    @Volatile var isShieldActive: Boolean = false

    private var autoReviveJob: kotlinx.coroutines.Job? = null

    fun cancelAutoRevive() {
        autoReviveJob?.cancel()
        autoReviveJob = null
        autoReviveCount = 0
        android.util.Log.i("ShizukuManager", "🛡️ Đã hủy toàn bộ tác vụ Shizuku auto-revive do AppShield đang kích hoạt.")
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        android.util.Log.d("ShizukuManager", "🔗 Binder received! Shizuku đã kết nối lại.")
        autoReviveCount = 0  // Reset bộ đếm khi binder sống lại thành công
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
        // Nếu AppShield đang kích hoạt bảo vệ app ngân hàng, không hiện thông báo và không tự bật lại WD
        if (isShieldActive) {
            android.util.Log.i("ShizukuManager", "🛡️ AppShield đang bảo vệ ngân hàng — bỏ qua thông báo mất kết nối và dừng auto-revive.")
            return@OnBinderDeadListener
        }
        // Push thông báo mất kết nối 1 lần
        com.pin.batteryguard.util.NotificationHelper.showShizukuDisconnected(context)
        // Auto-recovery: tự hồi sinh Shizuku sau 8 giây (đủ để HyperOS settle)
        scheduleAutoRevive()
    }

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        android.util.Log.d("ShizukuManager", "🔑 Shizuku RequestPermissionResult: code=$requestCode, result=$grantResult")
        invalidateCache()
        permissionsGranted = false // Reset để cho phép trigger auto-grant ngay khi được cấp quyền
        updateStatus()
    }

    init {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
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
        if (isShieldActive) {
            android.util.Log.w("ShizukuManager", "🛡️ Bỏ qua forceRefresh vì AppShield đang bảo vệ ứng dụng ngân hàng.")
            return false
        }
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
        val now = System.currentTimeMillis()

        // Tầng 1: Cache hit trong 30 giây (0ms, hoàn toàn không gọi Binder IPC)
        if (now - cachedReadyTime < CACHE_TTL_MS) {
            return cachedReadyResult
        }

        // Tầng 2: Fast-path nếu status trong RAM là READY và ping sống
        if (isReady()) {
            cachedReadyResult = true
            cachedReadyTime = now
            return true
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

        // Nếu AppShield đang bảo vệ ngân hàng, TUYỆT ĐỐI không tự bật Wireless Debugging
        if (isShieldActive) {
            return isReady()
        }

        // Thử kích hoạt lại Shizuku qua toggle Wireless Debugging
        // Chỉ thử nếu ngoài cooldown và có quyền WRITE_SECURE_SETTINGS
        if (now - lastReconnectAttemptTime > RECONNECT_COOLDOWN_MS) {
            // Fix: Tự bật WD khi đang TẮT (trước đây chỉ toggle khi đang BẬT)
            if (!isWirelessDebuggingEnabled() && hasWriteSecureSettings()) {
                lastReconnectAttemptTime = System.currentTimeMillis()
                android.util.Log.d("ShizukuManager", "📡 WD đang TẮT → tự bật WD + khởi động Shizuku...")
                enableWirelessDebugging()
                delay(2000)
                autoStarter.startShizukuService(notifyOnSuccess = true, isManual = false)
                delay(3000)
                try {
                    if (Shizuku.pingBinder()) {
                        updateStatus()
                        if (isReady()) {
                            android.util.Log.d("ShizukuManager", "✅ Shizuku đã READY sau auto-enable WD!")
                            cachedReadyResult = true
                            cachedReadyTime = System.currentTimeMillis()
                            return true
                        }
                    }
                } catch (_: Exception) {}
                android.util.Log.w("ShizukuManager", "⏳ Auto-enable WD xong nhưng Shizuku chưa sống lại")
            } else if (tryToggleWirelessDebugging()) {
                // WD đang BẬT nhưng Shizuku chết → toggle OFF→ON
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
        if (isShieldActive) {
            android.util.Log.w("ShizukuManager", "🛡️ Chặn bật Wireless Debugging vì AppShield đang bảo vệ ứng dụng ngân hàng!")
            return false
        }
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
        if (isShieldActive) {
            android.util.Log.w("ShizukuManager", "🛡️ Chặn toggle Wireless Debugging vì AppShield đang hoạt động!")
            return false
        }
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

    fun grantSystemPermissions(onCompleted: ((Boolean) -> Unit)? = null) {
        val hasSecure = hasWriteSecureSettings()
        val hasStats = context.checkSelfPermission("android.permission.BATTERY_STATS") == PackageManager.PERMISSION_GRANTED
        val hasDump = context.checkSelfPermission("android.permission.DUMP") == PackageManager.PERMISSION_GRANTED

        if (hasSecure && hasStats && hasDump) {
            permissionsGranted = true
            onCompleted?.invoke(true)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val packageName = context.packageName
                val permsToGrant = mutableListOf<String>()

                // Chỉ grant quyền nào chưa có
                if (!hasStats) {
                    permsToGrant.add("android.permission.BATTERY_STATS")
                }
                if (!hasDump) {
                    permsToGrant.add("android.permission.DUMP")
                }
                if (!hasSecure) {
                    permsToGrant.add("android.permission.WRITE_SECURE_SETTINGS")
                }

                if (permsToGrant.isEmpty()) {
                    android.util.Log.d("ShizukuManager", "✅ Tất cả quyền đã được cấp, bỏ qua grant")
                    permissionsGranted = true
                    onCompleted?.invoke(true)
                    return@launch
                }

                var successCount = 0
                for (perm in permsToGrant) {
                    val result = executeShizukuCommandWithTimeout(arrayOf("pm", "grant", packageName, perm), timeoutMs = 5000L)
                    if (result.isSuccess) {
                        successCount++
                    } else {
                        android.util.Log.w("ShizukuManager", "⚠️ Không thể cấp quyền $perm: ${result.stderr}")
                    }
                }

                val secureGranted = hasWriteSecureSettings()
                android.util.Log.d("ShizukuManager", "✅ Đã grant qua Shizuku. WRITE_SECURE_SETTINGS = $secureGranted")
                if (secureGranted) {
                    permissionsGranted = true
                }
                onCompleted?.invoke(secureGranted)
            } catch (e: Exception) {
                android.util.Log.e("ShizukuManager", "❌ Lỗi khi tự động cấp quyền qua Shizuku: ${e.message}")
                onCompleted?.invoke(false)
            }
        }
    }

    /**
     * Tự hồi sinh Shizuku khi binder chết.
     * Chờ 8s (HyperOS settle) rồi gọi startShizukuService.
     * Tối đa 3 lần liên tiếp. Reset khi binder sống lại.
     */
    private fun scheduleAutoRevive() {
        if (isShieldActive) {
            android.util.Log.i("ShizukuManager", "🛡️ AppShield đang bảo vệ ngân hàng — bỏ qua scheduleAutoRevive.")
            return
        }
        if (autoReviveCount >= MAX_AUTO_REVIVE) {
            android.util.Log.w("ShizukuManager", "⏹️ Đã thử hồi sinh $MAX_AUTO_REVIVE lần, dừng. User cần bấm Reload.")
            return
        }
        autoReviveCount++
        android.util.Log.i("ShizukuManager", "🔄 Auto-recovery $autoReviveCount/$MAX_AUTO_REVIVE — chờ 8s...")
        autoReviveJob?.cancel()
        autoReviveJob = recoveryScope.launch {
            delay(8000)
            if (isShieldActive) {
                android.util.Log.i("ShizukuManager", "🛡️ AppShield đang hoạt động — hủy auto-recovery sau delay.")
                return@launch
            }
            try {
                if (Shizuku.pingBinder()) {
                    android.util.Log.i("ShizukuManager", "✅ Shizuku đã tự sống lại, hủy auto-recovery.")
                    autoReviveCount = 0
                    return@launch
                }
            } catch (_: Exception) {}

            if (!isWirelessDebuggingEnabled() && hasWriteSecureSettings()) {
                android.util.Log.d("ShizukuManager", "📡 Auto-recovery: WD TẮT → tự bật")
                enableWirelessDebugging()
                delay(2000)
            }

            val revived = autoStarter.startShizukuService(notifyOnSuccess = true, isManual = false)
            if (revived) {
                android.util.Log.i("ShizukuManager", "✅ Auto-recovery thành công!")
                autoReviveCount = 0
            } else {
                android.util.Log.w("ShizukuManager", "⚠️ Auto-recovery lần $autoReviveCount thất bại.")
            }
        }
    }

    fun onDestroy() {
        try {
            cancelAutoRevive()
            recoveryScope.cancel()
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
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
