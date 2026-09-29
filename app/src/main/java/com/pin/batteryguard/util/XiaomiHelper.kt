package com.pin.batteryguard.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object XiaomiHelper {

    fun isXiaomi(): Boolean {
        return Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
                Build.BRAND.equals("Xiaomi", ignoreCase = true) ||
                Build.BRAND.equals("Redmi", ignoreCase = true) ||
                Build.BRAND.equals("POCO", ignoreCase = true)
    }

    fun isHyperOS(context: Context): Boolean {
        val version = getSystemProperty("ro.mi.os.version.name")
        return version.isNotEmpty()
    }

    fun getSystemProperty(key: String, default: String = ""): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val method = clazz.getMethod("get", String::class.java, String::class.java)
            (method.invoke(null, key, default) as? String) ?: default
        } catch (_: Exception) {
            default
        }
    }

    fun isChinaRom(): Boolean {
        if (!isXiaomi()) return false
        val buildIncremental = getSystemProperty("ro.build.version.incremental")
        if (buildIncremental.contains("CN", ignoreCase = true)) return true
        val region = getSystemProperty("ro.miui.region")
        if (region.equals("CN", ignoreCase = true)) return true
        val vendorRegion = getSystemProperty("ro.vendor.miui.region")
        return vendorRegion.equals("CN", ignoreCase = true)
    }

    fun openAutoStartSettings(context: Context) {
        try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent().apply {
                    action = "miui.intent.action.OP_AUTO_START"
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                // Fallback mở danh sách app thông thường
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        }
    }

    fun openBatterySaverSettings(context: Context) {
        try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
                putExtra("package_name", context.packageName)
                putExtra("package_label", "BatteryGuard")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestBatteryWhitelist(context: Context) {
        if (!isIgnoringBatteryOptimizations(context)) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    data class SetupStep(
        val title: String,
        val description: String,
        val buttonText: String,
        val action: (Context) -> Unit
    )

    fun getXiaomiSetupSteps(): List<SetupStep> {
        return listOf(
            SetupStep(
                title = "Tự khởi chạy (Auto-start)",
                description = "Cho phép ứng dụng tự chạy ngầm cùng hệ thống khi khởi động thiết bị.",
                buttonText = "Mở cài đặt",
                action = { context -> openAutoStartSettings(context) }
            ),
            SetupStep(
                title = "Bỏ hạn chế tiết kiệm pin",
                description = "Chuyển cấu hình pin của app sang chế độ 'Không hạn chế' (No restrictions) để tránh bị HyperOS kill service.",
                buttonText = "Mở cài đặt",
                action = { context -> openBatterySaverSettings(context) }
            ),
            SetupStep(
                title = "Bỏ qua tối ưu hóa pin",
                description = "Quyền hệ thống Android cho phép app bỏ qua tính năng tối ưu hóa pin chung của HĐH.",
                buttonText = "Cấp quyền",
                action = { context -> requestBatteryWhitelist(context) }
            )
        )
    }
}
