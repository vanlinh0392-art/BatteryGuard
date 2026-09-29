package com.pin.batteryguard.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pin.batteryguard.data.db.entity.AppPolicyOverride

@Dao
interface AppPolicyOverrideDao {
    @Query("SELECT allowActiveUseStop FROM app_policy_overrides WHERE userId = :userId AND packageName = :packageName LIMIT 1")
    suspend fun isActiveUseStopAllowed(userId: Int, packageName: String): Boolean?

    @Query("SELECT * FROM app_policy_overrides")
    suspend fun getAll(): List<AppPolicyOverride>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(override: AppPolicyOverride)
}
