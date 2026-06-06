package com.locationtracker.data.db

import androidx.paging.PagingSource
import androidx.room.*
import com.locationtracker.data.model.LocationRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: LocationRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<LocationRecord>)

    /** Paging 3 分页查询 — 防 OOM */
    @Query("SELECT * FROM location_records WHERE date = :date ORDER BY timestamp ASC")
    fun pagedByDate(date: String): PagingSource<Int, LocationRecord>

    @Query("SELECT * FROM location_records WHERE date = :date ORDER BY timestamp ASC")
    suspend fun byDate(date: String): List<LocationRecord>

    @Query("SELECT * FROM location_records WHERE date >= :from AND date <= :to ORDER BY timestamp ASC")
    suspend fun byDateRange(from: String, to: String): List<LocationRecord>

    /** 中断标记点查询 */
    @Query("SELECT * FROM location_records WHERE date = :date AND isInterrupted = 1 ORDER BY timestamp ASC")
    suspend fun interruptionMarks(date: String): List<LocationRecord>

    @Query("SELECT DISTINCT date FROM location_records ORDER BY date DESC")
    fun allDates(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM location_records WHERE date = :date")
    fun countByDate(date: String): Flow<Int>

    @Query("DELETE FROM location_records WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM location_records WHERE date < :before")
    suspend fun deleteOlderThan(before: String)
}
