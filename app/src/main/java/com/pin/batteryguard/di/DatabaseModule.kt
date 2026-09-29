package com.pin.batteryguard.di

import android.content.Context
import com.pin.batteryguard.data.db.AppDatabase
import com.pin.batteryguard.data.db.dao.AppUsageDao
import com.pin.batteryguard.data.db.dao.AppPolicyOverrideDao
import com.pin.batteryguard.data.db.dao.BatteryLogDao
import com.pin.batteryguard.data.db.dao.ExceptionListDao
import com.pin.batteryguard.data.db.dao.ForceStopLogDao
import com.pin.batteryguard.data.db.dao.FrozenAppDao
import com.pin.batteryguard.data.db.dao.MonitoringStateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    fun provideBatteryLogDao(db: AppDatabase): BatteryLogDao {
        return db.batteryLogDao()
    }

    @Provides
    fun provideAppUsageDao(db: AppDatabase): AppUsageDao {
        return db.appUsageDao()
    }

    @Provides
    fun provideForceStopLogDao(db: AppDatabase): ForceStopLogDao {
        return db.forceStopLogDao()
    }

    @Provides
    fun provideExceptionListDao(db: AppDatabase): ExceptionListDao {
        return db.exceptionListDao()
    }

    @Provides
    fun provideFrozenAppDao(db: AppDatabase): FrozenAppDao {
        return db.frozenAppDao()
    }

    @Provides
    fun provideMonitoringStateDao(db: AppDatabase): MonitoringStateDao = db.monitoringStateDao()

    @Provides
    fun provideAppPolicyOverrideDao(db: AppDatabase): AppPolicyOverrideDao = db.appPolicyOverrideDao()

    @Provides
    fun provideAppShieldSnapshotDao(db: AppDatabase): com.pin.batteryguard.data.db.dao.AppShieldSnapshotDao = db.appShieldSnapshotDao()

    @Provides
    fun provideShieldedAppDao(db: AppDatabase): com.pin.batteryguard.data.db.dao.ShieldedAppDao = db.shieldedAppDao()

    @Provides
    fun provideXiaomiFixSnapshotDao(db: AppDatabase): com.pin.batteryguard.data.db.dao.XiaomiFixSnapshotDao = db.xiaomiFixSnapshotDao()
}
