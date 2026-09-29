package com.pin.batteryguard.data.repository

import com.pin.batteryguard.data.db.dao.AppUsageDao
import com.pin.batteryguard.data.db.dao.AppPolicyOverrideDao
import com.pin.batteryguard.data.db.entity.AppPolicyOverride
import com.pin.batteryguard.data.db.dao.BatteryLogDao
import com.pin.batteryguard.data.db.dao.ForceStopLogDao
import com.pin.batteryguard.data.db.entity.AppUsageLog
import com.pin.batteryguard.data.db.entity.BatteryLog
import com.pin.batteryguard.data.db.entity.ForceStopLog
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryRepository @Inject constructor(
    private val batteryLogDao: BatteryLogDao,
    private val appUsageDao: AppUsageDao,
    private val forceStopLogDao: ForceStopLogDao,
    private val appPolicyOverrideDao: AppPolicyOverrideDao
) {
    fun getLogsInRange(startTime: Long, endTime: Long): Flow<List<BatteryLog>> =
        batteryLogDao.getLogsInRange(startTime, endTime)

    fun getLatestLog(): Flow<BatteryLog?> = batteryLogDao.getLatestLog()

    fun getLogsSince(since: Long): Flow<List<BatteryLog>> = batteryLogDao.getLogsSince(since)

    suspend fun insertBatteryLog(log: BatteryLog) = batteryLogDao.insert(log)

    suspend fun insertAppUsageLogs(logs: List<AppUsageLog>) = appUsageDao.insertAll(logs)

    fun getTopDraining(limit: Int, startTime: Long, endTime: Long): Flow<List<AppUsageLog>> =
        appUsageDao.getTopDraining(limit, startTime, endTime)

    fun getLatestAppUsagePeriod(): Flow<List<AppUsageLog>> = appUsageDao.getLatestPeriod()

    suspend fun insertForceStopLog(log: ForceStopLog) = forceStopLogDao.insert(log)

    fun getAllForceStopLogs(): Flow<List<ForceStopLog>> = forceStopLogDao.getAll()

    fun getForceStopLogsByDate(startTime: Long, endTime: Long): Flow<List<ForceStopLog>> =
        forceStopLogDao.getByDate(startTime, endTime)

    fun getForceStopCountSince(since: Long): Flow<Int> = forceStopLogDao.getCountSince(since)

    suspend fun getForceStopCountSinceDirect(since: Long): Int = forceStopLogDao.getSuccessfulCountSince(since)

    suspend fun getLatestSuccessfulLogForPackage(packageName: String): ForceStopLog? =
        forceStopLogDao.getLatestSuccessfulLogForPackage(packageName)

    suspend fun getLatestVerifiedLog(packageName: String, userId: Int): ForceStopLog? =
        forceStopLogDao.getLatestVerifiedLog(packageName, userId)

    suspend fun getVerifiedStopCount(packageName: String, userId: Int, since: Long): Int =
        forceStopLogDao.getVerifiedCount(packageName, userId, since)

    suspend fun isActiveUseStopAllowed(packageName: String, userId: Int): Boolean =
        appPolicyOverrideDao.isActiveUseStopAllowed(userId, packageName) ?: false

    suspend fun getAllActiveUseOverrides(): Map<String, Boolean> =
        appPolicyOverrideDao.getAll().associate { "${it.userId}:${it.packageName}" to it.allowActiveUseStop }

    suspend fun setActiveUseStopAllowed(packageName: String, userId: Int, allowed: Boolean) =
        appPolicyOverrideDao.upsert(AppPolicyOverride(userId, packageName, allowed))

    suspend fun cleanupOldData(retentionDays: Int) {
        val cutoffTime = System.currentTimeMillis() - (retentionDays * 24L * 60L * 60L * 1000L)
        batteryLogDao.deleteOlderThan(cutoffTime)
        appUsageDao.deleteOlderThan(cutoffTime)
        forceStopLogDao.deleteOlderThan(cutoffTime)
    }
}
