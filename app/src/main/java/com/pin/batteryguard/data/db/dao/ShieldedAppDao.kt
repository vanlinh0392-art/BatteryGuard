package com.pin.batteryguard.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pin.batteryguard.data.db.entity.ShieldedAppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ShieldedAppDao {
    @Query("SELECT * FROM shielded_apps ORDER BY isPresetBank DESC, appName ASC")
    fun getAllShieldedApps(): Flow<List<ShieldedAppEntity>>

    @Query("SELECT packageName FROM shielded_apps WHERE isEnabled = 1")
    fun getEnabledPackagesFlow(): Flow<List<String>>

    @Query("SELECT packageName FROM shielded_apps WHERE isEnabled = 1")
    suspend fun getEnabledPackages(): List<String>

    @Query("SELECT * FROM shielded_apps WHERE packageName = :packageName")
    suspend fun getByPackage(packageName: String): ShieldedAppEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(app: ShieldedAppEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(apps: List<ShieldedAppEntity>)

    @Query("UPDATE shielded_apps SET isEnabled = :isEnabled WHERE packageName = :packageName")
    suspend fun setEnabled(packageName: String, isEnabled: Boolean)

    @Query("DELETE FROM shielded_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("SELECT COUNT(*) FROM shielded_apps")
    suspend fun count(): Int
}
