package com.pin.batteryguard.service

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import com.pin.batteryguard.shizuku.ShizukuManager
import com.pin.batteryguard.shizuku.shizukuNewProcess
import com.pin.batteryguard.util.PackageHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppProtectionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager
) {
    @Volatile private var cachedProtectedSet: Set<String> = emptySet()
    @Volatile private var lastProtectedCacheTime: Long = 0L
    private val CACHE_TTL_MS = 5 * 60 * 1000L

    fun isSystemApp(packageName: String): Boolean = PackageHelper.isSystemApp(context, packageName)

    private fun getProtectedPackages(): Set<String> {
        val now = System.currentTimeMillis()
        if (now - lastProtectedCacheTime < CACHE_TTL_MS && cachedProtectedSet.isNotEmpty()) {
            return cachedProtectedSet
        }
        val set = mutableSetOf<String>()
        set.add(context.packageName)
        set.add(SHIZUKU_PACKAGE)
        set.addAll(roleHolders())
        defaultHomePackage()?.let { set.add(it) }
        securePackage(Settings.Secure.DEFAULT_INPUT_METHOD)?.let { set.add(it) }
        set.addAll(accessibilityPackages())
        cachedProtectedSet = set
        lastProtectedCacheTime = now
        return set
    }

    fun isProtected(packageName: String, uid: Int): Boolean {
        if (uid < 10_000) return true
        if (packageName in getProtectedPackages()) return true
        return try {
            val flags = context.packageManager.getApplicationInfo(packageName, 0).flags
            flags and ApplicationInfo.FLAG_PERSISTENT != 0
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    suspend fun hasActiveForegroundService(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!shizukuManager.ensureReady()) return@withContext true
        try {
            val process = shizukuNewProcess(arrayOf("dumpsys", "activity", "services", packageName), null, null)
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.contains("isForeground=true")
        } catch (_: Exception) {
            true
        }
    }

    private fun roleHolders(): Set<String> {
        return setOfNotNull(
            context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage,
            Telephony.Sms.getDefaultSmsPackage(context),
            defaultHomePackage()
        )
    }

    private fun defaultHomePackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }

    private fun securePackage(key: String): String? =
        Settings.Secure.getString(context.contentResolver, key)?.substringBefore('/')?.takeIf { it.isNotBlank() }

    private fun accessibilityPackages(): Set<String> =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty()
            .split(':')
            .mapNotNull { it.substringBefore('/').takeIf(String::isNotBlank) }
            .toSet()

    companion object {
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }
}
