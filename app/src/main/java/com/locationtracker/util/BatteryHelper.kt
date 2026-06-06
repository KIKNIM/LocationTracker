package com.locationtracker.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import timber.log.Timber

/**
 * 国产 ROM 电池优化跳转 — MIUI / ColorOS / OriginOS / EMUI / OneUI
 *
 * ⚠️ 各厂商系统版本差异大，捕获异常后回退到通用设置页
 */
object BatteryHelper {

    fun isOptimized(ctx: Context): Boolean {
        return (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun requestIgnore(ctx: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${ctx.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    fun openManufacturer(ctx: Context): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        return try {
            when {
                m.contains("xiaomi") || m.contains("redmi") -> openMiui(ctx)
                m.contains("oppo") || m.contains("oneplus") -> openColorOs(ctx)
                m.contains("vivo") -> openOriginOs(ctx)
                m.contains("huawei") || m.contains("honor") -> openEmui(ctx)
                m.contains("samsung") -> openSamsung(ctx)
                else -> openGeneric(ctx)
            }
        } catch (e: Exception) { openGeneric(ctx) }
    }

    private fun openMiui(ctx: Context): Boolean {
        val intent = Intent().apply {
            component = ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent); return true
    }

    private fun openColorOs(ctx: Context): Boolean {
        val intent = Intent().apply {
            component = ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent); return true
    }

    private fun openOriginOs(ctx: Context): Boolean {
        val intent = Intent().apply {
            component = ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent); return true
    }

    private fun openEmui(ctx: Context): Boolean {
        val intent = Intent().apply {
            component = ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent); return true
    }

    private fun openSamsung(ctx: Context): Boolean = openGeneric(ctx)

    private fun openGeneric(ctx: Context): Boolean {
        val intent = Intent().apply {
            component = ComponentName(
                "com.android.settings",
                "com.android.settings.Settings\$HighPowerApplicationsActivity"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            ctx.startActivity(intent); true
        } catch (e: Exception) {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            try { ctx.startActivity(fallback); true } catch (e2: Exception) { false }
        }
    }
}
