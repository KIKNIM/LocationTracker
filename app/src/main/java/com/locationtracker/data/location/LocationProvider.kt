package com.locationtracker.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.*
import com.locationtracker.data.model.UserActivityState
import com.locationtracker.util.PermissionHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val client: FusedLocationProviderClient
) {
    private val HIGH_MS = 5_000L
    private val BALANCED_MS = 30_000L
    private val STATIONARY_MS = 5 * 60_000L
    private val DRIVING_MS = 10_000L
    private val FASTEST = 2_000L
    private val DISPLACEMENT = 10f

    private var callback: LocationCallback? = null
    private var mode: LocationMode = LocationMode.BALANCED
    private var interval = BALANCED_MS

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive

    private val _latest = MutableStateFlow<Location?>(null)
    val latest: StateFlow<Location?> = _latest

    @SuppressLint("MissingPermission")
    fun start(m: LocationMode = LocationMode.BALANCED) {
        if (!PermissionHelper.hasLocation(ctx)) return
        mode = m
        interval = if (m == LocationMode.HIGH) HIGH_MS else BALANCED_MS
        stop()

        callback = object : LocationCallback() {
            override fun onLocationResult(r: LocationResult) {
                r.lastLocation?.let { _latest.value = it }
            }
        }
        client.requestLocationUpdates(
            buildRequest(), callback!!, Looper.getMainLooper()
        )
        _isActive.value = true
        Timber.i("Tracking started — mode=$mode interval=${interval}ms")
    }

    fun stop() {
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
        _isActive.value = false
    }

    fun adjustInterval(state: UserActivityState) {
        if (!_isActive.value) return
        val new = when (state) {
            UserActivityState.STATIONARY -> STATIONARY_MS
            UserActivityState.DRIVING -> DRIVING_MS
            UserActivityState.WALKING -> if (mode == LocationMode.HIGH) HIGH_MS else BALANCED_MS
            UserActivityState.RUNNING -> HIGH_MS
            UserActivityState.BICYCLING -> HIGH_MS
            else -> if (mode == LocationMode.HIGH) HIGH_MS else BALANCED_MS
        }
        if (new == interval) return
        interval = new
        stop()
        start(mode)
        Timber.i("Interval adjusted → ${new}ms (state=$state)")
    }

    fun switchMode(m: LocationMode) {
        mode = m
        if (_isActive.value) { stop(); start(m) }
    }

    @SuppressLint("MissingPermission")
    fun locationFlow(): Flow<Location> = callbackFlow {
        if (!PermissionHelper.hasLocation(ctx)) close()
        val cb = object : LocationCallback() {
            override fun onLocationResult(r: LocationResult) { r.lastLocation?.let { trySend(it) } }
        }
        client.requestLocationUpdates(buildRequest(), cb, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(cb) }
    }

    @SuppressLint("MissingPermission")
    suspend fun lastLocation(): Location? {
        if (!PermissionHelper.hasLocation(ctx)) return null
        return try {
            runCatching {
                com.google.android.gms.tasks.Tasks.await(
                    client.getCurrentLocation(
                        if (mode == LocationMode.HIGH) Priority.PRIORITY_HIGH_ACCURACY
                        else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                        null
                    )
                )
            }.getOrNull()
        } catch (e: Exception) {
            Timber.w(e, "lastKnownLocation failed")
            null
        }
    }

    private fun buildRequest() = LocationRequest.Builder(
        if (mode == LocationMode.HIGH) Priority.PRIORITY_HIGH_ACCURACY
        else Priority.PRIORITY_BALANCED_POWER_ACCURACY,
        interval
    ).setMinUpdateIntervalMillis(FASTEST)
        .setMaxUpdateDelayMillis(interval * 2)
        .setSmallestDisplacement(DISPLACEMENT)
        .build()
}

enum class LocationMode { HIGH, BALANCED }
