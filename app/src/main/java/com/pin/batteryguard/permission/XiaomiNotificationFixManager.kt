package com.pin.batteryguard.permission

import android.content.Context
import android.content.pm.PackageManager
import com.pin.batteryguard.data.db.dao.ExceptionListDao
import com.pin.batteryguard.data.db.dao.XiaomiFixSnapshotDao
import com.pin.batteryguard.data.db.entity.ExceptionApp
import com.pin.batteryguard.data.db.entity.XiaomiFixSnapshotEntity
import com.pin.batteryguard.shizuku.ShizukuManager
import com.pin.batteryguard.shizuku.executeShizukuCommandWithTimeout
import com.pin.batteryguard.util.XiaomiHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XiaomiNotificationFixManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val snapshotDao: XiaomiFixSnapshotDao,
    private val exceptionListDao: ExceptionListDao,
    private val shizukuManager: ShizukuManager
) {

    companion object {
        private val PACKAGE_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

        // Từ khóa nhận diện ứng dụng Nhắn tin / Mạng xã hội cần thông báo tức thì
        val CHAT_KEYWORDS = setOf(
            "zalo", "facebook", "orca", "messenger", "telegram", "whatsapp",
            "viber", "instagram", "discord", "locket", "line", "kakaotalk",
            "wechat", "tiktok", "skype", "signal", "threads", "barcelona",
            "snapchat", "slack", "teams", "gmail", "outlook", "mail"
        )

        // Từ khóa nhận diện ứng dụng Ngân hàng / Ví điện tử
        val BANKING_KEYWORDS = setOf(
            "momo", "zalopay", "vnpay", "viettelpay", "viettelmoney", "shopeepay",
            "mbmobile", "mb bank", "vietcombank", "vcb", "techcombank", "tcb",
            "vietinbank", "bidv", "agribank", "tpbank", "acb", "sacombank",
            "vneid", "cake", "timo", "tnex", "vib", "vpbank", "hdbank",
            "ocb", "seabank", "msb", "lpbank", "eximbank", "namabank", "abbank",
            "bacabank", "kienlongbank", "baovietbank", "saigonbank"
        )
    }

    fun isXiaomiDevice(): Boolean = XiaomiHelper.isXiaomi()

    fun isChinaRom(): Boolean = XiaomiHelper.isChinaRom()

    /**
     * Kiểm tra xem package có thuộc nhóm Chat hoặc Ngân hàng không
     */
    fun isChatOrBankApp(packageName: String, appName: String = ""): Boolean {
        val lowerPkg = packageName.lowercase(Locale.ROOT)
        val lowerName = appName.lowercase(Locale.ROOT)
        return CHAT_KEYWORDS.any { lowerPkg.contains(it) || lowerName.contains(it) } ||
                BANKING_KEYWORDS.any { lowerPkg.contains(it) || lowerName.contains(it) }
    }

    suspend fun hasSnapshot(packageName: String): Boolean = withContext(Dispatchers.IO) {
        snapshotDao.hasSnapshot(packageName)
    }

    suspend fun getSnapshot(packageName: String): XiaomiFixSnapshotEntity? = withContext(Dispatchers.IO) {
        snapshotDao.getByPackage(packageName)
    }

    /**
     * Kiểm tra tính hợp lệ của package name chuẩn Zero-Trust
     */
    fun validatePackageName(packageName: String) {
        require(packageName.matches(PACKAGE_REGEX) && packageName.length <= 128) {
            "Tên gói không hợp lệ: $packageName"
        }
    }

    /**
     * Chụp snapshot trạng thái ban đầu của ứng dụng (nếu chưa có)
     */
    private suspend fun captureSnapshotIfNotExists(packageName: String, uid: Int) {
        if (snapshotDao.hasSnapshot(packageName)) return

        var originalOp10053 = 3 // default
        var originalOp10008 = 3 // default
        var originalStandbyBucket = 10 // active fallback
        var wasInDoze = false
        var wasInNetpolicy = false

        try {
            // Đọc AppOps
            val appOpsResult = executeShizukuCommandWithTimeout(arrayOf("cmd", "appops", "get", packageName), 3000L)
            if (appOpsResult.isSuccess) {
                val out = appOpsResult.stdout
                if (out.contains("10053: allow") || out.contains("MIUIOP(10053): allow")) originalOp10053 = 0
                if (out.contains("10008: allow") || out.contains("MIUIOP(10008): allow")) originalOp10008 = 0
            }

            // Đọc Standby bucket
            val bucketResult = executeShizukuCommandWithTimeout(arrayOf("am", "get-standby-bucket", packageName), 2000L)
            if (bucketResult.isSuccess) {
                originalStandbyBucket = bucketResult.stdout.trim().toIntOrNull() ?: 10
            }

            // Đọc Doze whitelist
            val dozeResult = executeShizukuCommandWithTimeout(arrayOf("dumpsys", "deviceidle", "whitelist"), 2000L)
            if (dozeResult.isSuccess) {
                wasInDoze = dozeResult.stdout.contains(",$packageName,") || dozeResult.stdout.contains(packageName)
            }

            // Đọc Netpolicy
            if (uid > 0) {
                val netResult = executeShizukuCommandWithTimeout(arrayOf("cmd", "netpolicy", "list", "restrict-background-whitelist"), 2000L)
                if (netResult.isSuccess) {
                    wasInNetpolicy = netResult.stdout.contains(" $uid ") || netResult.stdout.contains("$uid")
                }
            }

            snapshotDao.upsert(
                XiaomiFixSnapshotEntity(
                    packageName = packageName,
                    uid = uid,
                    originalOp10053 = originalOp10053,
                    originalOp10008 = originalOp10008,
                    originalStandbyBucket = originalStandbyBucket,
                    wasInDozeWhitelist = wasInDoze,
                    wasInNetpolicy = wasInNetpolicy
                )
            )
        } catch (_: Exception) {
            // Fallback snapshot an toàn
            snapshotDao.upsert(
                XiaomiFixSnapshotEntity(
                    packageName = packageName,
                    uid = uid,
                    originalOp10053 = 3,
                    originalOp10008 = 3,
                    originalStandbyBucket = 10,
                    wasInDozeWhitelist = false,
                    wasInNetpolicy = false
                )
            )
        }
    }

    /**
     * Thực thi sửa lỗi thông báo Xiaomi 7 tầng cho 1 ứng dụng
     */
    suspend fun fixNotification(packageName: String, appName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            validatePackageName(packageName)

            if (!shizukuManager.isReady() && !shizukuManager.ensureReady()) {
                return@withContext Result.failure(Exception("Shizuku chưa sẵn sàng hoặc chưa được cấp quyền."))
            }

            val pm = context.packageManager
            val uid = try {
                pm.getPackageUid(packageName, 0)
            } catch (e: PackageManager.NameNotFoundException) {
                return@withContext Result.failure(Exception("Không tìm thấy ứng dụng trên thiết bị."))
            }

            // Bước 1: Lưu Snapshot ban đầu phục vụ Rollback
            captureSnapshotIfNotExists(packageName, uid)

            // Bước 2: Tạo chuỗi script gộp 7 tầng nguyên tử
            val script = buildString {
                // Tự khởi chạy HyperOS 2/3 (10053) & MIUI 12-14 legacy (10008)
                append("cmd appops set $packageName 10053 allow; ")
                append("cmd appops set $packageName 10008 allow; ")
                // Miễn trừ Doze
                append("dumpsys deviceidle whitelist +$packageName; ")
                // Đưa về Standby Bucket Active (10)
                append("am set-standby-bucket $packageName active; ")
                // Thông báo Package & UID
                append("cmd appops set $packageName POST_NOTIFICATION allow; ")
                append("cmd appops set --uid $packageName POST_NOTIFICATION allow; ")
                // 5 Quyền chạy nền sống còn
                append("for op in RUN_IN_BACKGROUND RUN_ANY_IN_BACKGROUND WAKE_LOCK START_FOREGROUND SCHEDULE_EXACT_ALARM; do ")
                append("cmd appops set $packageName \$op allow; ")
                append("done; ")
                // Dữ liệu nền không giới hạn
                if (uid > 0) {
                    append("cmd netpolicy add restrict-background-whitelist $uid; ")
                }
                // Refresh Security Center để MIUI cập nhật giao diện
                append("am force-stop com.miui.securitycenter")
            }

            val execResult = executeShizukuCommandWithTimeout(arrayOf("sh", "-c", script), 8000L)
            if (!execResult.isSuccess) {
                return@withContext Result.failure(Exception("Lỗi thực thi lệnh Shizuku (Mã lỗi ${execResult.exitCode}): ${execResult.stderr.ifBlank { execResult.stdout }}"))
            }

            // Bước 3: Đồng bộ vào ExceptionApp của BatteryGuard để không bị kill ngầm
            exceptionListDao.insert(
                ExceptionApp(
                    packageName = packageName,
                    appName = appName.ifBlank { packageName },
                    reason = "Fix thông báo Xiaomi",
                    isSystemDefault = false
                )
            )

            Result.success("Đã tối ưu thông báo & tự khởi chạy cho $appName thành công!")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Khôi phục (Rollback) trạng thái ban đầu của ứng dụng
     */
    suspend fun restoreNotification(packageName: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            validatePackageName(packageName)

            if (!shizukuManager.isReady() && !shizukuManager.ensureReady()) {
                return@withContext Result.failure(Exception("Shizuku chưa sẵn sàng."))
            }

            val snapshot = snapshotDao.getByPackage(packageName)
            val uid = snapshot?.uid ?: try {
                context.packageManager.getPackageUid(packageName, 0)
            } catch (_: Exception) {
                0
            }

            val script = buildString {
                // Trả AppOps về mặc định
                append("cmd appops set $packageName 10053 default; ")
                append("cmd appops set $packageName 10008 default; ")
                // Gỡ khỏi Doze nếu ban đầu không có
                if (snapshot?.wasInDozeWhitelist != true) {
                    append("dumpsys deviceidle whitelist -$packageName; ")
                }
                // Trả bucket về mặc định
                val originalBucket = snapshot?.originalStandbyBucket ?: 10
                append("am set-standby-bucket $packageName $originalBucket; ")
                // Gỡ netpolicy nếu ban đầu không có
                if (uid > 0 && snapshot?.wasInNetpolicy != true) {
                    append("cmd netpolicy remove restrict-background-whitelist $uid; ")
                }
                append("am force-stop com.miui.securitycenter")
            }

            val execResult = executeShizukuCommandWithTimeout(arrayOf("sh", "-c", script), 6000L)
            if (!execResult.isSuccess) {
                return@withContext Result.failure(Exception("Lỗi khôi phục: ${execResult.stderr}"))
            }

            // Xóa snapshot và gỡ khỏi ExceptionApp
            snapshotDao.deleteByPackage(packageName)
            exceptionListDao.delete(packageName)

            Result.success("Đã khôi phục cài đặt gốc thành công.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Chạy tối ưu hàng loạt cho danh sách ứng dụng với báo cáo tiến độ
     */
    suspend fun batchFix(
        apps: List<Pair<String, String>>, // (packageName, appName)
        onProgress: (current: Int, total: Int, currentName: String) -> Unit
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        var successCount = 0
        var failCount = 0
        val total = apps.size

        apps.forEachIndexed { index, (pkg, name) ->
            onProgress(index + 1, total, name)
            val result = fixNotification(pkg, name)
            if (result.isSuccess) {
                successCount++
            } else {
                failCount++
            }
        }
        Pair(successCount, failCount)
    }
}
