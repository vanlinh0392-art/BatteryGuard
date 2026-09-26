package com.pin.batteryguard.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.pin.batteryguard.MainActivity
import com.pin.batteryguard.R

object NotificationHelper {
    const val CHANNEL_MONITORING = "battery_monitoring"
    const val CHANNEL_ALERTS = "battery_alerts"
    const val CHANNEL_SUMMARY = "battery_summary"
    const val CHANNEL_APP_SHIELD = "app_shield"

    const val NOTIFICATION_ID_SERVICE = 1001
    const val NOTIFICATION_ID_ALERT = 1002
    const val NOTIFICATION_ID_SHIZUKU = 1003
    const val NOTIFICATION_ID_SHIZUKU_DISCONNECT = 1004
    const val NOTIFICATION_ID_APP_SHIELD = 1005
    const val NOTIFICATION_ID_APP_SHIELD_REVERTED = 1006

    fun createNotificationChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Xóa kênh cũ để áp dụng thay đổi mức độ quan trọng sang MIN
            try {
                manager.deleteNotificationChannel(CHANNEL_MONITORING)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Kênh 1: Giám sát pin (Silent)
            val monitoringChannel = NotificationChannel(
                CHANNEL_MONITORING,
                context.getString(R.string.notif_channel_monitoring),
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = context.getString(R.string.notif_channel_monitoring_desc)
                setShowBadge(false)
            }

            // Kênh 2: Cảnh báo pin (High)
            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.notif_channel_alerts),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_alerts_desc)
                enableLights(true)
                enableVibration(true)
            }

            // Kênh 3: Tổng hợp (Default)
            val summaryChannel = NotificationChannel(
                CHANNEL_SUMMARY,
                context.getString(R.string.notif_channel_summary),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_summary_desc)
            }

            // Kênh 4: App Shield (Bảo vệ ngân hàng)
            val shieldChannel = NotificationChannel(
                CHANNEL_APP_SHIELD,
                "Bảo vệ ứng dụng & Ngân hàng",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Thông báo khi ứng dụng ngân hàng được bảo vệ và trạng thái ẩn cài đặt"
                setShowBadge(true)
            }

        manager.createNotificationChannel(monitoringChannel)
        manager.createNotificationChannel(alertsChannel)
        manager.createNotificationChannel(summaryChannel)
        manager.createNotificationChannel(shieldChannel)
    }

    fun createMonitoringNotification(
        context: Context,
        batteryLevel: Int,
        isMonitoring: Boolean,
        stoppedCount: Int
    ): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val statusText = if (isMonitoring) {
            context.getString(R.string.monitoring_on)
        } else {
            context.getString(R.string.monitoring_off)
        }

        val title = context.getString(R.string.notif_monitoring_title, batteryLevel, statusText)
        val text = context.getString(R.string.notif_monitoring_text, stoppedCount)

        return NotificationCompat.Builder(context, CHANNEL_MONITORING)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    fun showForceStopSummary(context: Context, stoppedCount: Int, savedPercent: Float) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = context.getString(R.string.notif_stopped_title, stoppedCount)
        val text = context.getString(R.string.notif_stopped_text, savedPercent)

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        manager.notify(NOTIFICATION_ID_ALERT, notification)
    }

    fun showShizukuReminder(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Mở Shizuku Manager qua package name
        val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api") ?: Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 2, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = context.getString(R.string.notif_shizuku_title)
        val text = context.getString(R.string.notif_shizuku_text)

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        manager.notify(NOTIFICATION_ID_SHIZUKU, notification)
    }

    fun showHighTempAlertNotification(context: Context, temp: Float) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val coolDownIntent = Intent(context, com.pin.batteryguard.service.BatteryMonitorService::class.java).apply {
            action = "com.pin.batteryguard.ACTION_COOL_DOWN"
        }
        val coolDownPendingIntent = PendingIntent.getForegroundService(
            context, 5, coolDownIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val openAppIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 6, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = "⚠️ Cảnh báo quá nhiệt: ${temp}°C"
        val text = "Điện thoại đang quá nóng. Click để hạ nhiệt khẩn cấp bằng cách dừng các app ngầm ngốn pin."

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Hạ nhiệt ngay", coolDownPendingIntent)
            .build()

        manager.notify(NOTIFICATION_ID_ALERT, notification)
    }

    fun showCoolDownSuccessNotification(context: Context, stoppedCount: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val title = "❄️ Đã hạ nhiệt điện thoại thành công"
        val text = "Đã buộc dừng $stoppedCount ứng dụng chạy ngầm ngốn pin nhất để giảm tải cho CPU."

        val notification = NotificationCompat.Builder(context, CHANNEL_SUMMARY)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify(NOTIFICATION_ID_ALERT, notification)
    }
    /**
     * Hiển thị thông báo MỘT LẦN khi mất kết nối Shizuku (binder dead).
     * Thông báo tự động biến mất khi gọi dismissShizukuDisconnected().
     */
    fun showShizukuDisconnected(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Bấm vào thông báo → gửi action reconnect đến BatteryMonitorService
        val reconnectIntent = Intent(context, com.pin.batteryguard.service.BatteryMonitorService::class.java).apply {
            action = "com.pin.batteryguard.ACTION_RECONNECT_SHIZUKU"
        }
        val reconnectPendingIntent = PendingIntent.getForegroundService(
            context, 3, reconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SUMMARY)
            .setContentTitle("⚠️ Mất kết nối Shizuku")
            .setContentText("Bấm để tự bật Wireless Debugging và kết nối lại dịch vụ.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(reconnectPendingIntent)
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, "Kết nối lại", reconnectPendingIntent)
            .build()

        manager.notify(NOTIFICATION_ID_SHIZUKU_DISCONNECT, notification)
    }

    /** Tắt thông báo mất kết nối Shizuku khi binder sống lại. */
    fun dismissShizukuDisconnected(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID_SHIZUKU_DISCONNECT)
    }

    /** Thông báo khi Shizuku được tự động khởi chạy lại thành công qua ADB. */
    fun showShizukuRestarted(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 4, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SUMMARY)
            .setContentTitle("⚡ Shizuku đã tự khởi chạy")
            .setContentText("Dịch vụ Shizuku đã được tự động kích hoạt thành công qua ADB.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify(NOTIFICATION_ID_SHIZUKU, notification)
    }

    /**
     * Hiển thị thông báo thường trực khi chế độ App Shield đang ẩn cài đặt.
     */
    fun showAppShieldOngoingNotification(
        context: Context,
        appName: String,
        remainingMinutes: Int,
        revertPendingIntent: PendingIntent
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(context, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            context, 5, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_APP_SHIELD)
            .setContentTitle("🛡️ Đang bảo vệ $appName")
            .setContentText("Đã ẩn Dev Options & ADB. Tự động bật lại sau $remainingMinutes phút.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(mainPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, "⚡ Khôi phục ngay", revertPendingIntent)
            .build()

        manager.notify(NOTIFICATION_ID_APP_SHIELD, notification)
    }

    /** Hủy thông báo thường trực của App Shield */
    fun cancelAppShieldOngoingNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID_APP_SHIELD)
    }

    /** Thông báo sau khi đã khôi phục cài đặt an toàn */
    fun showAppShieldRevertedNotification(context: Context, revivedShizuku: Boolean) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val intent = Intent(context, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            context, 6, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val text = if (revivedShizuku) {
            "Đã khôi phục cài đặt gốc và tự động khởi chạy lại Shizuku."
        } else {
            "Đã khôi phục cài đặt Developer Options & ADB về trạng thái gốc."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_APP_SHIELD)
            .setContentTitle("✅ Đã khôi phục cài đặt an toàn")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(mainPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        manager.notify(NOTIFICATION_ID_APP_SHIELD_REVERTED, notification)
    }
}
