package com.pin.batteryguard.util

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

object PackageHelper {

    private val iconCache = android.util.LruCache<String, Drawable>(400)
    private val nameCache = android.util.LruCache<String, String>(500)
    private val systemAppCache = ConcurrentHashMap<String, Boolean>()

    // RAM cache cho danh sách tiến trình đang chạy (TTL 2.5 giây)
    // Giúp giảm từ 200+ cuộc gọi IPC tới ActivityManager xuống đúng 1 lần khi load danh sách app
    @Volatile private var cachedRunningProcesses: Set<String>? = null
    @Volatile private var lastRunningProcessesFetchTime = 0L
    private const val RUNNING_PROCESSES_CACHE_TTL_MS = 2500L

    fun getAppName(context: Context, packageName: String): String {
        nameCache.get(packageName)?.let { return it }
        val name = try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast(".")
        }
        nameCache.put(packageName, name)
        return name
    }

    fun getAppIcon(context: Context, packageName: String): Drawable? {
        iconCache.get(packageName)?.let { return it }
        val icon = try {
            context.packageManager.getApplicationIcon(packageName)
        } catch (e: Exception) {
            null
        }
        if (icon != null) {
            iconCache.put(packageName, icon)
        }
        return icon
    }

    /**
     * Cache vĩnh viễn trong phiên chạy vì trạng thái System App không đổi.
     * Loại bỏ hàng trăm Binder IPC calls khi duyệt danh sách app.
     */
    fun isSystemApp(context: Context, packageName: String): Boolean {
        systemAppCache[packageName]?.let { return it }
        val isSystem = try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) {
            false
        }
        systemAppCache[packageName] = isSystem
        return isSystem
    }

    /**
     * Lấy tập hợp process đang chạy với TTL 2.5s.
     * Cực kỳ tiết kiệm CPU khi gọi trong vòng lặp nhiều app.
     */
    fun getRunningProcessNames(context: Context): Set<String> {
        val now = SystemClock.elapsedRealtime()
        val cached = cachedRunningProcesses
        if (cached != null && (now - lastRunningProcessesFetchTime < RUNNING_PROCESSES_CACHE_TTL_MS)) {
            return cached
        }
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return emptySet()
        val running = am.runningAppProcesses?.mapNotNull { it.processName }?.toSet().orEmpty()
        cachedRunningProcesses = running
        lastRunningProcessesFetchTime = now
        return running
    }

    fun isAppRunning(context: Context, packageName: String): Boolean {
        return packageName in getRunningProcessNames(context)
    }

    fun invalidateRunningProcessesCache() {
        cachedRunningProcesses = null
        lastRunningProcessesFetchTime = 0L
    }
}
