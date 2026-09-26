package com.pin.batteryguard.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.pin.batteryguard.data.db.dao.ExceptionListDao
import com.pin.batteryguard.data.db.entity.ExceptionApp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

import android.content.Intent

@Singleton
class AppRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exceptionListDao: ExceptionListDao
) {
    private val packageManager: PackageManager = context.packageManager

    fun getExceptions(): Flow<List<ExceptionApp>> = exceptionListDao.getAll()

    suspend fun addException(packageName: String, appName: String, reason: String, isSystemDefault: Boolean = false) {
        exceptionListDao.insert(
            ExceptionApp(
                packageName = packageName,
                appName = appName,
                reason = reason,
                isSystemDefault = isSystemDefault
            )
        )
    }

    suspend fun removeException(packageName: String) {
        exceptionListDao.delete(packageName)
    }

    suspend fun isAppException(packageName: String): Boolean {
        return exceptionListDao.isException(packageName)
    }

    suspend fun getExceptionPackages(): List<String> {
        return exceptionListDao.getExceptionPackages()
    }

    suspend fun getInstalledUserApps(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        apps.filter { app ->
            // Chỉ lấy app người dùng cài, hoặc app hệ thống có launcher activity
            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val hasLauncher = packageManager.getLaunchIntentForPackage(app.packageName) != null
            
            (!isSystem && !isUpdatedSystem) || hasLauncher
        }.map { app ->
            val label = app.loadLabel(packageManager).toString()
            app.packageName to label
        }.sortedBy { it.second }
    }

    suspend fun insertDefaultExceptions() {
        val defaultList = mutableListOf(
            Triple("moe.shizuku.privileged.api", "Shizuku", "Bắt buộc để force stop hoạt động"),
            Triple(context.packageName, "BatteryGuard", "Dịch vụ của ứng dụng này"),
            Triple("com.android.dialer", "Điện thoại", "Tránh nhỡ cuộc gọi"),
            Triple("com.google.android.dialer", "Điện thoại", "Tránh nhỡ cuộc gọi"),
            Triple("com.android.mms", "Tin nhắn", "Tránh lỡ tin nhắn"),
            Triple("com.google.android.apps.messaging", "Tin nhắn", "Tránh lỡ tin nhắn"),
            Triple("com.android.deskclock", "Đồng hồ", "Tránh lỡ báo thức"),
            Triple("com.google.android.deskclock", "Đồng hồ", "Tránh lỡ báo thức")
        )

        try {
            val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
            val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            val launcherPkg = resolveInfo?.activityInfo?.packageName
            if (launcherPkg != null) {
                defaultList.add(Triple(launcherPkg, "Trình khởi chạy hệ thống", "Tránh làm đơ màn hình chính (Launcher) của máy"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        defaultList.forEach { (pkg, name, reason) ->
            if (isPackageInstalled(pkg)) {
                addException(packageName = pkg, appName = name, reason = reason, isSystemDefault = true)
            }
        }
    }

    suspend fun cleanUpNonInstalledExceptions() {
        val packages = getExceptionPackages()
        packages.forEach { pkg ->
            if (!isPackageInstalled(pkg)) {
                removeException(pkg)
            }
        }
    }

    fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}
