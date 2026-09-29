package com.pin.batteryguard.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.pin.batteryguard.domain.model.AppBatteryInfo
import java.util.Locale

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
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            // Tầng 1: Icon (30dp) + Tên app + Huy hiệu + Tỷ lệ tiêu thụ pin
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (appInfo.appIcon != null) {
                    val bitmap = remember(appInfo.userId, appInfo.packageName) {
                        appInfo.appIcon.toBitmap().asImageBitmap()
                    }
                    Image(
                        bitmap = bitmap,
                        contentDescription = appInfo.appName,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Gray.copy(alpha = 0.2f))
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Tên app + Huy hiệu ngoại lệ / đóng băng
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = appInfo.appName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (appInfo.isException) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "Ngoại lệ",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    if (appInfo.isFrozen) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.AcUnit,
                            contentDescription = "Đã đóng băng",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Tỷ lệ pin %/h
                val rateColor = when {
                    appInfo.ratePercentPerHour >= 1.0 -> MaterialTheme.colorScheme.error
                    appInfo.ratePercentPerHour >= 0.3 -> Color(0xFFFF9800)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                }
                Text(
                    text = String.format(Locale.US, "%.2f%%/h", appInfo.ratePercentPerHour),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = rateColor
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Tầng 2: Chỉ số cô đọng (mAh · ngầm) + Cụm nút thao tác Micro
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val backTimeText = formatDuration(appInfo.backgroundTimeMs)
                val statsText = if (appInfo.deltaMah > 0) {
                    String.format(Locale.US, "%.1f mAh · %s ngầm", appInfo.deltaMah, backTimeText)
                } else {
                    "$backTimeText ngầm"
                }

                Text(
                    text = statsText,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Cho phép tự dừng nếu active_use
                if (appInfo.decision.contains("active_use") && !appInfo.activeUseOverride) {
                    OutlinedButton(
                        onClick = onAllowActiveUseStop,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text("Tự dừng", fontSize = 10.sp)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                // Cụm action buttons micro
                if (!appInfo.isException && appInfo.isActionAllowed) {
                    if (appInfo.isFrozen) {
                        OutlinedButton(
                            onClick = onFreeze,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Mở", modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(2.dp))
                            Text("Mở", fontSize = 10.sp)
                        }
                    } else {
                        IconButton(onClick = onForceStop, modifier = Modifier.size(26.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = "Dừng",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        IconButton(onClick = onFreeze, modifier = Modifier.size(26.dp)) {
                            Icon(
                                imageVector = Icons.Filled.AcUnit,
                                contentDescription = "Đóng băng",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    IconButton(onClick = onAddException, modifier = Modifier.size(26.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = "Thêm ngoại lệ",
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Vạch pin siêu mỏng sát đáy thẻ (2dp)
            LinearProgressIndicator(
                progress = { (appInfo.ratePercentPerHour / 1.5).toFloat().coerceIn(0f, 1f) },
                color = if (appInfo.ratePercentPerHour >= 1.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
            )
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
