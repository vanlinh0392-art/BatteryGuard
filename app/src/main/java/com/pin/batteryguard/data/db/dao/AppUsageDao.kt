package com.pin.batteryguard.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pin.batteryguard.data.db.entity.AppUsageLog
import kotlinx.coroutines.flow.Flow

@Dao
interface AppUsageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AppUsageLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(logs: List<AppUsageLog>)

    @Query("SELECT * FROM app_usage_logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY estimatedDrainPercent DESC LIMIT :limit")
    fun getTopDraining(limit: Int, startTime: Long, endTime: Long): Flow<List<AppUsageLog>>

    @Query("SELECT * FROM app_usage_logs WHERE packageName = :packageName AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getByPackage(packageName: String, startTime: Long, endTime: Long): Flow<List<AppUsageLog>>

    @Query("SELECT * FROM app_usage_logs WHERE periodEnd = (SELECT MAX(periodEnd) FROM app_usage_logs) ORDER BY ratePercentPerHour DESC")
    fun getLatestPeriod(): Flow<List<AppUsageLog>>

    @Query("DELETE FROM app_usage_logs WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("SELECT SUM(estimatedDrainPercent) FROM app_usage_logs WHERE timestamp BETWEEN :startTime AND :endTime")
    suspend fun getTotalDrainInPeriod(startTime: Long, endTime: Long): Float?
}
