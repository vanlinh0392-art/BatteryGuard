package com.pin.batteryguard.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.pin.batteryguard.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AppUpdateManager"
private const val GITHUB_OWNER = "vanlinh0392-art"
private const val GITHUB_REPO = "BatteryGuard"

data class AppUpdateInfo(
    val latestVersion: String,
    val currentVersion: String,
    val hasUpdate: Boolean,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val apkSize: Long
)

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var downloadReceiver: BroadcastReceiver? = null

    /**
     * Kiểm tra phiên bản mới nhất từ GitHub Releases API.
     * Sử dụng hoàn toàn Java/Kotlin Stdlib HttpURLConnection (Ponytail Doctrine, Zero-Bloat).
     */
    suspend fun checkUpdate(
        owner: String = GITHUB_OWNER,
        repo: String = GITHUB_REPO
    ): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"
            val url = URL(apiUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "BatteryGuard-App")
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    return@withContext Result.failure(Exception("Chưa có bản phát hành (Releases) nào trên GitHub."))
                }
                return@withContext Result.failure(Exception("GitHub API trả về mã lỗi: $responseCode"))
            }

            val reader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = reader.use { it.readText() }
            connection.disconnect()

            val json = JSONObject(response)
            val tagName = json.optString("tag_name", "").trim()
            val releaseTitle = json.optString("name", tagName)
            val releaseNotes = json.optString("body", "Không có thông tin chi tiết.")
            val assets = json.optJSONArray("assets")

            var downloadUrl = ""
            var apkSize = 0L

            if (assets != null && assets.length() > 0) {
                // Ưu tiên tìm file .apk trong danh sách assets
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url", "")
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
                // Nếu không tìm thấy đuôi .apk cụ thể, lấy asset đầu tiên
                if (downloadUrl.isBlank() && assets.length() > 0) {
                    val firstAsset = assets.getJSONObject(0)
                    downloadUrl = firstAsset.optString("browser_download_url", "")
                    apkSize = firstAsset.optLong("size", 0L)
                }
            }

            val currentVersion = BuildConfig.VERSION_NAME
            val hasUpdate = isNewerVersion(tagName, currentVersion)

            Log.i(TAG, "Kiểm tra cập nhật: Hiện tại=$currentVersion, Mới nhất=$tagName, Có cập nhật=$hasUpdate")

            Result.success(
                AppUpdateInfo(
                    latestVersion = tagName,
                    currentVersion = currentVersion,
                    hasUpdate = hasUpdate,
                    releaseTitle = releaseTitle,
                    releaseNotes = releaseNotes,
                    downloadUrl = downloadUrl,
                    apkSize = apkSize
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi kiểm tra cập nhật: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * So sánh 2 chuỗi phiên bản dạng SemVer (Ví dụ: v1.0.1 và 1.0.0).
     */
    fun isNewerVersion(latest: String, current: String): Boolean {
        val lClean = latest.removePrefix("v").removePrefix("V").trim()
        val cClean = current.removePrefix("v").removePrefix("V").trim()

        val lParts = lClean.split('.').mapNotNull { it.toIntOrNull() }
        val cParts = cClean.split('.').mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(lParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val lVal = lParts.getOrElse(i) { 0 }
            val cVal = cParts.getOrElse(i) { 0 }
            if (lVal > cVal) return true
            if (lVal < cVal) return false
        }
        return false
    }

    /**
     * Tải file APK qua Android DownloadManager và tự động hiển thị trình cài đặt khi hoàn tất.
     */
    fun downloadAndInstall(updateInfo: AppUpdateInfo, onStarted: (Long) -> Unit = {}) {
        if (updateInfo.downloadUrl.isBlank()) {
            Log.e(TAG, "Không có URL tải APK")
            return
        }

        val apkFileName = "BatteryGuard-${updateInfo.latestVersion}.apk"
        val destinationFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), apkFileName)

        // Nếu file đã tồn tại từ lần tải trước, xóa đi để tải bản mới sạch sẽ
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(updateInfo.downloadUrl)).apply {
            setTitle("Tải bản cập nhật BatteryGuard ${updateInfo.latestVersion}")
            setDescription("Đang tải file $apkFileName...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.fromFile(destinationFile))
            setMimeType("application/vnd.android.package-archive")
        }

        val downloadId = downloadManager.enqueue(request)
        onStarted(downloadId)

        // Hủy receiver cũ nếu có
        downloadReceiver?.let {
            try { context.unregisterReceiver(it) } catch (_: Exception) {}
        }

        // Đăng ký BroadcastReceiver để lắng nghe tải xong
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(recvContext: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                if (id == downloadId) {
                    Log.i(TAG, "Tải APK thành công. Tiến hành khởi chạy trình cài đặt...")
                    installApk(destinationFile)
                    try {
                        context.unregisterReceiver(this)
                    } catch (_: Exception) {}
                    downloadReceiver = null
                }
            }
        }
        downloadReceiver = receiver

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
    }

    /**
     * Kích hoạt Package Installer của hệ thống thông qua FileProvider an toàn.
     */
    fun installApk(file: File) {
        if (!file.exists()) {
            Log.e(TAG, "File APK không tồn tại: ${file.absolutePath}")
            return
        }

        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi mở trình cài đặt APK: ${e.message}", e)
        }
    }

    /**
     * Fallback mở liên kết tải trực tiếp trên trình duyệt.
     */
    fun openBrowserDownload(downloadUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Không thể mở trình duyệt: ${e.message}")
        }
    }
}
