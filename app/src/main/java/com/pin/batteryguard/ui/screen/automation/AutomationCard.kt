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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import java.util.Locale
import kotlin.math.roundToInt

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
    onUnfreezeAllApps: () -> Unit = {},
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
                        onUpdateParams = onUpdateParams,
                        onUnfreezeAllApps = onUnfreezeAllApps
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
 * Bộ biên tập tham số cấu hình siêu tinh gọn cho từng loại quy tắc (UI/UX Pro Max)
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AutomationConfigEditor(
    ruleId: AutomationRuleId,
    params: AutomationConfigParams,
    onUpdateParams: (AutomationConfigParams) -> Unit,
    onUnfreezeAllApps: () -> Unit = {}
) {
    when (ruleId) {
        // 1. 📶 Tự động tắt 4G khi có Wi-Fi
        AutomationRuleId.WIFI_AUTO_DATA -> {
            val config = params as? WifiAutoDataParams ?: WifiAutoDataParams()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // A. Thời gian chờ TẮT 4G khi có Wi-Fi
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Trễ tắt 4G:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = if (config.delaySeconds == 0) "Tức thì (0s)" else "${config.delaySeconds}s",
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

                // B. Lựa chọn SIM
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Áp dụng SIM:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            TargetSimSelection.AUTO to "Tự động",
                            TargetSimSelection.SIM_1 to "SIM 1",
                            TargetSimSelection.SIM_2 to "SIM 2"
                        ).forEach { (simChoice, label) ->
                            val isSelected = config.targetSim == simChoice
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.clickable { onUpdateParams(config.copy(targetSim = simChoice)) }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                // C. Tự bật lại 4G khi mất Wi-Fi
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Tự bật lại 4G khi mất Wi-Fi",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = config.autoRestoreDataOnDisconnect,
                        onCheckedChange = { onUpdateParams(config.copy(autoRestoreDataOnDisconnect = it)) }
                    )
                }

                AnimatedVisibility(visible = config.autoRestoreDataOnDisconnect) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Trễ bật lại 4G:",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = if (config.restoreDelaySeconds == 0) "Tức thì (0s)" else "${config.restoreDelaySeconds}s",
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
                    title = "Giờ bắt đầu Rung/Im lặng",
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
                    title = "Giờ bật lại chuông thường",
                    initialHour = config.endHour,
                    initialMinute = config.endMinute,
                    onDismissRequest = { showEndTimePicker = false },
                    onConfirm = { h, m ->
                        onUpdateParams(config.copy(endHour = h, endMinute = m))
                    }
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Khung giờ ngang gọn
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Khung giờ đêm:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { showStartTimePicker = true }
                        ) {
                            Text(
                                String.format(Locale.US, "%02d:%02d", config.startHour, config.startMinute),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        Text("➔", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { showEndTimePicker = true }
                        ) {
                            Text(
                                String.format(Locale.US, "%02d:%02d", config.endHour, config.endMinute),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Chế độ chuông segmented pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chế độ:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            RingerTargetMode.VIBRATE to "📳 Rung",
                            RingerTargetMode.SILENT to "🔇 Im lặng"
                        ).forEach { (mode, label) ->
                            val isSelected = config.nightMode == mode
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.clickable { onUpdateParams(config.copy(nightMode = mode)) }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Áp dụng cả cuối tuần (T7, CN)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
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

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // 1. Sạc siêu nhanh 90W
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Sạc siêu nhanh (90W HyperCharge)",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = config.fastCharge90WEnabled,
                        onCheckedChange = { onUpdateParams(config.copy(fastCharge90WEnabled = it)) }
                    )
                }

                // 2. Ưu tiên Sạc Tối Đa
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Ưu tiên Sạc Tối Đa", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "48°C Fail-safe",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Switch(
                        checked = config.prioritizeFastChargeNoThrottle,
                        onCheckedChange = { onUpdateParams(config.copy(prioritizeFastChargeNoThrottle = it)) }
                    )
                }

                // 3. Ngưỡng sạc
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ngưỡng sạc bảo vệ pin:", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${config.maxChargeLimitPercent}%",
                        style = MaterialTheme.typography.bodyMedium.copy(
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

                // 4. Khung giờ
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Khung giờ sạc đêm:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { showOvernightStartPicker = true }
                        ) {
                            Text(
                                String.format(Locale.US, "%02d:00", config.startHour),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        Text("➔", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { showOvernightEndPicker = true }
                        ) {
                            Text(
                                String.format(Locale.US, "%02d:00", config.endHour),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Âm báo đầy pin", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
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
                    Text("Chống quá nhiệt (< 40°C)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = config.preventOverheat,
                        onCheckedChange = { onUpdateParams(config.copy(preventOverheat = it)) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Chỉ tự động đồng bộ khi sạc (Sync on Charge)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = config.syncOnChargeOnly,
                        onCheckedChange = { onUpdateParams(config.copy(syncOnChargeOnly = it)) }
                    )
                }
            }
        }

        // 4. ⚡ Tự động tiết kiệm pin khi pin yếu
        AutomationRuleId.LOW_BATTERY_SAVER -> {
            val config = params as? LowBatterySaverParams ?: LowBatterySaverParams()
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Mức pin kích hoạt:", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${config.thresholdPercent}%",
                        style = MaterialTheme.typography.bodyMedium.copy(
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
                    Text("Giảm 30% độ sáng", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
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
                    Text("Khóa tần số 60Hz", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = config.capRefreshRate60Hz,
                        onCheckedChange = { onUpdateParams(config.copy(capRefreshRate60Hz = it, smoothSaver120Hz = if (it) false else config.smoothSaver120Hz)) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Mượt mà 120Hz (SmoothSaver)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = config.smoothSaver120Hz,
                        onCheckedChange = { enabled ->
                            onUpdateParams(
                                if (enabled) {
                                    config.copy(smoothSaver120Hz = true, capRefreshRate60Hz = false)
                                } else {
                                    config.copy(smoothSaver120Hz = false)
                                }
                            )
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Bật Tiết kiệm pin hệ thống", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
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
                    Text("Tắt Màn hình luôn bật (AOD)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Trễ sau khi tắt màn:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1 to "1p", 3 to "3p", 5 to "5p", 10 to "10p").forEach { (min, label) ->
                            val isSelected = config.delayMinutes == min
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.clickable { onUpdateParams(config.copy(delayMinutes = min)) }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Dọn app ngầm hao pin", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
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
                    Text("Doze sâu (Deep Sleep)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = config.enableDeepDoze,
                        onCheckedChange = { onUpdateParams(config.copy(enableDeepDoze = it)) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Ngủ sâu tức thì (Instant Doze)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = config.instantDeepDoze,
                        onCheckedChange = { onUpdateParams(config.copy(instantDeepDoze = it)) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Tối ưu ngầm Google (GMS Doze)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = config.gmsDozeSafeMode,
                        onCheckedChange = { onUpdateParams(config.copy(gmsDozeSafeMode = it)) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Tăng sáng khi mở màn", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = config.restoreBrightnessOnScreenOn,
                        onCheckedChange = { onUpdateParams(config.copy(restoreBrightnessOnScreenOn = it)) }
                    )
                }

                AnimatedVisibility(visible = config.restoreBrightnessOnScreenOn) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Mức tăng khi mở:", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "+${config.screenOnBrightnessIncreasePercent}%",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        Slider(
                            value = config.screenOnBrightnessIncreasePercent.toFloat(),
                            onValueChange = { onUpdateParams(config.copy(screenOnBrightnessIncreasePercent = it.roundToInt())) },
                            valueRange = 5f..30f,
                            steps = 24,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        }

        // 6. 🛑 Dừng & Đóng băng ứng dụng hao pin ngầm
        AutomationRuleId.APP_STOP_MANAGEMENT -> {
            val config = params as? AppStopManagementParams ?: AppStopManagementParams()

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // A. Slider ngưỡng pin tiêu thụ (%/h)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ngưỡng tiêu thụ pin:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f%%/h", config.drainThresholdPercent),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Slider(
                    value = config.drainThresholdPercent,
                    onValueChange = {
                        onUpdateParams(config.copy(drainThresholdPercent = (it * 10).roundToInt() / 10f))
                    },
                    valueRange = 0.3f..5.0f,
                    steps = 46,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )

                // B. Chu kỳ quét 1 chạm
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chu kỳ quét:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(10, 15, 20, 30).forEach { mins ->
                            val isSelected = config.monitoringPeriodMinutes == mins
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.clickable { onUpdateParams(config.copy(monitoringPeriodMinutes = mins)) }
                            ) {
                                Text(
                                    text = "$mins phút",
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                // C. Switches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Tự động dừng app", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = config.autoForceStop,
                        onCheckedChange = { onUpdateParams(config.copy(autoForceStop = it)) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Báo cáo khi dừng", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = config.notifyBeforeStop,
                        onCheckedChange = { onUpdateParams(config.copy(notifyBeforeStop = it)) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Bỏ qua app hệ thống", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = config.excludeSystemApps,
                        onCheckedChange = { onUpdateParams(config.copy(excludeSystemApps = it)) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Đóng băng tức thì khi rời app (Smart Freeze)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = config.smartAutoFreeze,
                        onCheckedChange = { onUpdateParams(config.copy(smartAutoFreeze = it)) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Đóng băng theo dõi Xiaomi (7 dịch vụ)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(
                        checked = config.freezeXiaomiTelemetry,
                        onCheckedChange = { onUpdateParams(config.copy(freezeXiaomiTelemetry = it)) }
                    )
                }

                // D. Chế độ xử lý Segmented Pill Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chế độ:",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        listOf(
                            false to "🛑 Buộc dừng",
                            true to "🧊 Đóng băng"
                        ).forEach { (isFreeze, label) ->
                            val isSelected = config.enableFreezeMode == isFreeze
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                modifier = Modifier.clickable { onUpdateParams(config.copy(enableFreezeMode = isFreeze)) }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // E. Nút Rã băng toàn bộ app (Chỉ hiện khi đang ở chế độ Đóng băng)
                AnimatedVisibility(visible = config.enableFreezeMode) {
                    Button(
                        onClick = onUnfreezeAllApps,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AcUnit,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rã băng toàn bộ app", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
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

