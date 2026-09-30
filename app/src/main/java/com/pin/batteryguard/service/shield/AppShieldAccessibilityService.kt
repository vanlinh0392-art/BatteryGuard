package com.pin.batteryguard.service.shield

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.pin.batteryguard.domain.shield.AppShieldManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "AppShieldAccessibility"

@AndroidEntryPoint
class AppShieldAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var appShieldManager: AppShieldManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var lastHandledPackage: String = ""
    @Volatile
    private var lastHandledTime: Long = 0L
    private val DEBOUNCE_MS = 1500L

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.i(TAG, "✅ AppShieldAccessibilityService đã kết nối và đang hoạt động!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return

        // Bỏ qua chính BatteryGuard và package rỗng
        if (packageName == this.packageName || packageName.isBlank()) return

        val isShieldTarget = appShieldManager.shouldShieldApp(packageName)
        val now = SystemClock.elapsedRealtime()

        if (isShieldTarget) {
            // Đối với app ngân hàng: Nếu cài đặt thực tế chưa ẩn, không bao giờ drop event
            val isAlreadyHidden = appShieldManager.isCurrentlySettingsHidden()
            if (isAlreadyHidden && packageName == lastHandledPackage && (now - lastHandledTime < DEBOUNCE_MS)) {
                return
            }
            lastHandledPackage = packageName
            lastHandledTime = now

            scope.launch {
                try {
                    appShieldManager.hideSettingsForApp(packageName)
                } catch (e: Exception) {
                    Log.e(TAG, "Lỗi khi xử lý bảo vệ app $packageName: ${e.message}", e)
                }
            }
        } else {
            // Đối với 99% app thông thường: Debounce 1500ms chặn spam IPC tuyệt đối
            if (packageName == lastHandledPackage && (now - lastHandledTime < DEBOUNCE_MS)) {
                return
            }
            lastHandledPackage = packageName
            lastHandledTime = now
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Dịch vụ Accessibility bị ngắt")
    }

    override fun onDestroy() {
        scope.cancel() // H12: Dọn sạch coroutine scope tránh leak
        isRunning = false
        Log.i(TAG, "AppShieldAccessibilityService đã bị hủy.")
        super.onDestroy()
    }
}
