package com.pin.batteryguard.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.pin.batteryguard.data.db.entity.MonitoringSession
import com.pin.batteryguard.data.db.entity.UidBaseline

@Dao
interface MonitoringStateDao {
    @Query("SELECT * FROM monitoring_sessions WHERE id = 1 LIMIT 1")
    suspend fun getSession(): MonitoringSession?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(session: MonitoringSession)

    @Query("DELETE FROM monitoring_sessions")
    suspend fun clearSession()

    @Query("SELECT * FROM uid_baselines WHERE sessionId = 1")
    suspend fun getBaselines(): List<UidBaseline>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBaselines(baselines: List<UidBaseline>)

    @Query("DELETE FROM uid_baselines WHERE sessionId = 1")
    suspend fun clearBaselines()

    @Transaction
    suspend fun replaceState(session: MonitoringSession, baselines: List<UidBaseline>) {
        clearBaselines()
        upsertSession(session)
        if (baselines.isNotEmpty()) insertBaselines(baselines)
    }

    @Transaction
    suspend fun clearState() {
        clearBaselines()
        clearSession()
    }
}
