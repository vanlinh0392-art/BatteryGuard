package com.pin.batteryguard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pin.batteryguard.data.preferences.SettingsDataStore
import com.pin.batteryguard.shizuku.ShizukuAutoStarter
import com.pin.batteryguard.util.NotificationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "BootReceiver"

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var settingsDataStore: SettingsDataStore
    @Inject lateinit var appShieldManager: com.pin.batteryguard.domain.shield.AppShieldManager
    @Inject lateinit var shizukuAutoStarter: ShizukuAutoStarter

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.miui.app.ExceptionRecovery") {

            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // 1. Cứu hộ App Shield: Nếu máy sập nguồn khi đang ẩn, khôi phục ngay lập tức!
                    try {
                        appShieldManager.restoreSettings(reason = "Khởi động lại máy (Boot Recovery)")
                    } catch (e: Exception) {
                        Log.e(TAG, "Lỗi cứu hộ App Shield khi boot: ${e.message}")
                    }

                    // 2. Tiếp tục giám sát pin
                    val config = settingsDataStore.configFlow.first()
                    if (config.isMonitoringEnabled) {
                        val serviceIntent = Intent(context, BatteryMonitorService::class.java)
                        context.startForegroundService(serviceIntent)
                    }

                    // 3. Tự động khởi động lại Shizuku sau reboot
                    // Wireless ADB bị tắt sau reboot → bật lại bằng WRITE_SECURE_SETTINGS (persist qua reboot)
                    // rồi start Shizuku server qua ADB localhost
                    try {
                        shizukuAutoStarter.prepareAdbEnvironment()
                        delay(3000) // ADB daemon cần thời gian bind port sau reboot
                        val started = shizukuAutoStarter.startShizukuService(
                            notifyOnSuccess = true,
                            isManual = true // Bypass cooldown vì đây là boot
                        )
                        if (started) {
                            Log.i(TAG, "✅ Shizuku tự khởi động thành công sau reboot!")
                        } else {
                            // Shizuku không start được → hiện notification nhắc user
                            Log.w(TAG, "Shizuku chưa start được, hiện nhắc nhở.")
                            NotificationHelper.showShizukuReminder(context)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Không thể auto-start Shizuku: ${e.message}")
                        NotificationHelper.showShizukuReminder(context)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
