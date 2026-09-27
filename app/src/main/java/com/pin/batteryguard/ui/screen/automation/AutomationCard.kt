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
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
            .clip(RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 2.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Hàng chính siêu gọn: Icon + Thông tin (Title & Status) + Action (Tune + Switch)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Vùng chạm mở rộng: Chạm vào Icon hoặc Tiêu đề để mở/đóng cấu hình
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onToggleExpand() }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icon bo góc nhỏ gọn 40dp
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isActive) Green80.copy(alpha = 0.2f)
                                else if (isEffectivelyEnabled) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = rule.icon,
                            contentDescription = rule.title,
                            tint = if (isActive) Green80
                            else if (isEffectivelyEnabled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Tiêu đề & Dòng trạng thái tóm tắt (1 dòng súc tích)
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = rule.title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                ),
                                color = if (isMasterEnabled) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                },
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false)
                            )

                            Spacer(modifier = Modifier.width(6.dp))

                            // Mini Status Dot báo trạng thái tức thì
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            !isMasterEnabled || !rule.isEnabled -> Color.Gray.copy(alpha = 0.4f)
                                            rule.activeStatus == RuleActiveStatus.ACTIVE -> Green80
                                            else -> Teal80
                                        }
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Dòng trạng thái/mô tả tóm tắt 1 dòng duy nhất
                        val summaryText = when {
                            !isMasterEnabled -> "Tạm dừng (Master Off)"
                            !rule.isEnabled -> rule.subtitle
                            rule.statusDetail.isNotBlank() && rule.statusDetail != "Đã tắt" -> rule.statusDetail
                            else -> "${rule.statusBadgeText} • ${rule.subtitle}"
                        }

                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                            color = if (isActive) Green80.copy(alpha = 0.9f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Nút icon Cấu hình / Thu gọn
                IconButton(
                    onClick = { onToggleExpand() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.Tune,
                        contentDescription = if (isExpanded) "Thu gọn" else "Cấu hình",
                        tint = if (isExpanded) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(19.dp)
                    )
                }

                Spacer(modifier = Modifier.width(2.dp))

                // Switch kích hoạt nhanh
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

            // Phần mở rộng cấu hình chi tiết (Expandable Parameters Panel)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(250)) + fadeIn(),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        thickness = 0.8.dp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

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
                // A. Thời gian chờ TẮT 4G khi kết nối Wi-Fi (0 - 15 giây)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Thời gian trễ tắt 4G khi có Wi-Fi:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = if (config.delaySeconds == 0) "Tức thì (0s)" else "${config.delaySeconds} giây",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Slider(
                    value = config.delaySeconds.toFloat(),
                    onValueChange = { onUpdateParams(config.copy(delaySeconds = it.toInt())) },
                    valueRange = 0f..15f,
                    steps = 14,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "0s (Tức thì)", 3 to "3s", 5 to "5s", 10 to "10s", 15 to "15s").forEach { (sec, label) ->
                        FilterChip(
                            selected = config.delaySeconds == sec,
                            onClick = { onUpdateParams(config.copy(delaySeconds = sec)) },
                            label = { Text(label, fontSize = 11.5.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                // B. Lựa chọn SIM
                Text(
                    text = "Áp dụng cho SIM:",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                // C. Tự bật lại 4G khi mất Wi-Fi (0 - 15 giây)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tự bật lại 4G khi mất Wi-Fi", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Tự động khôi phục kết nối khi ra khỏi vùng phủ sóng Wi-Fi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = config.autoRestoreDataOnDisconnect,
                        onCheckedChange = { onUpdateParams(config.copy(autoRestoreDataOnDisconnect = it)) }
                    )
                }

                AnimatedVisibility(visible = config.autoRestoreDataOnDisconnect) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Thời gian trễ bật lại 4G:",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Text(
                                text = if (config.restoreDelaySeconds == 0) "Tức thì (0s)" else "${config.restoreDelaySeconds} giây",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        Slider(
                            value = config.restoreDelaySeconds.toFloat(),
                            onValueChange = { onUpdateParams(config.copy(restoreDelaySeconds = it.toInt())) },
                            valueRange = 0f..15f,
                            steps = 14,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "0s (Tức thì)", 2 to "2s", 3 to "3s", 5 to "5s", 10 to "10s", 15 to "15s").forEach { (sec, label) ->
                                FilterChip(
                                    selected = config.restoreDelaySeconds == sec,
                                    onClick = { onUpdateParams(config.copy(restoreDelaySeconds = sec)) },
                                    label = { Text(label, fontSize = 11.5.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }

                        Text(
                            text = if (config.restoreDelaySeconds == 0) "Khôi phục dữ liệu di động ngay lập tức khi vừa mất Wi-Fi."
                            else "Chờ ${config.restoreDelaySeconds} giây trước khi bật lại 4G để chống rung lắc khi Wi-Fi chập chờn.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // 2. 🔔 Tự động chuyển chuông theo giờ
        AutomationRuleId.DAY_NIGHT_RINGER -> {
            val config = params as? DayNightRingerParams ?: DayNightRingerParams()
            var showStartTimePicker by remember { mutableStateOf(false) }
            var showEndTimePicker by remember { mutableStateOf(false) }

            if (showStartTimePicker) {
                AutomationTimePickerDialog(
                    title = "Giờ bắt đầu chuyển Rung/Im lặng",
                    initialHour = config.startHour,
                    initialMinute = config.startMinute,
                    onDismissRequest = { showStartTimePicker = false },
                    onConfirm = { h, m ->
                        onUpdateParams(config.copy(startHour = h, startMinute = m))
                    }
                )
            }

            if (showEndTimePicker) {
                AutomationTimePickerDialog(
                    title = "Giờ kết thúc (Bật lại chuông thường)",
                    initialHour = config.endHour,
                    initialMinute = config.endMinute,
                    onDismissRequest = { showEndTimePicker = false },
                    onConfirm = { h, m ->
                        onUpdateParams(config.copy(endHour = h, endMinute = m))
                    }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Khung giờ ban đêm (Chạm vào ô để đổi giờ):",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showStartTimePicker = true }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Bắt đầu", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                String.format("%02d:%02d", config.startHour, config.startMinute),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text("Chạm để đổi", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showEndTimePicker = true }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Kết thúc", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                String.format("%02d:%02d", config.endHour, config.endMinute),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text("Chạm để đổi", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
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
            var showOvernightStartPicker by remember { mutableStateOf(false) }
            var showOvernightEndPicker by remember { mutableStateOf(false) }

            if (showOvernightStartPicker) {
                AutomationTimePickerDialog(
                    title = "Giờ bắt đầu chế độ sạc đêm",
                    initialHour = config.startHour,
                    initialMinute = 0,
                    onDismissRequest = { showOvernightStartPicker = false },
                    onConfirm = { h, _ ->
                        onUpdateParams(config.copy(startHour = h))
                    }
                )
            }

            if (showOvernightEndPicker) {
                AutomationTimePickerDialog(
                    title = "Giờ kết thúc chế độ sạc đêm",
                    initialHour = config.endHour,
                    initialMinute = 0,
                    onDismissRequest = { showOvernightEndPicker = false },
                    onConfirm = { h, _ ->
                        onUpdateParams(config.copy(endHour = h))
                    }
                )
            }

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

                Text(
                    text = "Khung giờ sạc đêm áp dụng (Chạm để đổi):",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showOvernightStartPicker = true }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Bắt đầu", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                String.format("%02d:00", config.startHour),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text("Chạm để đổi", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showOvernightEndPicker = true }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Kết thúc", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Icon(Icons.Filled.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                String.format("%02d:00", config.endHour),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text("Chạm để đổi", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                }

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

/**
 * Hộp thoại chọn giờ chuẩn Material 3 trực quan
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onDismissRequest: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.AccessTime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(state.hour, state.minute)
                onDismissRequest()
            }) {
                Text("Xác nhận", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Hủy")
            }
        }
    )
}

