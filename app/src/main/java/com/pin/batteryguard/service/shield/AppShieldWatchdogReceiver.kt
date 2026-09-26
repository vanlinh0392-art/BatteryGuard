package com.pin.batteryguard.service.shield

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pin.batteryguard.domain.shield.AppShieldManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "AppShieldWatchdog"

@AndroidEntryPoint
class AppShieldWatchdogReceiver : BroadcastReceiver() {

    @Inject
    lateinit var appShieldManager: AppShieldManager

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Nhận được action: $action")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_REVERT_TIMEOUT -> {
                        appShieldManager.restoreSettings(reason = "Hết thời gian hẹn giờ (AlarmManager)")
                    }
                    ACTION_MANUAL_REVERT -> {
                        appShieldManager.restoreSettings(reason = "Người dùng bấm khôi phục")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi xử lý action $action: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REVERT_TIMEOUT = "com.pin.batteryguard.ACTION_SHIELD_TIMEOUT"
        const val ACTION_MANUAL_REVERT = "com.pin.batteryguard.ACTION_SHIELD_MANUAL_REVERT"
    }
}
