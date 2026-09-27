package com.pin.batteryguard.ui.screen.automation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
                            text = "Kích hoạt & Quy tắc thông minh",
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. MASTER TOGGLE HERO CARD
            item(key = "master_header") {
                MasterAutomationHeroCard(
                    isMasterEnabled = uiState.isMasterEnabled,
                    activeCount = uiState.activeRulesCount,
                    totalCount = uiState.rules.size,
                    onToggleMaster = { viewModel.toggleMaster(it) }
                )
            }

            // 2. QUICK STATS ROW
            item(key = "quick_stats") {
                AutomationStatsRow(
                    activeCount = uiState.activeRulesCount,
                    enabledCount = uiState.enabledRulesCount,
                    estimatedSavings = uiState.estimatedBatterySavingsPercent,
                    isShizukuReady = uiState.isShizukuReady
                )
            }

            // 3. SECTION TITLE: PRESET MODULES
            item(key = "section_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "MODULES CÓ SẴN (PRESETS)",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${uiState.rules.size} quy tắc",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 4. DANH SÁCH AUTOMATION CARDS
            items(
                items = uiState.rules,
                key = { it.id.name }
            ) { rule ->
                AutomationCard(
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
                            text = "Mẹo: Bạn có thể bấm vào 'Cấu hình' ở từng thẻ để tùy chỉnh ngưỡng pin, độ trễ và chế độ chuyển đổi theo nhu cầu cá nhân.",
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
 * Hero Card chứa Master Toggle lớn và chỉ số tổng quát
 */
@Composable
fun MasterAutomationHeroCard(
    isMasterEnabled: Boolean,
    activeCount: Int,
    totalCount: Int,
    onToggleMaster: (Boolean) -> Unit
) {
    val gradientColors = if (isMasterEnabled) {
        listOf(
            Green40.copy(alpha = 0.35f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    } else {
        listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(gradientColors))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon lớn với viền sáng
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            if (isMasterEnabled) Green80.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                        .border(
                            1.5.dp,
                            if (isMasterEnabled) Green80.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = if (isMasterEnabled) Green80 else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Thông tin trạng thái tổng
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tự động hóa tổng",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isMasterEnabled) {
                            "Đang hoạt động • $activeCount/$totalCount module sẵn sàng"
                        } else {
                            "Tất cả quy tắc tự động đang tạm dừng"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isMasterEnabled) Green80 else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

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
}

/**
 * Hàng các thẻ thống kê nhanh: Số rule hoạt động, % tiết kiệm, quyền Shizuku
 */
@Composable
fun AutomationStatsRow(
    activeCount: Int,
    enabledCount: Int,
    estimatedSavings: Int,
    isShizukuReady: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Thẻ 1: Quy tắc đang kích hoạt
        StatMiniCard(
            modifier = Modifier.weight(1f),
            label = "Đang chạy",
            value = "$activeCount quy tắc",
            subLabel = "$enabledCount đã bật",
            indicatorColor = if (activeCount > 0) BatteryFull else MaterialTheme.colorScheme.outline
        )

        // Thẻ 2: Ước tính tiết kiệm
        StatMiniCard(
            modifier = Modifier.weight(1f),
            label = "Ước tính tiết kiệm",
            value = "~$estimatedSavings%",
            subLabel = "Pin tiêu thụ/ngày",
            indicatorColor = Teal80
        )

        // Thẻ 3: Shizuku Bridge
        StatMiniCard(
            modifier = Modifier.weight(1f),
            label = "Quyền hệ thống",
            value = if (isShizukuReady) "Sẵn sàng" else "Chưa cấp",
            subLabel = if (isShizukuReady) "Shizuku ADB" else "Cần Shizuku",
            indicatorColor = if (isShizukuReady) BatteryFull else BatteryLow
        )
    }
}

@Composable
fun StatMiniCard(
    label: String,
    value: String,
    subLabel: String,
    indicatorColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(indicatorColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = subLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}
