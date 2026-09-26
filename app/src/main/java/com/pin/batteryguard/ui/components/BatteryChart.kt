package com.pin.batteryguard.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.pin.batteryguard.ui.theme.ChartFill
import com.pin.batteryguard.ui.theme.ChartGrid
import com.pin.batteryguard.ui.theme.ChartLine

import androidx.compose.ui.graphics.StrokeCap

@Composable
fun BatteryChart(
    history: List<com.pin.batteryguard.data.db.entity.BatteryLog>,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tempLineColor = com.pin.batteryguard.ui.theme.BatteryMedium // Cam (nhiệt độ)

    // Tính toán tọa độ tỉ lệ (0.0 - 1.0) bên ngoài DrawScope để tránh sắp xếp & map danh sách trên mỗi khung hình draw
    val cachedData = androidx.compose.runtime.remember(history) {
        if (history.isEmpty()) return@remember null
        val sortedHistory = history.sortedBy { it.timestamp }
        val minTime = sortedHistory.first().timestamp
        val maxTime = sortedHistory.last().timestamp
        val timeRange = (maxTime - minTime).coerceAtLeast(1L)
        
        val levelPoints = sortedHistory.map { log ->
            val xRatio = (log.timestamp - minTime).toFloat() / timeRange.toFloat()
            val yRatio = log.level / 100f
            xRatio to yRatio
        }
        
        val tempPoints = sortedHistory.map { log ->
            val xRatio = (log.timestamp - minTime).toFloat() / timeRange.toFloat()
            // Map 20°C - 50°C sang 0.0 - 1.0
            val yRatio = ((log.temperature - 20f) / 30f).coerceIn(0f, 1f)
            xRatio to yRatio
        }
        
        levelPoints to tempPoints
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        val width = size.width
        val height = size.height

        // 1. Vẽ grid lines (ngang: 25%, 50%, 75%, 100%)
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

        if (cachedData == null) return@Canvas
        val (levelPointsRaw, tempPointsRaw) = cachedData

        // 2. Chuyển đổi các điểm sang hệ tọa độ Canvas
        val levelPoints = levelPointsRaw.map { (xRatio, yRatio) ->
            Offset(xRatio * width, height - yRatio * height)
        }
        
        val tempPoints = tempPointsRaw.map { (xRatio, yRatio) ->
            Offset(xRatio * width, height - yRatio * height)
        }

        // 3. Tạo đường dẫn path vẽ line cho Battery Level
        val linePath = Path().apply {
            if (levelPoints.isNotEmpty()) {
                moveTo(levelPoints.first().x, levelPoints.first().y)
                for (i in 1 until levelPoints.size) {
                    lineTo(levelPoints[i].x, levelPoints[i].y)
                }
            }
        }

        // 4. Tạo đường dẫn path đổ màu fill phía dưới đồ thị pin
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

        // Đổ màu fill gradient cho pin
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(primaryColor.copy(alpha = 0.15f), Color.Transparent)
            )
        )

        // Vẽ đường viền line chart pin
        drawPath(
            path = linePath,
            color = primaryColor,
            style = Stroke(width = 3.dp.toPx())
        )

        // 5. Vẽ đường viền line chart nhiệt độ
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

        // Vẽ chấm tròn tại các điểm mốc chính của pin
        levelPoints.forEach { point ->
            drawCircle(
                color = primaryColor,
                radius = 3.dp.toPx(),
                center = point
            )
        }
    }
}
