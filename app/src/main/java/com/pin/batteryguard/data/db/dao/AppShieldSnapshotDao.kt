package com.pin.batteryguard.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pin.batteryguard.data.db.entity.AppShieldSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppShieldSnapshotDao {
    @Query("SELECT * FROM app_shield_snapshots WHERE id = 1")
    suspend fun getSnapshot(): AppShieldSnapshotEntity?

    @Query("SELECT * FROM app_shield_snapshots WHERE id = 1")
    fun getSnapshotFlow(): Flow<AppShieldSnapshotEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSnapshot(snapshot: AppShieldSnapshotEntity)

    @Query("UPDATE app_shield_snapshots SET isCurrentlyHidden = :hidden WHERE id = 1")
    suspend fun setHidden(hidden: Boolean)

    @Query("DELETE FROM app_shield_snapshots WHERE id = 1")
    suspend fun clearSnapshot()
}
