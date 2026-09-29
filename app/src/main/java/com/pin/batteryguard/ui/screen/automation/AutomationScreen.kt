package com.pin.batteryguard.ui.screen.automation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pin.batteryguard.ui.theme.BatteryFull
import com.pin.batteryguard.ui.theme.BatteryGood
import com.pin.batteryguard.ui.theme.BatteryLow
import com.pin.batteryguard.ui.theme.Green40
import com.pin.batteryguard.ui.theme.Green80
import com.pin.batteryguard.ui.theme.Teal80

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationScreen(
    onNavigateToSettings: () -> Unit = {},
    viewModel: AutomationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Tự động hóa",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Quy tắc tự động",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshLiveSensorStatus() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Làm mới trạng thái"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. COMPACT MASTER CONTROL STRIP
            item(key = "master_header") {
                CompactMasterControlCard(
                    isMasterEnabled = uiState.isMasterEnabled,
                    activeCount = uiState.activeRulesCount,
                    totalCount = uiState.rules.size,
                    estimatedSavings = uiState.estimatedBatterySavingsPercent,
                    isShizukuReady = uiState.isShizukuReady,
                    hasWriteSecureSettings = uiState.hasWriteSecureSettings,
                    onToggleMaster = { viewModel.toggleMaster(it) },
                    onRequestGrant = { viewModel.triggerAutoGrant() }
                )
            }

            // 2. SECTION TITLE: PRESET MODULES & SORTING CHIP
            item(key = "section_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "DANH SÁCH QUY TẮC",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${uiState.rules.size})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Chip chuyển đổi chế độ sắp xếp linh hoạt
                    Surface(
                        onClick = { viewModel.toggleAutoSortActive() },
                        shape = RoundedCornerShape(8.dp),
                        color = if (uiState.autoSortActiveToTop) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(
                            0.8.dp,
                            if (uiState.autoSortActiveToTop) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = if (uiState.autoSortActiveToTop) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (uiState.autoSortActiveToTop) "Ưu tiên Đang chạy" else "Thứ tự Gốc",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                                color = if (uiState.autoSortActiveToTop) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 3. DANH SÁCH AUTOMATION CARDS (CÓ HIỆU ỨNG TRƯỢT SẮP XẾP)
            items(
                items = uiState.rules,
                key = { it.id.name }
            ) { rule ->
                AutomationCard(
                    modifier = Modifier.animateItem(),
                    rule = rule,
                    isMasterEnabled = uiState.isMasterEnabled,
                    isExpanded = uiState.expandedRuleId == rule.id,
                    onToggle = { enabled ->
                        viewModel.toggleRule(rule.id, enabled)
                    },
                    onToggleExpand = {
                        viewModel.toggleExpandRule(rule.id)
                    },
                    onUpdateParams = { newParams ->
                        viewModel.updateRuleParams(rule.id, newParams)
                    }
                )
            }

            // 5. FOOTER INFO
            item(key = "footer_tips") {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Chạm thẻ để chỉnh",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Thanh điều khiển tổng siêu gọn (Compact Master Control Strip)
 * Tích hợp toàn bộ Master Switch, trạng thái số rule đang chạy, % tiết kiệm và Shizuku readiness vào 1 hàng duy nhất ~64dp
 */
@Composable
fun CompactMasterControlCard(
    isMasterEnabled: Boolean,
    activeCount: Int,
    totalCount: Int,
    estimatedSavings: Int,
    isShizukuReady: Boolean,
    hasWriteSecureSettings: Boolean = false,
    onToggleMaster: (Boolean) -> Unit,
    onRequestGrant: () -> Unit = {}
) {
    val gradientColors = if (isMasterEnabled) {
        listOf(
            Green40.copy(alpha = 0.22f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    } else {
        listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(
            1.dp,
            if (isMasterEnabled) Green80.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isMasterEnabled) 1.5.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(gradientColors))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon Bolt nhỏ gọn với viền phát sáng
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (isMasterEnabled) Green80.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    .border(
                        1.dp,
                        if (isMasterEnabled) Green80.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (isMasterEnabled) Green80 else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Thông tin trung tâm: Tiêu đề + Capsule đang chạy + Dòng phụ
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Tổng điều khiển",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isMasterEnabled) Green80.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Text(
                            text = if (isMasterEnabled) "Chạy: $activeCount/$totalCount" else "Đã tắt",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                            color = if (isMasterEnabled) Green80 else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Dòng phụ: Tiết kiệm pin & Trạng thái hoạt động (Quyền Hệ thống / Shizuku)
                val isOperational = isShizukuReady || hasWriteSecureSettings
                val readinessLabel = when {
                    isShizukuReady -> "Shizuku Sẵn sàng"
                    hasWriteSecureSettings -> "Quyền Hệ thống"
                    else -> "Cần cấp quyền"
                }
                val readinessColor = if (isOperational) Green80 else BatteryLow

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onRequestGrant() }
                        .padding(vertical = 1.dp)
                ) {
                    Text(
                        text = "Tiết kiệm ~$estimatedSavings% • ",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(readinessColor)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = readinessLabel,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = readinessColor
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Master Switch
            Switch(
                checked = isMasterEnabled,
                onCheckedChange = onToggleMaster,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}
