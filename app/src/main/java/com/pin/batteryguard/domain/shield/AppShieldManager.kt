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
    private val forceStopManager: ForceStopManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    // Fast-path In-Memory cache cho danh sách ứng dụng người dùng CHỌN THÊM (O(1) < 0.05ms)
    private val _cachedWatchedPackages = MutableStateFlow<Set<String>>(emptySet())
    val cachedWatchedPackages = _cachedWatchedPackages.asStateFlow()

    // RAM Cache cho các package ngân hàng / tài chính đã được TỰ ĐỘNG PHÁT HIỆN
    private val _autoDetectedBankPackages = ConcurrentHashMap.newKeySet<String>()

    // Cờ trạng thái tự động nhận diện từ cấu hình
    @Volatile
    private var isAutoDetectEnabled: Boolean = true

    // Cooldown map chống lặp vô tận khi relaunch app
    private val recentTriggerTimestamps = ConcurrentHashMap<String, Long>()

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

        // Lắng nghe cấu hình để cập nhật cờ Auto-detect ngân hàng
        scope.launch {
            settingsDataStore.shieldConfigFlow.collectLatest { config ->
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
        if (isAutoDetectEnabled && isBankingOrFinancePackage(packageName)) {
            return true
        }

        return false
    }

    /**
     * Nhận diện thông minh ứng dụng ngân hàng, ví điện tử hoặc dịch vụ tài chính.
     */
    fun isBankingOrFinancePackage(packageName: String): Boolean {
        if (_autoDetectedBankPackages.contains(packageName)) return true

        val lowerPkg = packageName.lowercase()
        val segments = lowerPkg.split(".")

        // BUG #4 fix: Pattern ngắn (≤4 ký tự) match segment chính xác để tránh false positive
        val segmentExactPatterns = setOf(
            "vcb", "acb", "scb", "vib", "msb", "shb", "ocb", "tpb",
            "bidv", "ipay", "momo", "timo", "ssi", "vps", "tcbs", "uob",
            "bank", "banking", "mbank", "ebanking"
        )
        if (segments.any { it in segmentExactPatterns }) {
            _autoDetectedBankPackages.add(packageName)
            return true
        }

        // Pattern dài (≥5 ký tự): đủ specific, dùng contains an toàn
        val containsPatterns = listOf(
            "vietcombank", "digibank", "techcombank", "mbmobile", "mbbank",
            "vietinbank", "agribank", "vpbank", "tpbank", "sacombank",
            "shbmobile", "msbmobile", "hdbank", "ocbomni", "scbmobile",
            "myvib", "kienlongbank", "kienlong", "seabank", "seamobile",
            "bacabank", "pvcombank", "baovietbank", "dongabank",
            "lienviet", "lpbank", "namabank", "shinhan", "shinhanglobal",
            "wooribank", "hsbc", "standardchartered", "publicbank", "kbank",
            "zalopay", "vtpay", "viettelmoney", "viettelpay", "vnptmoney",
            "shopeepay", "airpay", "vnpay", "payoo",
            "finhay", "tikop", "topi", "infina",
            "vndirect", "entrade", "smartbanking"
        )
        if (containsPatterns.any { lowerPkg.contains(it) }) {
            _autoDetectedBankPackages.add(packageName)
            return true
        }

        try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val label = pm.getApplicationLabel(appInfo).toString().lowercase()
            val labelKeywords = listOf(
                "ngân hàng", "bank", "ví điện tử", "chứng khoán",
                "smartbanking", "digibank", "ipay", "finance"
            )
            if (labelKeywords.any { label.contains(it) }) {
                _autoDetectedBankPackages.add(packageName)
                return true
            }
        } catch (_: Exception) { }

        return false
    }

    /** Quét trước các app ngân hàng/ví điện tử đang cài đặt trên máy */
    private fun preloadInstalledBankingApps() {
        try {
            val pm = context.packageManager
            val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in installed) {
                if (app.packageName == context.packageName) continue
                if (isBankingOrFinancePackage(app.packageName)) {
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

    /**
     * Kích hoạt ẩn Developer Options / ADB khi phát hiện mở app ngân hàng.
     */
    suspend fun hideSettingsForApp(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!hasWriteSecureSettings()) {
            Log.w(TAG, "Không thể ẩn cài đặt: Chưa được cấp quyền WRITE_SECURE_SETTINGS")
            return@withContext false
        }

        val config = settingsDataStore.shieldConfigFlow.first()
        if (!config.isEnabled) return@withContext false

        mutex.withLock {
            // Cooldown check trong mutex để tránh race condition (BUG #3 fix)
            val now = SystemClock.elapsedRealtime()
            val lastTrigger = recentTriggerTimestamps[packageName] ?: 0L
            if (now - lastTrigger < 10_000L) {
                Log.d(TAG, "Bỏ qua trigger trùng lặp cho $packageName (cooldown)")
                return@withLock true
            }
            recentTriggerTimestamps[packageName] = now
            // Dọn entries cũ > 1 phút (BUG #7 fix)
            recentTriggerTimestamps.entries.removeIf { now - it.value > 60_000L }

            val existingSnapshot = snapshotDao.getSnapshot()
            if (existingSnapshot?.isCurrentlyHidden == true) {
                Log.i(TAG, "Cài đặt đã đang ở trạng thái ẩn. Gia hạn bộ đếm hẹn giờ.")
                scheduleWatchdogAlarm(config.autoRevertMinutes)
                return@withLock true
            }

            val cr = context.contentResolver

            // 1. Chụp Snapshot trạng thái gốc
            val devEnabled = try {
                Settings.Global.getInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 1)
            } catch (_: Exception) { 1 }

            val adbEnabled = try {
                Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 1)
            } catch (_: Exception) { 1 }

            val adbWifiEnabled = try {
                Settings.Global.getInt(cr, "adb_wifi_enabled", 1)
            } catch (_: Exception) { 1 }

            val accStr = try {
                Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
            } catch (_: Exception) { "" }

            val accEnabled = try {
                Settings.Secure.getInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            } catch (_: Exception) { 1 }

            val wasShizukuRunning = try {
                Shizuku.pingBinder()
            } catch (_: Exception) { false }

            val snapshot = AppShieldSnapshotEntity(
                id = 1,
                isCurrentlyHidden = true,
                hideTimestamp = System.currentTimeMillis(),
                timeoutMinutes = config.autoRevertMinutes,
                triggeredPackage = packageName,
                originalDevOptionsEnabled = devEnabled,
                originalAdbEnabled = adbEnabled,
                originalAdbWifiEnabled = adbWifiEnabled,
                originalAccessibilityEnabled = accEnabled,
                originalAccessibilityServices = accStr,
                wasShizukuRunning = wasShizukuRunning
            )
            snapshotDao.saveSnapshot(snapshot)

            // 2. Tùy chọn kill app trước để xóa cache bảo mật của ngân hàng
            if (config.relaunchApp) {
                try {
                    forceStopManager.forceStopDetailed(packageName, 0)
                } catch (e: Exception) {
                    Log.w(TAG, "Không thể force-stop $packageName: ${e.message}")
                }
            }

            // 3. Thực thi ẩn cài đặt hệ thống
            try {
                if (config.hideDevOptions) {
                    Settings.Global.putInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0)
                }
                if (config.hideAdb) {
                    Settings.Global.putInt(cr, Settings.Global.ADB_ENABLED, 0)
                }
                if (config.hideWirelessAdb) {
                    Settings.Global.putInt(cr, "adb_wifi_enabled", 0)
                }
                if (config.hideAccessibility && accStr.isNotBlank()) {
                    Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "")
                }
                Log.i(TAG, "✅ Đã ẩn thành công Developer Options & ADB cho $packageName")
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi ghi Secure Settings: ${e.message}", e)
                return@withLock false
            }

            // 4. Mở lại ứng dụng ngân hàng sạch sẽ nếu có tùy chọn relaunch
            if (config.relaunchApp) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                }
            }

            // 5. Lập lịch hẹn giờ khôi phục qua AlarmManager phần cứng
            scheduleWatchdogAlarm(config.autoRevertMinutes)

            // 6. Hiển thị Ongoing Notification kèm nút Khôi phục ngay
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

            true
        }
    }

    /**
     * Khôi phục cài đặt hệ thống về trạng thái ban đầu và tự động bật lại Shizuku.
     */
    suspend fun restoreSettings(reason: String = "Manual"): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
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
                    // BUG #2 fix: Chờ ADB daemon bind port sau khi bật lại
                    delay(2000)
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
