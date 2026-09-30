package com.pin.batteryguard.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.pin.batteryguard.data.db.dao.AppUsageDao
import com.pin.batteryguard.data.db.dao.BatteryLogDao
import com.pin.batteryguard.data.db.dao.ExceptionListDao
import com.pin.batteryguard.data.db.dao.ForceStopLogDao
import com.pin.batteryguard.data.db.dao.FrozenAppDao
import com.pin.batteryguard.data.db.entity.AppUsageLog
import com.pin.batteryguard.data.db.entity.AppPolicyOverride
import com.pin.batteryguard.data.db.entity.BatteryLog
import com.pin.batteryguard.data.db.entity.ExceptionApp
import com.pin.batteryguard.data.db.entity.ForceStopLog
import com.pin.batteryguard.data.db.entity.FrozenApp
import com.pin.batteryguard.data.db.entity.MonitoringSession
import com.pin.batteryguard.data.db.entity.UidBaseline

@Database(
    entities = [
        BatteryLog::class,
        AppUsageLog::class,
        ForceStopLog::class,
        ExceptionApp::class,
        FrozenApp::class,
        MonitoringSession::class,
        UidBaseline::class,
        AppPolicyOverride::class,
        com.pin.batteryguard.data.db.entity.AppShieldSnapshotEntity::class,
        com.pin.batteryguard.data.db.entity.ShieldedAppEntity::class,
        com.pin.batteryguard.data.db.entity.XiaomiFixSnapshotEntity::class,
        com.pin.batteryguard.data.db.entity.PermissionSnapshotEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun batteryLogDao(): BatteryLogDao
    abstract fun appUsageDao(): AppUsageDao
    abstract fun forceStopLogDao(): ForceStopLogDao
    abstract fun exceptionListDao(): ExceptionListDao
    abstract fun frozenAppDao(): FrozenAppDao
    abstract fun monitoringStateDao(): com.pin.batteryguard.data.db.dao.MonitoringStateDao
    abstract fun appPolicyOverrideDao(): com.pin.batteryguard.data.db.dao.AppPolicyOverrideDao
    abstract fun appShieldSnapshotDao(): com.pin.batteryguard.data.db.dao.AppShieldSnapshotDao
    abstract fun shieldedAppDao(): com.pin.batteryguard.data.db.dao.ShieldedAppDao
    abstract fun xiaomiFixSnapshotDao(): com.pin.batteryguard.data.db.dao.XiaomiFixSnapshotDao
    abstract fun permissionSnapshotDao(): com.pin.batteryguard.data.db.dao.PermissionSnapshotDao

    companion object {
        private const val DATABASE_NAME = "battery_guard.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                ).addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build()
                INSTANCE = instance
                instance
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_usage_logs ADD COLUMN userId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_usage_logs ADD COLUMN uid INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_usage_logs ADD COLUMN deltaMah REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE app_usage_logs ADD COLUMN ratePercentPerHour REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE app_usage_logs ADD COLUMN evidence TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_usage_logs ADD COLUMN decision TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE app_usage_logs ADD COLUMN skipReason TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE force_stop_logs ADD COLUMN userId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE force_stop_logs ADD COLUMN uid INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE force_stop_logs ADD COLUMN deltaMah REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE force_stop_logs ADD COLUMN ratePercentPerHour REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE force_stop_logs ADD COLUMN verified INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE force_stop_logs ADD COLUMN exitCode INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE force_stop_logs ADD COLUMN errorMessage TEXT")

                db.execSQL("CREATE TABLE IF NOT EXISTS frozen_apps_new (userId INTEGER NOT NULL, packageName TEXT NOT NULL, appName TEXT NOT NULL, frozenAt INTEGER NOT NULL, PRIMARY KEY(userId, packageName))")
                db.execSQL("INSERT INTO frozen_apps_new(userId, packageName, appName, frozenAt) SELECT 0, packageName, appName, frozenAt FROM frozen_apps")
                db.execSQL("DROP TABLE frozen_apps")
                db.execSQL("ALTER TABLE frozen_apps_new RENAME TO frozen_apps")
                db.execSQL("CREATE TABLE IF NOT EXISTS monitoring_sessions (id INTEGER NOT NULL PRIMARY KEY, startedAtMillis INTEGER NOT NULL, startedAtElapsedRealtime INTEGER NOT NULL, lastCapturedAtMillis INTEGER NOT NULL, lastCapturedAtElapsedRealtime INTEGER NOT NULL, statsStartFingerprint TEXT NOT NULL, capacityMah REAL NOT NULL, chargeCounterUah INTEGER, isScreenOff INTEGER NOT NULL, isCharging INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS uid_baselines (sessionId INTEGER NOT NULL, uid INTEGER NOT NULL, userId INTEGER NOT NULL, totalPowerMah REAL NOT NULL, foregroundTimeMs INTEGER NOT NULL, backgroundTimeMs INTEGER NOT NULL, cpuPowerMah REAL NOT NULL, wakeLockPowerMah REAL NOT NULL, audioPowerMah REAL NOT NULL, gnssPowerMah REAL NOT NULL, sensorPowerMah REAL NOT NULL, consecutiveBreaches INTEGER NOT NULL, PRIMARY KEY(sessionId, uid))")
                db.execSQL("CREATE TABLE IF NOT EXISTS app_policy_overrides (userId INTEGER NOT NULL, packageName TEXT NOT NULL, allowActiveUseStop INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(userId, packageName))")
            }
        }

        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS app_shield_snapshots (
                        id INTEGER NOT NULL PRIMARY KEY,
                        isCurrentlyHidden INTEGER NOT NULL,
                        hideTimestamp INTEGER NOT NULL,
                        timeoutMinutes INTEGER NOT NULL,
                        triggeredPackage TEXT NOT NULL,
                        originalDevOptionsEnabled INTEGER NOT NULL,
                        originalAdbEnabled INTEGER NOT NULL,
                        originalAdbWifiEnabled INTEGER NOT NULL,
                        originalAccessibilityEnabled INTEGER NOT NULL,
                        originalAccessibilityServices TEXT NOT NULL,
                        overlaidPackagesDenied TEXT NOT NULL,
                        wasShizukuRunning INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS shielded_apps (
                        packageName TEXT NOT NULL PRIMARY KEY,
                        appName TEXT NOT NULL,
                        isEnabled INTEGER NOT NULL,
                        isPresetBank INTEGER NOT NULL,
                        addedAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // AppUsageLog indices
                db.execSQL("CREATE INDEX IF NOT EXISTS index_app_usage_logs_timestamp ON app_usage_logs (timestamp)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_app_usage_logs_periodEnd ON app_usage_logs (periodEnd)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_app_usage_logs_packageName_timestamp ON app_usage_logs (packageName, timestamp)")
                // ForceStopLog indices
                db.execSQL("CREATE INDEX IF NOT EXISTS index_force_stop_logs_packageName_userId_verified_timestamp ON force_stop_logs (packageName, userId, verified, timestamp)")
                // BatteryLog indices
                db.execSQL("CREATE INDEX IF NOT EXISTS index_battery_logs_timestamp ON battery_logs (timestamp)")
            }
        }

        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS xiaomi_fix_snapshots (
                        packageName TEXT PRIMARY KEY NOT NULL,
                        uid INTEGER NOT NULL,
                        capturedAt INTEGER NOT NULL,
                        originalOp10053 INTEGER NOT NULL,
                        originalOp10008 INTEGER NOT NULL,
                        originalStandbyBucket INTEGER NOT NULL,
                        wasInDozeWhitelist INTEGER NOT NULL,
                        wasInNetpolicy INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS permission_snapshots (
                        id TEXT NOT NULL PRIMARY KEY,
                        packageName TEXT NOT NULL,
                        appName TEXT NOT NULL,
                        capturedAt INTEGER NOT NULL,
                        formattedDate TEXT NOT NULL,
                        grantedPermissionsJson TEXT NOT NULL,
                        appOpsStatesJson TEXT NOT NULL,
                        isBatteryWhitelisted INTEGER NOT NULL,
                        standbyBucket INTEGER NOT NULL,
                        snapshotReason TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_permission_snapshots_packageName_capturedAt ON permission_snapshots (packageName, capturedAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_permission_snapshots_packageName ON permission_snapshots (packageName)")
            }
        }
    }
}
