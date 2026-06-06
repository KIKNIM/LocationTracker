package com.locationtracker.data.location

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.google.android.gms.location.*
import com.locationtracker.data.model.UserActivityState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 活动识别 — Activity Transition API
 *
 * 非 Geofencing。检测用户的运动状态变化，用于动态调节定位频率。
 * 静止需持续 3 分钟才确认，避免短暂停车触发频繁切换。
 */
@Singleton
class ActivityWatcher @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val client: ActivityRecognitionClient
) {
    private val STILL_CONFIRM_MS = 3 * 60_000L
    private var stillCandidateAt: Long? = null
    private var registered = false

    private val _state = MutableStateFlow(UserActivityState.UNKNOWN)
    val state: StateFlow<UserActivityState> = _state

    fun start() {
        if (registered) return
        val transitions = listOf(
            transition(DetectedActivity.STILL, ActivityTransition.ACTIVITY_TRANSITION_ENTER),
            transition(DetectedActivity.STILL, ActivityTransition.ACTIVITY_TRANSITION_EXIT),
            transition(DetectedActivity.IN_VEHICLE, ActivityTransition.ACTIVITY_TRANSITION_ENTER),
            transition(DetectedActivity.ON_FOOT, ActivityTransition.ACTIVITY_TRANSITION_ENTER),
            transition(DetectedActivity.ON_BICYCLE, ActivityTransition.ACTIVITY_TRANSITION_ENTER),
            transition(DetectedActivity.RUNNING, ActivityTransition.ACTIVITY_TRANSITION_ENTER),
        )
        val pi = PendingIntent.getBroadcast(
            ctx, 0, Intent(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        client.requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), pi)
            .addOnSuccessListener { registered = true; Timber.i("Activity transitions ON") }
            .addOnFailureListener { Timber.e(it, "Activity transitions failed") }
    }

    fun stop() {
        if (!registered) return
        val pi = PendingIntent.getBroadcast(
            ctx, 0, Intent(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        client.removeActivityTransitionUpdates(pi)
        registered = false
    }

    fun onTransition(event: ActivityTransitionEvent) {
        val new = map(event.activityType)
        if (new != UserActivityState.STATIONARY) {
            stillCandidateAt = null
            if (_state.value != new) { _state.value = new; Timber.i("Activity → $new") }
        } else if (_state.value != UserActivityState.STATIONARY) {
            if (stillCandidateAt == null) stillCandidateAt = SystemClock.elapsedRealtime()
            else if (SystemClock.elapsedRealtime() - stillCandidateAt!! >= STILL_CONFIRM_MS) {
                _state.value = UserActivityState.STATIONARY
                stillCandidateAt = null
                Timber.i("Activity → STATIONARY (confirmed after 3m)")
            }
        }
    }

    private fun map(type: Int) = when (type) {
        DetectedActivity.STILL -> UserActivityState.STATIONARY
        DetectedActivity.WALKING, DetectedActivity.ON_FOOT -> UserActivityState.WALKING
        DetectedActivity.RUNNING -> UserActivityState.RUNNING
        DetectedActivity.IN_VEHICLE -> UserActivityState.DRIVING
        DetectedActivity.ON_BICYCLE -> UserActivityState.BICYCLING
        else -> UserActivityState.UNKNOWN
    }

    private fun transition(type: Int, trans: Int) = ActivityTransition.Builder()
        .setActivityType(type).setActivityTransition(trans).build()

    companion object {
        const val ACTION = "com.locationtracker.ACTIVITY_TRANSITION"
    }
}

class ActivityTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        // ActivityWatcher 通过 Hilt 注入后在 Service 中手动处理
        // 这里只是接收广播，实际逻辑由 Service 中的 Watcher 消费
    }
}
