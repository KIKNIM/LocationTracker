package com.locationtracker

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.locationtracker.data.location.LocationMode
import com.locationtracker.service.LocationService
import com.locationtracker.ui.MainScreen
import com.locationtracker.util.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var showPrivacy = true

    // ─── 权限 Launchers ──────────────────────────

    private val fgLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) {
            requestBackground()
        }
    }

    private val bgLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted && PermissionHelper.isR()) {
            // Android 11+ 被拒 → 引导到设置
            openSettings()
        }
    }

    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 通知权限——不给也能跑，前台服务通知降级 */ }

    // ─── 生命周期 ──────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (getSharedPreferences("app", MODE_PRIVATE).getBoolean("privacy_ok", false)) {
            showPrivacy = false
        }

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    var showPrivacyDialog by remember { mutableStateOf(showPrivacy) }

                    MainScreen(
                        onStartTracking = { mode -> startTracking(mode) },
                        onStopTracking = { stopTracking() },
                        onRequestPermissions = { requestAll() },
                        onOpenBattery = { openBatterySettings() },
                        onOpenBackgroundSettings = { openSettings() }
                    )

                    if (showPrivacyDialog) {
                        PrivacyDialog(
                            onAccept = {
                                getSharedPreferences("app", MODE_PRIVATE)
                                    .edit().putBoolean("privacy_ok", true).apply()
                                showPrivacyDialog = false
                            },
                            onReject = { finish() }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 如果后台权限在设置中已开启，自动恢复
    }

    // ─── 权限申请 ──────────────────────────

    private fun requestAll() {
        if (!PermissionHelper.hasLocation(this)) {
            fgLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            requestBackground()
        }
    }

    private fun requestBackground() {
        if (PermissionHelper.hasBackground(this)) {
            requestNotification(); return
        }
        if (PermissionHelper.isQ()) {
            // Android 10: 可以直接弹窗
            bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else if (PermissionHelper.isR()) {
            // Android 11+: 引导到设置
            openSettings()
        }
    }

    private fun requestNotification() {
        if (PermissionHelper.isT() && !PermissionHelper.hasNotification(this)) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // ─── 导航 ──────────────────────────

    private fun openSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
        )
    }

    private fun openBatterySettings() {
        startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
        )
    }

    // ─── 追踪控制 ──────────────────────────

    private fun startTracking(mode: LocationMode) {
        LocationService.start(this, mode)
        // 保存模式供开机自启恢复
        LocationService.prefs.edit().putString("last_mode", mode.name).apply()
    }

    private fun stopTracking() {
        LocationService.stop(this)
    }
}
