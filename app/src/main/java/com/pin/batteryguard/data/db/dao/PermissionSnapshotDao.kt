package com.pin.batteryguard.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pin.batteryguard.data.db.entity.PermissionSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PermissionSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: PermissionSnapshotEntity)

    @Query("SELECT * FROM permission_snapshots WHERE packageName = :packageName ORDER BY capturedAt DESC")
    fun getSnapshotsForPackage(packageName: String): Flow<List<PermissionSnapshotEntity>>

    @Query("SELECT * FROM permission_snapshots WHERE packageName = :packageName ORDER BY capturedAt DESC LIMIT 1")
    suspend fun getLatestSnapshot(packageName: String): PermissionSnapshotEntity?

    @Query("DELETE FROM permission_snapshots WHERE packageName = :packageName")
    suspend fun deleteSnapshotsForPackage(packageName: String)

    @Query("DELETE FROM permission_snapshots WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM permission_snapshots WHERE packageName = :packageName")
    suspend fun countSnapshotsForPackage(packageName: String): Int

    /**
     * Tự động dọn dẹp các bản ghi cũ, chỉ giữ lại tối đa keepLimit bản ghi gần nhất cho mỗi app (Zero-Bloat Database).
     */
    @Query("""
        DELETE FROM permission_snapshots 
        WHERE packageName = :packageName 
          AND id NOT IN (
              SELECT id FROM permission_snapshots 
              WHERE packageName = :packageName 
              ORDER BY capturedAt DESC 
              LIMIT :keepLimit
          )
    """)
    suspend fun pruneOldSnapshots(packageName: String, keepLimit: Int = 5)
}
