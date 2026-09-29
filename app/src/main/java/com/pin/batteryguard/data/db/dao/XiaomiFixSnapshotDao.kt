package com.pin.batteryguard.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pin.batteryguard.data.db.entity.XiaomiFixSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface XiaomiFixSnapshotDao {
    @Query("SELECT * FROM xiaomi_fix_snapshots")
    fun getAllFlow(): Flow<List<XiaomiFixSnapshotEntity>>

    @Query("SELECT * FROM xiaomi_fix_snapshots")
    suspend fun getAll(): List<XiaomiFixSnapshotEntity>

    @Query("SELECT * FROM xiaomi_fix_snapshots WHERE packageName = :packageName LIMIT 1")
    suspend fun getByPackage(packageName: String): XiaomiFixSnapshotEntity?

    @Query("SELECT COUNT(*) > 0 FROM xiaomi_fix_snapshots WHERE packageName = :packageName")
    suspend fun hasSnapshot(packageName: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: XiaomiFixSnapshotEntity)

    @Query("DELETE FROM xiaomi_fix_snapshots WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)
}
