# Add project specific ProGuard rules here.
-keep class dev.rikka.shizuku.** { *; }
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep class android.app.IActivityManager { *; }
-keep class android.app.IActivityManager$Stub { *; }

# Keep data models
-keep class com.pin.batteryguard.data.model.** { *; }
-keep class com.pin.batteryguard.ui.screen.automation.** { *; }
-keep class com.pin.batteryguard.automation.** { *; }

# Keep XiaomiHelper reflective / IPC calls
-keep class com.pin.batteryguard.util.XiaomiHelper { *; }

# Keep SecurityValidator
-keep class com.pin.batteryguard.security.SecurityValidator { *; }
