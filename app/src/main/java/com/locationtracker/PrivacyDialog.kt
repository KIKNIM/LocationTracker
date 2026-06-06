package com.locationtracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun PrivacyDialog(onAccept: () -> Unit, onReject: () -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("隐私声明", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("欢迎使用虾米位置追踪！使用前请阅读：", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                listOf(
                    "1. 本应用仅记录您的每日位置轨迹",
                    "2. 所有数据仅存储在设备本地",
                    "3. 数据库经 AES-256 加密（密钥由安全硬件管理）",
                    "4. 数据默认保留 90 天自动清理",
                    "5. 不会上传任何数据到服务器"
                ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
                Spacer(Modifier.height(12.dp))
                Text("继续使用即表示您同意以上条款。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        },
        confirmButton = { Button(onClick = onAccept, Modifier.fillMaxWidth()) { Text("同意并继续") } },
        dismissButton = { TextButton(onClick = onReject, Modifier.fillMaxWidth()) { Text("拒绝并退出", color = MaterialTheme.colorScheme.error) } }
    )
}
