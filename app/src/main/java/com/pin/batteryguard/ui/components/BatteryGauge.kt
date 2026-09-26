package com.pin.batteryguard.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pin.batteryguard.ui.theme.*

@Composable
fun BatteryGauge(
    level: Int,
    temperature: Float,
    isCharging: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp
) {
    val animatedLevel by animateFloatAsState(
        targetValue = level / 100f,
        animationSpec = tween(durationMillis = 1000),
        label = "BatteryLevel"
    )

    // Xác định màu sắc dựa trên dung lượng pin hiện tại
    val batteryColor = when {
        level > 60 -> BatteryFull
        level > 35 -> BatteryGood
        level > 15 -> BatteryMedium
        level > 5 -> BatteryLow
        else -> BatteryCritical
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size)
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val strokeWidth = 14.dp.toPx()
            
            // Vẽ vòng tròn nền
            drawArc(
                color = Color.Gray.copy(alpha = 0.15f),
                startAngle = 140f,
                sweepAngle = 260f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Vẽ vòng tròn giá trị thực tế với Gradient
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(batteryColor.copy(alpha = 0.5f), batteryColor)
                ),
                startAngle = 140f,
                sweepAngle = animatedLevel * 260f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isCharging) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = "Charging",
                    tint = BatteryFull,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = "$level%",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            val tempColor = when {
                temperature >= 42f -> BatteryCritical
                temperature >= 37f -> BatteryMedium
                else -> BatteryFull
            }
            Text(
                text = "${temperature}°C",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = tempColor
            )
        }
    }
}
