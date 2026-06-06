package com.locationtracker.data.repository

import android.location.Location
import androidx.paging.*
import com.locationtracker.data.db.LocationDao
import com.locationtracker.data.model.LocationRecord
import com.locationtracker.util.DouglasPeucker
import com.locationtracker.util.TrackSplitter
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(private val dao: LocationDao) {

    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    suspend fun save(location: Location, interrupted: Boolean = false, battery: Int? = null): Long {
        return dao.insert(
            LocationRecord(
                timestamp = location.time,
                date = fmt.format(Date(location.time)),
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                altitude = if (location.hasAltitude()) location.altitude else null,
                speed = if (location.hasSpeed()) location.speed else null,
                bearing = if (location.hasBearing()) location.bearing else null,
                provider = location.provider ?: "fused",
                isInterrupted = interrupted,
                batteryLevel = battery
            )
        )
    }

    suspend fun markInterruption(ts: Long, lat: Double, lng: Double): Long {
        return dao.insert(
            LocationRecord(
                timestamp = ts,
                date = fmt.format(Date(ts)),
                latitude = lat, longitude = lng,
                accuracy = -1f,
                isInterrupted = true
            )
        )
    }

    fun pagedByDate(date: String): Flow<PagingData<LocationRecord>> = Pager(
        PagingConfig(pageSize = 50, enablePlaceholders = false, prefetchDistance = 10)
    ) { dao.pagedByDate(date) }.flow

    suspend fun recordsByDate(date: String): List<LocationRecord> = dao.byDate(date)

    suspend fun simplifiedByDate(date: String, epsilon: Double = 10.0): List<LocationRecord> {
        val all = dao.byDate(date)
        return if (all.size > 100) DouglasPeucker.simplify(all, epsilon) else all
    }

    suspend fun segmentsByDate(date: String): List<TrackSplitter.TrackSegment> {
        return TrackSplitter.split(dao.byDate(date))
    }

    suspend fun distanceByDate(date: String): Double {
        return TrackSplitter.totalDistance(dao.byDate(date))
    }

    suspend fun interruptionMarks(date: String): List<LocationRecord> =
        dao.interruptionMarks(date)

    fun allDates(): Flow<List<String>> = dao.allDates()

    suspend fun cleanOld(days: Int = 90) {
        val cutoff = fmt.format(Date(System.currentTimeMillis() - days * 86400000L))
        dao.deleteOlderThan(cutoff)
    }

    suspend fun deleteDate(date: String) = dao.deleteByDate(date)
}
