package com.pin.batteryguard.ui.screen.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pin.batteryguard.shizuku.ShizukuStatus
import com.pin.batteryguard.ui.components.AppUsageCard
import com.pin.batteryguard.ui.components.BatteryChart
import com.pin.batteryguard.ui.components.BatteryGauge
import com.pin.batteryguard.ui.theme.BatteryFull
import com.pin.batteryguard.ui.theme.BatteryLow

@Composable
fun DashboardScreen(
    onNavigateToApps: () -> Unit,
    onNavigateToSetup: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    // Chuyển hướng đến setup nếu chưa hoàn thành
    LaunchedEffect(uiState.isSetupCompleted) {
        if (uiState.isSetupCompleted == false) {
            onNavigateToSetup()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshBatteryState()
    }

    if (uiState.isSetupCompleted == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        ) {
            Text(
                text = "BatteryGuard",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            
            // Chỉ báo trạng thái Shizuku
            val statusColor = if (uiState.shizukuStatus == ShizukuStatus.READY) BatteryFull else BatteryLow
            val statusLabel = when (uiState.shizukuStatus) {
                ShizukuStatus.READY -> "Shizuku: Sẵn sàng"
                else -> "Shizuku: Chưa kết nối"
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(statusColor.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
        }

        // Circular Gauge
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        ) {
            BatteryGauge(
                level = uiState.batteryState.level,
                temperature = uiState.batteryState.temperature,
                isCharging = uiState.batteryState.isCharging
            )
        }

        // Stats Card (Giám sát master + logs)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (uiState.isMonitoring) "Tự động giám sát" else "Tạm dừng giám sát",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "Quét khi khóa máy",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = uiState.isMonitoring,
                    onCheckedChange = { viewModel.toggleMonitoring(it) }
                )
            }
        }

        // Quick Stats Row (Dừng hôm nay + Tiết kiệm pin)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Dừng hôm nay", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text("${uiState.stoppedToday} apps", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Tiết kiệm ước tính", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Text(String.format("~%.1f%%", uiState.savedPercent), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BatteryFull)
                }
            }
        }

        // Lịch sử đồ thị pin 24h
        Text(
            text = "Lịch sử pin 24h",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )
        if (uiState.batteryHistory.isNotEmpty()) {
            BatteryChart(history = uiState.batteryHistory)
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth().height(120.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("Đang thu thập dữ liệu", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }

        // Lịch sử dừng ứng dụng
        Text(
            text = "Lịch sử dừng app",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
        )
        if (uiState.recentForceStopLogs.isNotEmpty()) {
            uiState.recentForceStopLogs.forEach { log ->
                ForceStopLogItem(log = log)
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Chưa có lịch sử", fontSize = 12.sp)
                }
            }
        }

        // Top ngốn pin section
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 8.dp)
                .clickable { onNavigateToApps() }
        ) {
            Text(
                text = "App ngốn pin nhất",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ChevronRight, contentDescription = "Xem tất cả")
        }

        if (uiState.topDrainingApps.isNotEmpty()) {
            uiState.topDrainingApps.forEach { appInfo ->
                AppUsageCard(
                    appInfo = appInfo,
                    onForceStop = { viewModel.forceStopApp(appInfo) },
                    onFreeze = { viewModel.freezeApp(appInfo) },
                    onAddException = { viewModel.addToException(appInfo.packageName, appInfo.appName) },
                    onAllowActiveUseStop = { viewModel.allowActiveUseStop(appInfo) }
                )
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Không có app ngốn pin", fontSize = 12.sp)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ForceStopLogItem(log: com.pin.batteryguard.data.db.entity.ForceStopLog) {
    val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(log.timestamp))
    val dateStr = java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault())
        .format(java.util.Date(log.timestamp))
    val actionLabel = if (log.action == "freeze") "Đóng băng" else "Dừng"
    val statusColor = if (log.success) BatteryFull else BatteryLow
    val statusText = if (log.success) "✓" else "✗"

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
        ),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            Text(
                text = statusText,
                color = statusColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.width(20.dp)
            )
            // App name + action
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    text = log.appName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                val reasonLabel = if (log.reason.contains("manual")) "Thủ công" else "Tự động"
                Text(
                    text = "$actionLabel • $reasonLabel",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1
                )
            }
            // Drain info
            if (log.ratePercentPerHour > 0.0) {
                Text(
                    text = String.format("%.1f%%/h", log.ratePercentPerHour),
                    fontSize = 11.sp,
                    color = BatteryLow,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            // Time
            Column(horizontalAlignment = Alignment.End) {
                Text(text = timeStr, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = dateStr,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}
