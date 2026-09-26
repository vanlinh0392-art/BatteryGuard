package com.pin.batteryguard.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pin.batteryguard.data.db.entity.ExceptionApp
import kotlinx.coroutines.flow.Flow

@Dao
interface ExceptionListDao {
    @Query("SELECT * FROM exception_apps ORDER BY appName ASC")
    fun getAll(): Flow<List<ExceptionApp>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: ExceptionApp)

    @Query("DELETE FROM exception_apps WHERE packageName = :packageName")
    suspend fun delete(packageName: String)

    @Query("SELECT COUNT(*) > 0 FROM exception_apps WHERE packageName = :packageName")
    suspend fun isException(packageName: String): Boolean

    @Query("SELECT packageName FROM exception_apps")
    suspend fun getExceptionPackages(): List<String>
}
