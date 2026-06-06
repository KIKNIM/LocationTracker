package com.locationtracker.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 权限助手 — Android 9 / 10 / 11 / 12 / 13 / 14 / 15 / 16 全系适配
 *
 * 关键差异：
 * - Android ≤9: 仅需 FINE/COARSE
 * - Android 10:  首次引入 BACKGROUND_LOCATION，可直接弹窗
 * - Android 11+: 必须分两次申请 — 先前台再后台，第二次只能引导到设置页
 * - Android 12:  精确位置开关，用户可在弹窗中关闭
 * - Android 13:  POST_NOTIFICATIONS
 * - Android 14+: FOREGROUND_SERVICE_TYPE_LOCATION 必须声明
 */
object PermissionHelper {

    private val FG = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    fun hasLocation(ctx: Context): Boolean =
        FG.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

    fun hasBackground(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun hasNotification(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        else true

    fun isQ()  = Build.VERSION.SDK_INT == Build.VERSION_CODES.Q
    fun isR()  = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    fun isS()  = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    fun isT()  = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    fun isU()  = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    /** Android 11+ 需要引导用户到设置页 */
    fun needsSettingsRedirect(ctx: Context): Boolean =
        isR() && hasLocation(ctx) && !hasBackground(ctx)
}
