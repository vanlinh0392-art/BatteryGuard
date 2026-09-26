package com.pin.batteryguard.util

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

object PackageHelper {

    private val iconCache = android.util.LruCache<String, Drawable>(100)
    private val nameCache = android.util.LruCache<String, String>(100)

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

    fun isSystemApp(context: Context, packageName: String): Boolean {
        return try {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) {
            false
        }
    }

    fun isAppRunning(context: Context, packageName: String): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningProcesses = am.runningAppProcesses ?: return false
        for (processInfo in runningProcesses) {
            if (processInfo.processName == packageName) {
                return true
            }
        }
        return false
    }

}
