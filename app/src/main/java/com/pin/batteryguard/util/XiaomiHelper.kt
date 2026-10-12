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

    // =========================================================================
    // 🔒 Ổ Khóa Đa nhiệm Recents & Kernel Freeze Millet (HyperOS / MIUI)
    // =========================================================================

    fun isAppLockedInRecents(context: Context, packageName: String): Boolean {
        val jsonStr = try {
            Settings.System.getString(context.contentResolver, "locked_apps") ?: ""
        } catch (_: Exception) {
            ""
        }
        if (jsonStr.isBlank()) return false
        return try {
            val array = org.json.JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val u = obj.optInt("u", -999)
                if (u == 0) {
                    val pkgs = obj.optJSONArray("pkgs") ?: continue
                    for (j in 0 until pkgs.length()) {
                        if (pkgs.optString(j) == packageName) return true
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    fun modifyLockedAppsJson(currentJson: String, packageName: String, locked: Boolean): String {
        return try {
            val array = if (currentJson.isNotBlank()) org.json.JSONArray(currentJson) else org.json.JSONArray()
            var user0Obj: org.json.JSONObject? = null
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                if (obj.optInt("u", -999) == 0) {
                    user0Obj = obj
                    break
                }
            }
            if (user0Obj == null) {
                user0Obj = org.json.JSONObject().apply {
                    put("u", 0)
                    put("pkgs", org.json.JSONArray())
                }
                array.put(user0Obj)
            }
            val pkgs = user0Obj.optJSONArray("pkgs") ?: org.json.JSONArray().also { user0Obj.put("pkgs", it) }
            val newPkgs = org.json.JSONArray()
            var found = false
            for (i in 0 until pkgs.length()) {
                val pkg = pkgs.optString(i)
                if (pkg == packageName) {
                    found = true
                    if (locked) newPkgs.put(pkg)
                } else if (pkg.isNotBlank()) {
                    newPkgs.put(pkg)
                }
            }
            if (locked && !found) {
                newPkgs.put(packageName)
            }
            user0Obj.put("pkgs", newPkgs)
            array.toString()
        } catch (_: Exception) {
            currentJson
        }
    }

    fun setAppLockedInRecents(context: Context, packageName: String, locked: Boolean): Boolean {
        return try {
            val cr = context.contentResolver
            val currentJson = Settings.System.getString(cr, "locked_apps") ?: "[]"
            val newJson = modifyLockedAppsJson(currentJson, packageName, locked)
            Settings.System.putString(cr, "locked_apps", newJson)
        } catch (_: Exception) {
            false
        }
    }

    fun isMilletWhitelisted(context: Context, packageName: String): Boolean {
        val list = try {
            Settings.System.getString(context.contentResolver, "MILLET_NO_RESTRICT_APP") ?: ""
        } catch (_: Exception) {
            ""
        }
        if (list.isBlank()) return false
        val items = list.split(",").map { it.trim() }
        return items.contains(packageName)
    }

    fun modifyMilletWhitelist(currentList: String, packageName: String, whitelisted: Boolean): String {
        val items = currentList.split(",").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
        if (whitelisted) {
            if (!items.contains(packageName)) items.add(packageName)
        } else {
            items.remove(packageName)
        }
        return items.joinToString(",")
    }

    fun setMilletWhitelist(context: Context, packageName: String, whitelisted: Boolean): Boolean {
        return try {
            val cr = context.contentResolver
            val currentList = Settings.System.getString(cr, "MILLET_NO_RESTRICT_APP") ?: ""
            val newList = modifyMilletWhitelist(currentList, packageName, whitelisted)
            Settings.System.putString(cr, "MILLET_NO_RESTRICT_APP", newList)
        } catch (_: Exception) {
            false
        }
    }

    // =========================================================================
    // ⚡ Quản lý Sạc Nhanh 90W & Giao diện Tối OLED (Xiaomi 14 Hardware)
    // =========================================================================

    fun isFastChargeEnabled(context: Context): Boolean {
        return try {
            Settings.Secure.getInt(context.contentResolver, "key_fast_charge_enabled", 1) == 1
        } catch (_: Exception) {
            true
        }
    }

    fun setFastChargeEnabled(context: Context, enabled: Boolean): Boolean {
        return try {
            Settings.Secure.putInt(context.contentResolver, "key_fast_charge_enabled", if (enabled) 1 else 0)
        } catch (_: Exception) {
            false
        }
    }

    fun isDarkMode(context: Context): Boolean {
        return try {
            Settings.System.getInt(context.contentResolver, "dark_mode_enable", 0) == 1
        } catch (_: Exception) {
            false
        }
    }

    fun setDarkMode(context: Context, enabled: Boolean): Boolean {
        return try {
            Settings.System.putInt(context.contentResolver, "dark_mode_enable", if (enabled) 1 else 0)
        } catch (_: Exception) {
            false
        }
    }
}
