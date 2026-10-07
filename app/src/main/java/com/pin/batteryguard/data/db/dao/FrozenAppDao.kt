package com.pin.batteryguard.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pin.batteryguard.data.db.entity.FrozenApp
import kotlinx.coroutines.flow.Flow

@Dao
interface FrozenAppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: FrozenApp)

    @Query("DELETE FROM frozen_apps WHERE packageName = :packageName AND userId = :userId")
    suspend fun delete(packageName: String, userId: Int = 0)

    @Query("SELECT * FROM frozen_apps")
    fun getAllFlow(): Flow<List<FrozenApp>>

    @Query("SELECT * FROM frozen_apps")
    suspend fun getAll(): List<FrozenApp>

    @Query("SELECT EXISTS(SELECT 1 FROM frozen_apps WHERE packageName = :packageName AND userId = :userId)")
    suspend fun isFrozen(packageName: String, userId: Int = 0): Boolean

    @Query("DELETE FROM frozen_apps")
    suspend fun deleteAll()

    @Query("SELECT * FROM frozen_apps WHERE userId = :userId AND isManual = 0")
    suspend fun getAutoFrozenApps(userId: Int = 0): List<FrozenApp>

    @Query("SELECT * FROM frozen_apps WHERE userId = :userId")
    suspend fun getAllForUser(userId: Int = 0): List<FrozenApp>
}
