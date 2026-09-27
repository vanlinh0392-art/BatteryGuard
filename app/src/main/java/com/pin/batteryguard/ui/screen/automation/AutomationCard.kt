package com.pin.batteryguard.ui.screen.automation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pin.batteryguard.ui.theme.BatteryFull
import com.pin.batteryguard.ui.theme.BatteryLow
import com.pin.batteryguard.ui.theme.Green40
import com.pin.batteryguard.ui.theme.Green80
import com.pin.batteryguard.ui.theme.Teal40
import com.pin.batteryguard.ui.theme.Teal80

/**
 * Thẻ Automation Card trực quan phong cách Smart Home (HomeKit / SmartThings)
 */
@Composable
fun AutomationCard(
    rule: AutomationRuleUiModel,
    isMasterEnabled: Boolean,
    isExpanded: Boolean,
    onToggle: (Boolean) -> Unit,
    onToggleExpand: () -> Unit,
    onUpdateParams: (AutomationConfigParams) -> Unit,
    modifier: Modifier = Modifier
) {
    val isEffectivelyEnabled = isMasterEnabled && rule.isEnabled
    val isActive = isEffectivelyEnabled && rule.activeStatus == RuleActiveStatus.ACTIVE

    // Viền sáng động khi module đang chạy thực tế
    val borderColor by animateColorAsState(
        targetValue = when {
            isActive -> Green80.copy(alpha = 0.5f)
            isEffectivelyEnabled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
        },
        label = "cardBorderColor"
    )

    val containerColor by animateColorAsState(
        targetValue = when {
            isActive -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            isEffectivelyEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
        },
        label = "cardContainerColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.2.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 2.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Hàng chính: Icon + Thông tin + Switch On/Off nhanh
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon tròn nổi bật kiểu Smart Device
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isEffectivelyEnabled) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = rule.icon,
                        contentDescription = rule.title,
                        tint = if (isEffectivelyEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        },
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Tên module & mô tả ngắn
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = rule.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        ),
                        color = if (isMasterEnabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        }
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = rule.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Switch kích hoạt nhanh (1 chạm là bật/tắt ngay)
                Switch(
                    checked = rule.isEnabled,
                    onCheckedChange = { onToggle(it) },
                    enabled = isMasterEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hàng trạng thái & nút cấu hình chi tiết
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onToggleExpand() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Badge trạng thái Đang chạy / Chờ / Tắt
                AutomationStatusBadge(
                    status = if (!isMasterEnabled) RuleActiveStatus.DISABLED else rule.activeStatus,
                    statusText = if (!isMasterEnabled) "Tạm dừng (Master Off)" else rule.statusBadgeText
                )

                // Nút mở cấu hình thông số
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Cấu hình",
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isExpanded) "Thu gọn" else "Cấu hình",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Chi tiết trạng thái kích hoạt hiện thời
            if (rule.statusDetail.isNotBlank() && isEffectivelyEnabled) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "• ${rule.statusDetail}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 2.dp)
                )
            }

            // Phần mở rộng cấu hình chi tiết (Expandable Parameters Panel)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(250)) + fadeIn(),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        thickness = 1.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    AutomationConfigEditor(
                        ruleId = rule.id,
                        params = rule.params,
                        onUpdateParams = onUpdateParams
                    )
                }
            }
        }
    }
}

/**
 * Capsule Badge hiển thị trạng thái hoạt động trực quan
 */
@Composable
fun AutomationStatusBadge(
    status: RuleActiveStatus,
    statusText: String
) {
    val (bgColor, textColor, dotColor) = when (status) {
        RuleActiveStatus.ACTIVE -> Triple(
            Green80.copy(alpha = 0.18f),
            Green80,
            Green80
        )
        RuleActiveStatus.IDLE -> Triple(
            Teal80.copy(alpha = 0.16f),
            Teal80,
            Teal80
        )
        RuleActiveStatus.DISABLED -> Triple(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            Color.Gray
        )
    }

    // Hiệu ứng nhấp nháy cho trạng thái đang chạy (Active)
    val infiniteTransition = rememberInfiniteTransition(label = "pulsingDot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dotAlpha"
    )

    Surface(
        shape = CircleShape,
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .alpha(if (status == RuleActiveStatus.ACTIVE) dotAlpha else 1f)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = textColor
            )
        }
    }
}

/**
 * Bộ biên tập tham số cấu hình riêng biệt cho từng loại quy tắc
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutomationConfigEditor(
    ruleId: AutomationRuleId,
    params: AutomationConfigParams,
    onUpdateParams: (AutomationConfigParams) -> Unit
) {
    when (ruleId) {
        // 1. 📶 Tự động tắt 4G khi có Wi-Fi
        AutomationRuleId.WIFI_AUTO_DATA -> {
            val config = params as? WifiAutoDataParams ?: WifiAutoDataParams()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Thời gian trễ tắt 4G sau khi kết nối Wi-Fi:",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(5 to "5 giây", 15 to "15 giây", 30 to "30 giây", 60 to "1 phút").forEach { (sec, label) ->
                        FilterChip(
                            selected = config.delaySeconds == sec,
                            onClick = { onUpdateParams(config.copy(delaySeconds = sec)) },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                Text(
                    text = "Áp dụng cho SIM:",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        TargetSimSelection.AUTO to "Tự động (SIM data)",
                        TargetSimSelection.SIM_1 to "SIM 1",
                        TargetSimSelection.SIM_2 to "SIM 2"
                    ).forEach { (simChoice, label) ->
                        FilterChip(
                            selected = config.targetSim == simChoice,
                            onClick = { onUpdateParams(config.copy(targetSim = simChoice)) },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
                Text(
                    text = when (config.targetSim) {
                        TargetSimSelection.AUTO -> "Hệ thống sẽ tự nhận diện SIM đang bật dữ liệu để bật/tắt chính xác."
                        TargetSimSelection.SIM_1 -> "Chỉ áp dụng bật/tắt dữ liệu di động cho SIM ở Khe 1."
                        TargetSimSelection.SIM_2 -> "Chỉ áp dụng bật/tắt dữ liệu di động cho SIM ở Khe 2."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tự bật lại 4G khi mất Wi-Fi", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Đảm bảo duy trì kết nối mạng thông suốt (chờ 20s chống rung lắc mạng)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.autoRestoreDataOnDisconnect,
                        onCheckedChange = { onUpdateParams(config.copy(autoRestoreDataOnDisconnect = it)) }
                    )
                }
            }
        }

        // 2. 🔔 Tự động chuyển chuông theo giờ
        AutomationRuleId.DAY_NIGHT_RINGER -> {
            val config = params as? DayNightRingerParams ?: DayNightRingerParams()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Khung giờ ban đêm (Tự chuyển chế độ):",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Bắt đầu", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(
                                String.format("%02d:%02d", config.startHour, config.startMinute),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Kết thúc", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(
                                String.format("%02d:%02d", config.endHour, config.endMinute),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                Text(
                    text = "Chế độ chuông ban đêm:",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = config.nightMode == RingerTargetMode.VIBRATE,
                        onClick = { onUpdateParams(config.copy(nightMode = RingerTargetMode.VIBRATE)) },
                        label = { Text("📳 Rung (Khuyên dùng)") }
                    )
                    FilterChip(
                        selected = config.nightMode == RingerTargetMode.SILENT,
                        onClick = { onUpdateParams(config.copy(nightMode = RingerTargetMode.SILENT)) },
                        label = { Text("🔇 Im lặng tuyệt đối") }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Áp dụng cả thứ Bảy & Chủ Nhật", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = config.applyOnWeekends,
                        onCheckedChange = { onUpdateParams(config.copy(applyOnWeekends = it)) }
                    )
                }
            }
        }

        // 3. 🔋 Bảo vệ pin khi sạc qua đêm
        AutomationRuleId.OVERNIGHT_CHARGING -> {
            val config = params as? OvernightChargingParams ?: OvernightChargingParams()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Ngưỡng sạc bảo vệ tuổi thọ:", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${config.maxChargeLimitPercent}%",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
                Slider(
                    value = config.maxChargeLimitPercent.toFloat(),
                    onValueChange = { onUpdateParams(config.copy(maxChargeLimitPercent = it.toInt())) },
                    valueRange = 70f..90f,
                    steps = 3,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Âm báo khi pin đạt ngưỡng", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Phát chuông nhẹ nhắc rút sạc hoặc chuyển dòng thấp",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.alertSoundOnTarget,
                        onCheckedChange = { onUpdateParams(config.copy(alertSoundOnTarget = it)) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Kiểm soát nhiệt độ sạc đêm", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Giảm công suất sạc nếu pin vượt quá 40°C",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.preventOverheat,
                        onCheckedChange = { onUpdateParams(config.copy(preventOverheat = it)) }
                    )
                }
            }
        }

        // 4. ⚡ Tự động tiết kiệm pin khi pin yếu
        AutomationRuleId.LOW_BATTERY_SAVER -> {
            val config = params as? LowBatterySaverParams ?: LowBatterySaverParams()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Kích hoạt khi mức pin dưới:", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${config.thresholdPercent}%",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = BatteryLow
                        )
                    )
                }
                Slider(
                    value = config.thresholdPercent.toFloat(),
                    onValueChange = { onUpdateParams(config.copy(thresholdPercent = it.toInt())) },
                    valueRange = 10f..35f,
                    steps = 4,
                    colors = SliderDefaults.colors(
                        thumbColor = BatteryLow,
                        activeTrackColor = BatteryLow
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Tự động giảm 30% độ sáng màn hình", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = config.dimDisplayBrightness,
                        onCheckedChange = { onUpdateParams(config.copy(dimDisplayBrightness = it)) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Bật Chế độ tiết kiệm pin Android", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = config.enableSystemPowerSaver,
                        onCheckedChange = { onUpdateParams(config.copy(enableSystemPowerSaver = it)) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Tắt Màn hình luôn bật (AOD)", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = config.turnOffAodAndRadios,
                        onCheckedChange = { onUpdateParams(config.copy(turnOffAodAndRadios = it)) }
                    )
                }
            }
        }

        // 5. 📴 Tối ưu sâu khi tắt màn hình
        AutomationRuleId.DEEP_SCREEN_OFF -> {
            val config = params as? DeepScreenOffParams ?: DeepScreenOffParams()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Thời gian chờ sau khi tắt màn hình:",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1 to "1 phút", 3 to "3 phút", 5 to "5 phút", 10 to "10 phút").forEach { (min, label) ->
                        FilterChip(
                            selected = config.delayMinutes == min,
                            onClick = { onUpdateParams(config.copy(delayMinutes = min)) },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dọn dẹp app chạy ngầm hao pin", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Buộc dừng các ứng dụng ngầm ngoài danh sách ngoại lệ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.forceStopBackgroundDrainers,
                        onCheckedChange = { onUpdateParams(config.copy(forceStopBackgroundDrainers = it)) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Kích hoạt chế độ Doze sâu (Deep Sleep)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Đưa CPU vào trạng thái nghỉ sâu nhất",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.enableDeepDoze,
                        onCheckedChange = { onUpdateParams(config.copy(enableDeepDoze = it)) }
                    )
                }
            }
        }
    }
}
