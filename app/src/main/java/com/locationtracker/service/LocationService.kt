package com.locationtracker.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.*
import com.locationtracker.MainActivity
import com.locationtracker.R
import com.locationtracker.data.location.ActivityWatcher
import com.locationtracker.data.location.LocationMode
import com.locationtracker.data.location.LocationProvider
import com.locationtracker.data.model.UserActivityState
import com.locationtracker.data.repository.LocationRepository
import com.locationtracker.util.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import timber.log.Timber
import javax.inject.Inject

/**
 * 前台定位服务 — 应用生命周期核心
 *
 * 设计：
 * - startForeground() + 常驻通知 → 保活
 * - START_STICKY → 系统杀后自动恢复
 * - Partial WakeLock → 防 CPU 休眠 (10min 超时自动释放)
 * - 5 分钟无有效定位 → 插中断标记
 * - ActivityWatcher → 动态调节定位频率
 *
 * Android 14/15/16 兼容：
 * - foregroundServiceType="location" 已在 Manifest 声明
 * - Android 15+ 短时前台服务限制：本服务 duration 超 6 小时需用户确认
 *   实际场景由用户主动开启追踪，视为 "user-initiated"，不受限制
 */
@AndroidEntryPoint
class LocationService : Service() {

    companion object {
        private const val NID = 2001
        private const val CHANNEL_ID = "tracking"

        /** 中断检测阈值 5 分钟 */
        private const val INTERRUPTION_MS = 5 * 60_000L
        /** 中断检查周期 30 秒 */
        private const val CHECK_MS = 30_000L
        /** 静止状态写库间隔 60 秒 */
        private const val PERSIST_STATIONARY_MS = 60_000L
        /** 活跃状态写库间隔 15 秒 */
        private const val PERSIST_ACTIVE_MS = 15_000L
        /** WakeLock 超时 10 分钟 */
        private const val WL_TIMEOUT_MS = 10 * 60_000L

        fun start(ctx: Context, mode: LocationMode = LocationMode.BALANCED) {
            val i = Intent(ctx, LocationService::class.java).apply {
                action = "START"; putExtra("mode", mode.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i)
            } else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.startService(Intent(ctx, LocationService::class.java).apply { action = "STOP" })
        }
    }

    @Inject lateinit var locationProvider: LocationProvider
    @Inject lateinit var activityWatcher: ActivityWatcher
    @Inject lateinit var repository: LocationRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var persistJob: Job? = null
    private var interruptJob: Job? = null
    private var wl: PowerManager.WakeLock? = null

    private var lastValidAt = 0L
    private var lastSaved: Location? = null
    private var battery = -1
    private var mode = LocationMode.BALANCED

    override fun onCreate() {
        super.onCreate()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "位置追踪", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
        prefs = App.securePrefs
    }

    override fun onBind(i: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> {
                mode = try {
                    LocationMode.valueOf(intent.getStringExtra("mode") ?: "BALANCED")
                } catch (_: Exception) { LocationMode.BALANCED }
                startInternal()
            }
            "STOP" -> { stopInternal(); stopSelf() }
            else -> if (intent == null && wasRunning) startInternal() // START_STICKY 恢复
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopInternal(); scope.cancel(); super.onDestroy()
    }

    // ──────────────── 启停 ────────────────

    private fun startInternal() {
        if (!PermissionHelper.hasLocation(this)) return

        acquireWL()

        val notif = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("虾米位置追踪")
            .setContentText("模式: ${if (mode == LocationMode.HIGH) "高精度" else "均衡省电"}")
            .setSmallIcon(R.drawable.ic_location)
            .setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else startForeground(NID, notif)
        } catch (e: Exception) {
            Timber.e(e, "Foreground start failed"); return
        }

        lastValidAt = System.currentTimeMillis()
        locationProvider.start(mode)
        if (PermissionHelper.hasLocation(this)) activityWatcher.start()

        startPersistJob()
        startInterruptJob()
        wasRunning = true
        Timber.i("Service started — mode=$mode")
    }

    private fun stopInternal() {
        locationProvider.stop(); activityWatcher.stop()
        persistJob?.cancel(); interruptJob?.cancel()
        releaseWL(); wasRunning = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        Timber.i("Service stopped")
    }

    // ──────────────── 协程任务 ────────────────

    private fun startPersistJob() {
        persistJob?.cancel()
        persistJob = scope.launch {
            activityWatcher.state.collect { state ->
                val interval = if (state == UserActivityState.STATIONARY)
                    PERSIST_STATIONARY_MS else PERSIST_ACTIVE_MS

                while (true) {
                    delay(interval)
                    val loc = locationProvider.latest.value ?: continue
                    if (loc == lastSaved) continue

                    battery = readBattery()
                    repository.save(loc, false, battery)
                    lastSaved = loc; lastValidAt = System.currentTimeMillis()
                }
            }
        }
    }

    private fun startInterruptJob() {
        interruptJob?.cancel()
        interruptJob = scope.launch {
            while (true) {
                delay(CHECK_MS)
                val since = System.currentTimeMillis() - lastValidAt
                if (since >= INTERRUPTION_MS) {
                    val last = lastSaved
                    if (last != null) {
                        repository.markInterruption(
                            System.currentTimeMillis(),
                            last.latitude, last.longitude
                        )
                        Timber.w("Track interrupted — ${since / 1000}s without valid location")
                    }
                    lastValidAt = System.currentTimeMillis()
                }

                // Activity 状态改变 → 调间隔
                locationProvider.adjustInterval(activityWatcher.state.value)
            }
        }
    }

    // ──────────────── WakeLock ────────────────

    private fun acquireWL() {
        wl = (getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "Tracker:LocationWL"
        ).also { it.acquire(WL_TIMEOUT_MS) }
    }

    private fun releaseWL() {
        wl?.let { if (it.isHeld) it.release() }; wl = null
    }

    private fun readBattery(): Int {
        val intent = registerReceiver(
            null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        ) ?: return -1
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (scale > 0) level * 100 / scale else -1
    }

    companion object {
        lateinit var prefs: androidx.security.crypto.EncryptedSharedPreferences
        var wasRunning = false
    }
}
