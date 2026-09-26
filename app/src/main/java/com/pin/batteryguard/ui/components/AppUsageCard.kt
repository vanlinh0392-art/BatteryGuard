package com.pin.batteryguard.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.pin.batteryguard.domain.model.AppBatteryInfo

@Composable
fun AppUsageCard(
    appInfo: AppBatteryInfo,
    onForceStop: () -> Unit,
    onFreeze: () -> Unit,
    onAddException: () -> Unit,
    onAllowActiveUseStop: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // App Icon
                if (appInfo.appIcon != null) {
                    val bitmap = remember(appInfo.userId, appInfo.packageName) {
                        appInfo.appIcon.toBitmap().asImageBitmap()
                    }
                    Image(
                        bitmap = bitmap,
                        contentDescription = appInfo.appName,
                        modifier = Modifier.size(42.dp)
                    )
                } else {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(alpha = 0.2f)),
                        modifier = Modifier.size(42.dp)
                    ) {}
                }

                Spacer(modifier = Modifier.width(12.dp))

                // App Name + package
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = appInfo.appName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        if (appInfo.isException) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = "Whitelist",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (appInfo.isFrozen) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Filled.AcUnit,
                                contentDescription = "Frozen",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = "${appInfo.packageName} · user ${appInfo.userId} · UID ${appInfo.uid}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                // Battery Drain
                Text(
                    text = String.format("%.2f%%/h", appInfo.ratePercentPerHour),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (appInfo.decision.contains("active_use") && !appInfo.activeUseOverride) {
                OutlinedButton(onClick = onAllowActiveUseStop, modifier = Modifier.fillMaxWidth()) {
                    Text("Cho phép tự dừng app này dù có foreground service", fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Progress bar
            LinearProgressIndicator(
                progress = { (appInfo.ratePercentPerHour / 1.5).toFloat().coerceIn(0f, 1f) },
                color = MaterialTheme.colorScheme.error,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = String.format("Delta %.2f mAh · %s", appInfo.deltaMah, appInfo.decision.ifBlank { "recorded" }),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            if (appInfo.evidence.isNotBlank()) {
                Text(
                    text = appInfo.evidence,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action row
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                val foreTimeText = formatDuration(appInfo.foregroundTimeMs)
                val backTimeText = formatDuration(appInfo.backgroundTimeMs)
                
                Text(
                    text = "Màn hình: $foreTimeText | Ngầm: $backTimeText",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.weight(1f))

                if (!appInfo.isException && appInfo.isActionAllowed) {
                    if (appInfo.isFrozen) {
                        OutlinedButton(
                            onClick = onFreeze, // Click unfreeze
                            contentPadding = ButtonDefaults.ContentPadding,
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Unfreeze", modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Bỏ đóng băng", fontSize = 11.sp)
                        }
                    } else {
                        IconButton(onClick = onForceStop, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = "Force stop",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        IconButton(onClick = onFreeze, modifier = Modifier.size(32.dp)) {
                            Icon(
                                imageVector = Icons.Filled.AcUnit,
                                contentDescription = "Freeze",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
                
                if (!appInfo.isException && appInfo.isActionAllowed) {
                    IconButton(onClick = onAddException, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "Whitelist add",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0p"
    val totalSecs = ms / 1000
    val hours = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${mins}p"
        mins > 0 -> "${mins}p"
        else -> "${totalSecs}s"
    }
}
