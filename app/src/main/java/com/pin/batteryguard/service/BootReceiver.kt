package com.pin.batteryguard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pin.batteryguard.data.preferences.SettingsDataStore
import com.pin.batteryguard.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var appShieldManager: com.pin.batteryguard.domain.shield.AppShieldManager

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == "android.intent.action.QUICKBOOT_POWERON" || 
            action == "com.miui.app.ExceptionRecovery") {
            
            CoroutineScope(Dispatchers.IO).launch {
                // 1. Cứu hộ App Shield: Nếu máy sập nguồn khi đang ẩn, khôi phục ngay lập tức!
                try {
                    appShieldManager.restoreSettings(reason = "Khởi động lại máy (Boot Recovery)")
                } catch (e: Exception) {
                    android.util.Log.e("BootReceiver", "Lỗi cứu hộ App Shield khi boot: ${e.message}")
                }

                // 2. Tiếp tục giám sát pin
                val config = settingsDataStore.configFlow.first()
                if (config.isMonitoringEnabled) {
                    val serviceIntent = Intent(context, BatteryMonitorService::class.java)
                    context.startForegroundService(serviceIntent)
                    
                    // Gợi nhắc user bật Shizuku vì non-rooted Shizuku tự tắt khi restart máy
                    NotificationHelper.showShizukuReminder(context)
                }
            }
        }
    }
}
