package com.pin.batteryguard.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pin.batteryguard.data.db.entity.BatteryLog
import com.pin.batteryguard.domain.model.BatteryState
import com.pin.batteryguard.ui.theme.BatteryMedium

@Composable
fun BatteryChart(
    history: List<BatteryLog>,
    currentState: BatteryState? = null,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tempLineColor = BatteryMedium // Màu cam cho nhiệt độ

    val effectiveHistory = remember(history, currentState) {
        if (history.isNotEmpty()) {
            history
        } else if (currentState != null && currentState.level > 0) {
            listOf(
                BatteryLog(
                    level = currentState.level,
                    temperature = currentState.temperature,
                    voltage = currentState.voltage,
                    currentNow = currentState.currentNow,
                    currentAvg = 0,
                    isCharging = currentState.isCharging,
                    chargeType = currentState.chargeType,
                    screenOn = true,
                    timestamp = System.currentTimeMillis()
                )
            )
        } else {
            emptyList()
        }
    }

    if (effectiveHistory.isEmpty()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)),
            modifier = modifier.fillMaxWidth().height(120.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("Đang thu thập dữ liệu", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
        return
    }

    val cachedData = remember(effectiveHistory) {
        val sortedHistory = effectiveHistory.sortedBy { it.timestamp }
        val now = System.currentTimeMillis()
        val lastTime = maxOf(sortedHistory.last().timestamp, now)
        val firstTime = sortedHistory.first().timestamp

        // Cửa sổ tối thiểu 1 giờ, tối đa 24 giờ
        val isShortRange = sortedHistory.size == 1 || (lastTime - firstTime) < 15 * 60 * 1000L
        val minTime = if (isShortRange) {
            lastTime - 60 * 60 * 1000L
        } else {
            maxOf(firstTime, lastTime - 24 * 60 * 60 * 1000L)
        }
        val timeRange = (lastTime - minTime).coerceAtLeast(60 * 1000L)

        val rawLevelPoints = mutableListOf<Pair<Float, Float>>()
        val rawTempPoints = mutableListOf<Pair<Float, Float>>()

        // Điểm neo bên trái (x = 0f)
        val firstLog = sortedHistory.first()
        rawLevelPoints.add(0f to (firstLog.level / 100f).coerceIn(0f, 1f))
        rawTempPoints.add(0f to ((firstLog.temperature - 20f) / 30f).coerceIn(0f, 1f))

        // Các điểm log thực tế trong khoảng
        sortedHistory.forEach { log ->
            if (log.timestamp >= minTime) {
                val xRatio = ((log.timestamp - minTime).toFloat() / timeRange.toFloat()).coerceIn(0f, 1f)
                val yRatio = (log.level / 100f).coerceIn(0f, 1f)
                val tempRatio = ((log.temperature - 20f) / 30f).coerceIn(0f, 1f)
                rawLevelPoints.add(xRatio to yRatio)
                rawTempPoints.add(xRatio to tempRatio)
            }
        }

        // Điểm neo bên phải đến thời điểm hiện tại (x = 1f)
        val lastLog = sortedHistory.last()
        rawLevelPoints.add(1f to (lastLog.level / 100f).coerceIn(0f, 1f))
        rawTempPoints.add(1f to ((lastLog.temperature - 20f) / 30f).coerceIn(0f, 1f))

        Triple(rawLevelPoints, rawTempPoints, isShortRange)
    }

    val (levelPointsRaw, tempPointsRaw, isShortRange) = cachedData
    val latestLog = effectiveHistory.maxByOrNull { it.timestamp }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Thanh chú thích & thông số mới nhất
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(primaryColor)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Pin", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))

                Spacer(modifier = Modifier.width(12.dp))

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(tempLineColor)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Nhiệt độ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))

                Spacer(modifier = Modifier.weight(1f))

                if (latestLog != null) {
                    Text(
                        text = "${latestLog.level}% • ${String.format(java.util.Locale.US, "%.1f°C", latestLog.temperature)}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Canvas vẽ đồ thị
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
            ) {
                val width = size.width
                val height = size.height

                // 1. Grid lines ngang (25%, 50%, 75%, 100%)
                val gridLines = 4
                for (i in 1..gridLines) {
                    val y = height * (i.toFloat() / gridLines)
                    drawLine(
                        color = Color.White.copy(alpha = 0.05f),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // 2. Chuyển đổi tọa độ
                val levelPoints = levelPointsRaw.map { (xRatio, yRatio) ->
                    Offset(xRatio * width, height - yRatio * height)
                }

                val tempPoints = tempPointsRaw.map { (xRatio, yRatio) ->
                    Offset(xRatio * width, height - yRatio * height)
                }

                // 3. Đường line cho pin
                val linePath = Path().apply {
                    if (levelPoints.isNotEmpty()) {
                        moveTo(levelPoints.first().x, levelPoints.first().y)
                        for (i in 1 until levelPoints.size) {
                            lineTo(levelPoints[i].x, levelPoints[i].y)
                        }
                    }
                }

                // 4. Fill gradient phía dưới pin
                val fillPath = Path().apply {
                    if (levelPoints.isNotEmpty()) {
                        moveTo(levelPoints.first().x, height)
                        lineTo(levelPoints.first().x, levelPoints.first().y)
                        for (i in 1 until levelPoints.size) {
                            lineTo(levelPoints[i].x, levelPoints[i].y)
                        }
                        lineTo(levelPoints.last().x, height)
                        close()
                    }
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.2f), Color.Transparent)
                    )
                )

                drawPath(
                    path = linePath,
                    color = primaryColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // 5. Đường line nhiệt độ
                val tempPath = Path().apply {
                    if (tempPoints.isNotEmpty()) {
                        moveTo(tempPoints.first().x, tempPoints.first().y)
                        for (i in 1 until tempPoints.size) {
                            lineTo(tempPoints[i].x, tempPoints[i].y)
                        }
                    }
                }

                drawPath(
                    path = tempPath,
                    color = tempLineColor,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                // 6. Chấm tròn tại các điểm mốc
                levelPoints.forEach { point ->
                    drawCircle(
                        color = primaryColor,
                        radius = 2.5.dp.toPx(),
                        center = point
                    )
                }
            }

            // Trục thời gian
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val startLabel = if (isShortRange) "1h trước" else "24h trước"
                Text(startLabel, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                Text("Hiện tại", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }
        }
    }
}
