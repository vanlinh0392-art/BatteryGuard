package com.pin.batteryguard.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pin.batteryguard.data.db.entity.ForceStopLog
import kotlinx.coroutines.flow.Flow

@Dao
interface ForceStopLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ForceStopLog)

    @Query("SELECT * FROM force_stop_logs ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ForceStopLog>>

    @Query("SELECT * FROM force_stop_logs WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    fun getByDate(startTime: Long, endTime: Long): Flow<List<ForceStopLog>>

    @Query("SELECT COUNT(*) FROM force_stop_logs WHERE timestamp >= :since")
    fun getCountSince(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM force_stop_logs WHERE timestamp >= :since AND success = 1")
    suspend fun getSuccessfulCountSince(since: Long): Int

    @Query("DELETE FROM force_stop_logs WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("SELECT * FROM force_stop_logs WHERE packageName = :packageName AND success = 1 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSuccessfulLogForPackage(packageName: String): ForceStopLog?

    @Query("SELECT * FROM force_stop_logs WHERE packageName = :packageName AND userId = :userId AND verified = 1 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestVerifiedLog(packageName: String, userId: Int): ForceStopLog?

    @Query("SELECT COUNT(*) FROM force_stop_logs WHERE packageName = :packageName AND userId = :userId AND verified = 1 AND action = 'force_stop' AND timestamp >= :since")
    suspend fun getVerifiedCount(packageName: String, userId: Int, since: Long): Int
}
