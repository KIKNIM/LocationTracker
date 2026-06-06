package com.locationtracker.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.locationtracker.data.location.LocationMode
import com.locationtracker.service.LocationService
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON") return

        if (!LocationService.wasRunning) {
            Timber.i("Boot — tracking was off, skip")
            return
        }

        // 延迟 5 秒确保系统服务就绪
        android.os.Handler(ctx.mainLooper).postDelayed({
            val mode = try {
                LocationMode.valueOf(
                    LocationService.prefs.getString("last_mode", "BALANCED") ?: "BALANCED"
                )
            } catch (_: Exception) { LocationMode.BALANCED }

            try {
                val si = Intent(ctx, LocationService::class.java).apply {
                    action = "START"; putExtra("mode", mode.name)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ctx.startForegroundService(si)
                else ctx.startService(si)
                Timber.i("Auto-restarted after boot — mode=$mode")
            } catch (e: Exception) {
                Timber.e(e, "Boot auto-restart failed")
            }
        }, 5000)
    }
}
