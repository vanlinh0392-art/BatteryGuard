package com.pin.batteryguard.domain.shield

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.pin.batteryguard.data.db.dao.AppShieldSnapshotDao
import com.pin.batteryguard.data.db.dao.ShieldedAppDao
import com.pin.batteryguard.data.db.entity.AppShieldSnapshotEntity
import com.pin.batteryguard.data.db.entity.ShieldedAppEntity
import com.pin.batteryguard.data.preferences.SettingsDataStore
import com.pin.batteryguard.domain.model.AppShieldConfig
import com.pin.batteryguard.service.shield.AppShieldAccessibilityService
import com.pin.batteryguard.service.shield.AppShieldWatchdogReceiver
import com.pin.batteryguard.shizuku.ForceStopManager
import com.pin.batteryguard.shizuku.ShizukuAutoStarter
import com.pin.batteryguard.util.NotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AppShieldManager"

@Singleton
class AppShieldManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val snapshotDao: AppShieldSnapshotDao,
    private val shieldedAppDao: ShieldedAppDao,
    private val settingsDataStore: SettingsDataStore,
    private val shizukuAutoStarter: ShizukuAutoStarter,
    private val forceStopManager: ForceStopManager,
    private val shizukuManager: com.pin.batteryguard.shizuku.ShizukuManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    // Fast-path In-Memory cache cho danh sách ứng dụng người dùng CHỌN THÊM (O(1) < 0.05ms)
    private val _cachedWatchedPackages = MutableStateFlow<Set<String>>(emptySet())
    val cachedWatchedPackages = _cachedWatchedPackages.asStateFlow()

    // RAM Cache cho các package ngân hàng / tài chính đã được TỰ ĐỘNG PHÁT HIỆN
    private val _autoDetectedBankPackages = ConcurrentHashMap.newKeySet<String>()

    // RAM Cache cho cấu hình AppShield — Đọc tức thì 0ms, không đợi DataStore Disk I/O
    @Volatile
    private var cachedConfig: AppShieldConfig = AppShieldConfig(isEnabled = true)

    // Cờ trạng thái tự động nhận diện từ cấu hình
    @Volatile
    private var isAutoDetectEnabled: Boolean = true

    // Cooldown map chống lặp vô tận khi relaunch app
    private val recentTriggerTimestamps = ConcurrentHashMap<String, Long>()

    // C4: Negative cache cho isSensitiveSecurityPackage — tránh IPC/PM spam
    private val packageCheckCache = ConcurrentHashMap<String, Boolean>()

    val isCurrentlyHiddenFlow: Flow<Boolean> = snapshotDao.getSnapshotFlow().map { it?.isCurrentlyHidden == true }

    val currentSnapshotFlow = snapshotDao.getSnapshotFlow()

    init {
        // Đồng bộ danh sách app người dùng chọn thêm vào RAM
        scope.launch {
            shieldedAppDao.getEnabledPackagesFlow().collect { packages ->
                _cachedWatchedPackages.value = packages.toSet()
                Log.d(TAG, "Đã cập nhật RAM cache: ${packages.size} apps chọn thêm được bảo vệ.")
            }
        }

        // Lắng nghe cấu hình để cập nhật RAM cache cho AppShield
        scope.launch {
            settingsDataStore.shieldConfigFlow.collectLatest { config ->
                cachedConfig = config
                isAutoDetectEnabled = config.autoDetectBanks
            }
        }

        // Quét nạp trước các app ngân hàng/ví điện tử đang cài trên thiết bị vào cache
        scope.launch {
            preloadInstalledBankingApps()
            populatePresetBanksIfEmpty()
        }
    }

    /**
     * Kiểm tra xem một ứng dụng có cần được bảo vệ hay không.
     * Kết hợp 2 lớp độc lập:
     * 1. Ứng dụng người dùng CHỌN THÊM trong Whitelist (DB)
     * 2. Cơ chế TỰ ĐỘNG PHÁT HIỆN ứng dụng Ngân hàng / Ví điện tử / Tài chính
     */
    fun shouldShieldApp(packageName: String): Boolean {
        if (packageName == context.packageName || packageName.isBlank()) return false

        // Lớp 1: Nằm trong danh sách chọn thêm của người dùng
        if (_cachedWatchedPackages.value.contains(packageName)) {
            return true
        }

        // Lớp 2: Cơ chế tự động phát hiện ngân hàng/ví điện tử
        if (isAutoDetectEnabled && isSensitiveSecurityPackage(packageName)) {
            return true
        }

        return false
    }

    /**
     * Nhận diện thông minh ứng dụng ngân hàng, ví điện tử hoặc dịch vụ tài chính.
     * C4: Dùng packageCheckCache để tránh lặp IPC/PM lookup cho app đã biết.
     * M5: Các danh sách cố định đã chuyển sang companion object constants.
     */
    fun isSensitiveSecurityPackage(packageName: String): Boolean {
        // C4: Check cache trước (bao gồm cả negative cache)
        packageCheckCache[packageName]?.let { return it }

        if (_autoDetectedBankPackages.contains(packageName)) {
            packageCheckCache[packageName] = true
            return true
        }

        val lowerPkg = packageName.lowercase()
        val segments = lowerPkg.split(".")

        // M5: Pattern ngắn match segment chính xác (companion object constant)
        if (segments.any { it in BANK_SEGMENT_KEYWORDS }) {
            _autoDetectedBankPackages.add(packageName)
            packageCheckCache[packageName] = true
            return true
        }

        // M5: Pattern dài: đủ specific, dùng contains an toàn (companion object constant)
        if (BANK_CONTAINS_PATTERNS.any { lowerPkg.contains(it) }) {
            _autoDetectedBankPackages.add(packageName)
            packageCheckCache[packageName] = true
            return true
        }

        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(appInfo).toString().lowercase()
            // M5: Label keywords (companion object constant)
            if (SENSITIVE_APP_LABELS.any { label.contains(it) }) {
                _autoDetectedBankPackages.add(packageName)
                packageCheckCache[packageName] = true
                return true
            }
        } catch (_: Exception) { }

        // C4: Lưu negative cache — package này KHÔNG phải app nhạy cảm
        packageCheckCache[packageName] = false
        return false
    }

    companion object {
        // M5: Các danh sách cố định dùng trong isSensitiveSecurityPackage — chỉ khởi tạo 1 lần

        /** Segment chính xác trong package name (match từng phần sau dấu '.') */
        private val BANK_SEGMENT_KEYWORDS = setOf(
            // Ngân hàng
            "vcb", "acb", "scb", "vib", "msb", "shb", "ocb", "tpb",
            "bidv", "ipay", "momo", "timo", "ssi", "vps", "tcbs", "uob",
            "bank", "banking", "mbank", "ebanking",
            // Chính phủ / Định danh
            "vnid", "vssid", "etax", "bhxh"
        )

        /** Substring pattern dài — đủ specific, dùng contains an toàn */
        private val BANK_CONTAINS_PATTERNS = listOf(
            // Ngân hàng
            "vietcombank", "digibank", "techcombank", "mbmobile", "mbbank",
            "vietinbank", "agribank", "vpbank", "tpbank", "sacombank",
            "shbmobile", "msbmobile", "hdbank", "ocbomni", "scbmobile",
            "myvib", "kienlongbank", "kienlong", "seabank", "seamobile",
            "bacabank", "pvcombank", "baovietbank", "dongabank",
            "lienviet", "lpbank", "namabank", "shinhan", "shinhanglobal",
            "wooribank", "hsbc", "standardchartered", "publicbank", "kbank",
            // Ví điện tử
            "zalopay", "vtpay", "viettelmoney", "viettelpay", "vnptmoney",
            "shopeepay", "airpay", "vnpay", "payoo",
            "finhay", "tikop", "topi", "infina",
            // Chứng khoán
            "vndirect", "entrade", "smartbanking",
            // Chính phủ / Dịch vụ công
            "dancuquocgia", "dichvucong", "baohiemxahoi", "vneid"
        )

        /** Keywords trong app label (tên hiển thị) */
        private val SENSITIVE_APP_LABELS = listOf(
            "ngân hàng", "bank", "ví điện tử", "chứng khoán",
            "smartbanking", "digibank", "ipay", "finance",
            "định danh", "dịch vụ công", "bảo hiểm xã hội", "vneid", "vssid"
        )
    }

    /** Quét trước các app ngân hàng/ví điện tử đang cài đặt trên máy */
    private fun preloadInstalledBankingApps() {
        try {
            val pm = context.packageManager
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in installed) {
                if (app.packageName == context.packageName) continue
                if (isSensitiveSecurityPackage(app.packageName)) {
                    _autoDetectedBankPackages.add(app.packageName)
                }
            }
            Log.d(TAG, "Đã quét và phát hiện ${_autoDetectedBankPackages.size} app ngân hàng/ví điện tử trên máy.")
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi khi quét app ngân hàng cài đặt: ${e.message}")
        }
    }

    /** Kiểm tra quyền WRITE_SECURE_SETTINGS */
    fun hasWriteSecureSettings(): Boolean {
        return context.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS") == PackageManager.PERMISSION_GRANTED
    }

    /** Kiểm tra xem cài đặt trên phần cứng máy thực tế đã đang bị ẩn chưa */
    fun isCurrentlySettingsHidden(): Boolean {
        val cr = context.contentResolver
        val dev = try { Settings.Global.getInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) } catch (_: Exception) { 0 }
        val wd = try { Settings.Global.getInt(cr, "adb_wifi_enabled", 0) } catch (_: Exception) { 0 }
        return dev == 0 && wd == 0
    }

    /**
     * Kích hoạt ẩn Developer Options / ADB khi phát hiện mở app ngân hàng.
     * Kiến trúc Two-Phase:
     * - Phase 1: Zero-Latency Fast-Path (< 1.5ms, In-Memory):
     *     + Chặn đứng Shizuku auto-revive & auto-enable wireless debugging
     *     + Ghi tắt NGAY LẬP TỨC: DEVELOPMENT_SETTINGS_ENABLED = 0, ADB_ENABLED = 0, adb_wifi_enabled = 0 (Global + Secure + OEM)
     *     + Nếu config.relaunchApp: fastForceStop + relaunch app sạch sẽ
     * - Phase 2: Async Persistence:
     *     + Lưu snapshot trạng thái vào Room DB bất đồng bộ
     *     + Lập lịch AlarmManager Hardware Watchdog
     *     + Hiển thị Ongoing Notification với nút Khôi phục
     */
    suspend fun hideSettingsForApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!hasWriteSecureSettings()) {
            Log.w(TAG, "Không thể ẩn cài đặt: Chưa được cấp quyền WRITE_SECURE_SETTINGS")
            return@withContext false
        }

        val config = cachedConfig
        // Nếu cả master switch và autoDetectBanks đều tắt thì bỏ qua
        if (!config.isEnabled && !config.autoDetectBanks) return@withContext false

        val cr = context.contentResolver
        val now = SystemClock.elapsedRealtime()
        val lastTrigger = recentTriggerTimestamps[packageName] ?: 0L

        // Reality Check: Nếu settings thực sự đã ẩn và trong cooldown 2s -> bỏ qua
        if (now - lastTrigger < 2_000L && isCurrentlySettingsHidden()) {
            Log.d(TAG, "Bỏ qua trigger trùng lặp cho $packageName (cài đặt thực tế đã ẩn)")
            return@withContext true
        }
        recentTriggerTimestamps[packageName] = now
        recentTriggerTimestamps.entries.removeIf { now - it.value > 60_000L }

        // ==========================================
        // ⚡ PHASE 1: ZERO-LATENCY FAST-PATH (< 1.5ms)
        // ==========================================
        // 1. Chặn đứng Shizuku auto-revive & auto-enable wireless debugging
        shizukuManager.isShieldActive = true
        shizukuManager.cancelAutoRevive()

        // 2. Chụp trạng thái thực tế trước khi ghi đè để lưu snapshot chính xác
        val devEnabled = try {
            Settings.Global.getInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)
        } catch (_: Exception) { 1 }

        val adbEnabled = try {
            Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 1)
        } catch (_: Exception) { 1 }

        val adbWifiEnabled = try {
            Settings.Global.getInt(cr, "adb_wifi_enabled", 1)
        } catch (_: Exception) { 1 }

        val wasShizukuRunning = try {
            Shizuku.pingBinder()
        } catch (_: Exception) { false }

        // 3. Tắt cài đặt hệ thống NGAY LẬP TỨC
        try {
            if (config.hideDevOptions) {
                Settings.Global.putInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
                try { Settings.Secure.putInt(cr, "development_settings_enabled", 0) } catch (_: Exception) {}
            }
            if (config.hideAdb) {
                Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 0)
                try { Settings.Secure.putInt(cr, "adb_enabled", 0) } catch (_: Exception) {}
            }
            if (config.hideWirelessAdb) {
                Settings.Global.putInt(cr, "adb_wifi_enabled", 0)
            }
            // Xiaomi / HyperOS specific defense
            try {
                Settings.Secure.putInt(cr, "security_adb_install_enable", 0)
                Settings.Secure.putInt(cr, "adb_security_enabled", 0)
            } catch (_: Exception) {}
            Log.i(TAG, "⚡ [Fast-Path] Đã tắt Developer Options & Wireless ADB cho $packageName")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi ghi Secure Settings: ${e.message}", e)
            return@withContext false
        }

        // 4. Fast Kill & Clean Relaunch (nếu cấu hình yêu cầu)
        if (config.relaunchApp) {
            // Dùng fastForceStop (~15ms, không delay, không dumpsys)
            forceStopManager.fastForceStop(packageName, 0)
            try {
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
                am?.killBackgroundProcesses(packageName)
            } catch (_: Exception) {}

            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(launchIntent)
            }
        }

        // ==========================================
        // 💾 PHASE 2: ASYNC PERSISTENCE & WATCHDOG
        // ==========================================
        scope.launch {
            mutex.withLock {
                val existingSnapshot = snapshotDao.getSnapshot()
                if (existingSnapshot?.isCurrentlyHidden == true) {
                    scheduleWatchdogAlarm(config.autoRevertMinutes)
                    return@withLock
                }

                val accStr = try {
                    Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
                } catch (_: Exception) { "" }

                val accEnabled = try {
                    Settings.Secure.getInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                } catch (_: Exception) { 1 }

                if (config.hideAccessibility && accStr.isNotBlank()) {
                    try {
                        Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "")
                    } catch (e: Exception) {
                        Log.w(TAG, "Không thể ẩn Accessibility Services: ${e.message}")
                    }
                }

                val snapshot = AppShieldSnapshotEntity(
                    id = 1,
                    isCurrentlyHidden = true,
                    hideTimestamp = System.currentTimeMillis(),
                    timeoutMinutes = config.autoRevertMinutes,
                    triggeredPackage = packageName,
                    originalDevOptionsEnabled = if (devEnabled == 0) 1 else devEnabled,
                    originalAdbEnabled = if (adbEnabled == 0) 1 else adbEnabled,
                    originalAdbWifiEnabled = if (adbWifiEnabled == 0) 1 else adbWifiEnabled,
                    originalAccessibilityEnabled = accEnabled,
                    originalAccessibilityServices = accStr,
                    wasShizukuRunning = wasShizukuRunning
                )
                snapshotDao.saveSnapshot(snapshot)

                scheduleWatchdogAlarm(config.autoRevertMinutes)

                val revertIntent = Intent(context, AppShieldWatchdogReceiver::class.java).apply {
                    action = AppShieldWatchdogReceiver.ACTION_MANUAL_REVERT
                }
                val pendingRevert = PendingIntent.getBroadcast(
                    context, 101, revertIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                val appLabel = try {
                    val info = context.packageManager.getApplicationInfo(packageName, 0)
                    context.packageManager.getApplicationLabel(info).toString()
                } catch (_: Exception) { packageName }

                NotificationHelper.showAppShieldOngoingNotification(
                    context,
                    appLabel,
                    config.autoRevertMinutes,
                    pendingRevert
                )
            }
        }

        true
    }

    /**
     * Khôi phục cài đặt hệ thống về trạng thái ban đầu và tự động bật lại Shizuku.
     */
    suspend fun restoreSettings(reason: String = "Manual"): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            // Đánh dấu tắt khiên bảo vệ để cho phép Shizuku hoạt động lại
            shizukuManager.isShieldActive = false
            val snapshot = snapshotDao.getSnapshot()
            if (snapshot == null || !snapshot.isCurrentlyHidden) {
                Log.d(TAG, "Không có phiên ẩn nào cần khôi phục.")
                return@withLock false
            }

            Log.i(TAG, "Bắt đầu khôi phục cài đặt. Lý do: $reason")
            val config = settingsDataStore.shieldConfigFlow.first()
            val cr = context.contentResolver

            // 1. Hủy Alarm Watchdog & Notification
            cancelWatchdogAlarm()
            NotificationHelper.cancelAppShieldOngoingNotification(context)

            // 2. Khôi phục Settings Global & Secure
            var restoreSuccess = false
            try {
                Settings.Global.putInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, snapshot.originalDevOptionsEnabled)
                Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, snapshot.originalAdbEnabled)
                Settings.Global.putInt(cr, "adb_wifi_enabled", snapshot.originalAdbWifiEnabled)

                // Khôi phục chính xác 100% các dịch vụ trợ năng active trước đó
                val ourAccessibilityComponent = ComponentName(context, AppShieldAccessibilityService::class.java).flattenToString()
                val originalServices = snapshot.originalAccessibilityServices
                    .split(":")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toMutableSet()

                // Luôn bảo toàn và khôi phục trọn vẹn danh sách trợ năng active trước đó + dịch vụ của BatteryGuard
                originalServices.add(ourAccessibilityComponent)

                val restoredServicesString = originalServices.joinToString(":")
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, restoredServicesString)
                Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, if (restoredServicesString.isNotBlank()) 1 else 0)
                Log.i(TAG, "✅ Đã khôi phục chính xác ${originalServices.size} dịch vụ trợ năng active trước đó: $restoredServicesString")
                Log.i(TAG, "✅ Đã khôi phục cài đặt Developer Options & ADB về giá trị gốc.")
                restoreSuccess = true
            } catch (e: SecurityException) {
                Log.e(TAG, "Mất quyền WRITE_SECURE_SETTINGS, giữ snapshot để thử lại sau.", e)
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi khôi phục Secure Settings: ${e.message}", e)
            }

            // BUG #5 fix: Chỉ đánh dấu đã khôi phục khi ghi settings thực sự thành công
            if (!restoreSuccess) {
                Log.w(TAG, "Không đánh dấu khôi phục vì ghi settings thất bại. Sẽ thử lại lần sau.")
                return@withLock false
            }
            snapshotDao.setHidden(false)

            // 3. TỰ ĐỘNG BẬT LẠI SHIZUKU (Key Synergy!)
            var revivedShizuku = false
            if (config.autoRestartShizuku && snapshot.wasShizukuRunning) {
                Log.i(TAG, "Tự động kích hoạt lại Shizuku qua ShizukuAutoStarter...")
                try {
                    // Chờ ADB daemon bind port sau khi bật lại Dev Mode và ADB
                    delay(3000)
                    revivedShizuku = shizukuAutoStarter.startShizukuService(notifyOnSuccess = false)
                    Log.i(TAG, "Kết quả hồi sinh Shizuku: $revivedShizuku")
                } catch (e: Exception) {
                    Log.w(TAG, "Không thể tự khởi động lại Shizuku: ${e.message}")
                }
            }

            // 4. Phát thông báo hoàn tất
            NotificationHelper.showAppShieldRevertedNotification(context, revivedShizuku)

            true
        }
    }

    /** Lập lịch AlarmManager thức giấc chính xác sau số phút quy định */
    private fun scheduleWatchdogAlarm(minutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AppShieldWatchdogReceiver::class.java).apply {
            action = AppShieldWatchdogReceiver.ACTION_REVERT_TIMEOUT
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 102, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val triggerAtMillis = SystemClock.elapsedRealtime() + (minutes * 60 * 1000L)
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
            Log.d(TAG, "Đã lập lịch AlarmManager khôi phục sau $minutes phút.")
        } catch (e: Exception) {
            Log.w(TAG, "Không thể đặt exact alarm: ${e.message}")
        }
    }

    /** Hủy AlarmManager khi đã khôi phục */
    private fun cancelWatchdogAlarm() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AppShieldWatchdogReceiver::class.java).apply {
            action = AppShieldWatchdogReceiver.ACTION_REVERT_TIMEOUT
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 102, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    /** Tự động nạp preset các ngân hàng Việt Nam nếu danh sách còn trống */
    suspend fun populatePresetBanksIfEmpty() {
        if (shieldedAppDao.count() > 0) return

        val presetSignatures = listOf(
            "com.VCB" to "VCB Digibank",
            "vn.com.techcombank.bb.app" to "Techcombank",
            "com.vibe.techcombank" to "Techcombank Mobile",
            "com.mbmobile" to "MB Bank",
            "com.babor.bidv" to "BIDV SmartBanking",
            "com.vnpay.bidv" to "BIDV SmartBanking",
            "com.vietinbank.ipay" to "VietinBank iPay",
            "com.vnpay.agribank" to "Agribank E-Mobile",
            "com.vnpay.vpbankonline" to "VPBank NEO",
            "com.tpb.mb.gprsandroid" to "TPBank Mobile",
            "mobile.acb.com.vn" to "ACB ONE",
            "com.vnpay.sacombank" to "Sacombank mBanking",
            "vn.com.momo" to "MoMo",
            "com.zing.zalo.pay" to "ZaloPay",
            "com.bplus.vtpay" to "Viettel Money",
            "com.cake.bank" to "Cake by VPBank",
            "vn.timo.banking" to "Timo Digital Bank"
        )

        val pm = context.packageManager
        val appsToInsert = mutableListOf<ShieldedAppEntity>()

        for ((pkg, defaultName) in presetSignatures) {
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(info).toString().ifBlank { defaultName }
                appsToInsert.add(
                    ShieldedAppEntity(
                        packageName = pkg,
                        appName = label,
                        isEnabled = true,
                        isPresetBank = true
                    )
                )
            } catch (_: PackageManager.NameNotFoundException) {
                // Chưa cài đặt thì bỏ qua
            }
        }

        if (appsToInsert.isNotEmpty()) {
            shieldedAppDao.insertAll(appsToInsert)
            Log.i(TAG, "Đã tự động nhận diện và thêm ${appsToInsert.size} app ngân hàng Việt Nam vào App Shield.")
        }
    }
}
