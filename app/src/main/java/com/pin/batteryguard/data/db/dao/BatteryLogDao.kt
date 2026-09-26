package com.pin.batteryguard.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pin.batteryguard.data.db.entity.BatteryLog
import kotlinx.coroutines.flow.Flow

@Dao
interface BatteryLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: BatteryLog)

    @Query("SELECT * FROM battery_logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    fun getLogsInRange(startTime: Long, endTime: Long): Flow<List<BatteryLog>>

    @Query("SELECT * FROM battery_logs ORDER BY timestamp DESC LIMIT 1")
    fun getLatestLog(): Flow<BatteryLog?>

    @Query("SELECT * FROM battery_logs WHERE timestamp >= :since ORDER BY timestamp ASC")
    fun getLogsSince(since: Long): Flow<List<BatteryLog>>

    @Query("DELETE FROM battery_logs WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("SELECT AVG(level) FROM battery_logs WHERE timestamp BETWEEN :startTime AND :endTime")
    suspend fun getAverageBatteryLevel(startTime: Long, endTime: Long): Float?
}
