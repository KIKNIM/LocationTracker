package com.locationtracker.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.locationtracker.data.location.LocationMode
import com.locationtracker.ui.viewmodel.MainViewModel
import com.locationtracker.util.BatteryHelper
import com.locationtracker.util.PermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onStartTracking: (LocationMode) -> Unit,
    onStopTracking: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenBattery: () -> Unit,
    onOpenBackgroundSettings: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val isTracking by viewModel.isTracking.collectAsState()
    val mode by viewModel.currentMode.collectAsState()
    val locText by viewModel.locationText.collectAsState()
    val dates by viewModel.dates.collectAsState()
    val selDate by viewModel.selectedDate.collectAsState()
    val segments by viewModel.segments.collectAsState()
    val dist by viewModel.distance.collectAsState()
    val count by viewModel.recordCount.collectAsState()

    val ctx = LocalContext.current
    val hasLoc = PermissionHelper.hasLocation(ctx)
    val hasBg = PermissionHelper.hasBackground(ctx)
    val hasNotif = PermissionHelper.hasNotification(ctx)
    val ignoreBatt = BatteryHelper.isOptimized(ctx)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🦐 虾米位置追踪") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── 状态卡片 ──
            item {
                Card(Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(4.dp)) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = MaterialTheme.shapes.extraLarge,
                            color = if (isTracking) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(Icons.Default.MyLocation, null,
                                tint = if (isTracking) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (isTracking) "追踪中" else "已停止",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isTracking) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(locText, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // ── 启动/停止按钮 ──
            item {
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (isTracking) onStopTracking() else onStartTracking(mode)
                    },
                    Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isTracking) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        if (isTracking) Icons.Default.Pause else Icons.Default.PlayArrow,
                        null, Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isTracking) "停止追踪" else "开始追踪", fontSize = 18.sp)
                }
            }

            // ── 模式选择 ──
            item {
                Spacer(Modifier.height(16.dp))
                Text("定位模式", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModeCard(LocationMode.BALANCED, mode == LocationMode.BALANCED,
                        { viewModel.switchMode(LocationMode.BALANCED) }, Modifier.weight(1f))
                    ModeCard(LocationMode.HIGH, mode == LocationMode.HIGH,
                        { viewModel.switchMode(LocationMode.HIGH) }, Modifier.weight(1f))
                }
            }

            // ── 权限状态 ──
            item {
                Spacer(Modifier.height(16.dp))
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )) {
                    Column(Modifier.padding(14.dp)) {
                        Text("后台保活设置", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        PermRow("位置权限", hasLoc, onRequestPermissions)
                        PermRow("后台定位 (始终允许)", hasBg, onOpenBackgroundSettings)
                        PermRow("忽略电池优化", ignoreBatt, onOpenBattery)
                        PermRow("通知权限 (A13+)", hasNotif) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                (ctx as ComponentActivity).let { act ->
                                    act.activityResultRegistry.let { /* via launcher */ }
                                }
                            }
                        }
                    }
                }
            }

            // ── 轨迹回放 ──
            if (dates.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Text("历史轨迹", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                }
                items(dates.take(30)) { date ->
                    Card(
                        onClick = { viewModel.selectDate(date) },
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (date == selDate) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(date, fontWeight = if (date == selDate) FontWeight.Bold else FontWeight.Normal)
                            if (date == selDate) {
                                Text("%.1f km | %d 点".format(dist / 1000, count), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                // 选中日期的线段信息
                if (segments.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Text("线段: ${segments.size} 段 (实线: ${segments.count { it.type == com.locationtracker.util.TrackSplitter.LineType.SOLID }}, 虚线: ${segments.count { it.type == com.locationtracker.util.TrackSplitter.LineType.DASHED }})",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { viewModel.deleteDate(selDate) }) {
                            Text("删除当天数据", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeCard(mode: LocationMode, sel: Boolean, onClick: () -> Unit, mod: Modifier) {
    Card(onClick = onClick, modifier = mod,
        colors = CardDefaults.cardColors(
            containerColor = if (sel) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (sel) CardDefaults.outlinedCardBorder() else null
    ) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (mode == LocationMode.HIGH) Icons.Default.LocationOn else Icons.Default.BatterySaver,
                null, tint = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(if (mode == LocationMode.HIGH) "高精度" else "均衡省电",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
            Text(if (mode == LocationMode.HIGH) "~5s 更新" else "~30s 更新",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PermRow(label: String, granted: Boolean, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = MaterialTheme.shapes.extraSmall,
            color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(8.dp)) {}
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        TextButton(onClick = action) {
            Text(if (granted) "✓" else "去设置",
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        }
    }
}
