package com.pin.batteryguard.ui.screen.shield

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pin.batteryguard.ui.theme.BatteryFull
import com.pin.batteryguard.ui.theme.BatteryLow
import com.pin.batteryguard.util.PackageHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppShieldScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPermissionGranter: () -> Unit,
    viewModel: AppShieldViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var isAdvancedSettingsExpanded by remember { mutableStateOf(false) }

    // Tự động làm mới quyền và trạng thái khi quay lại màn hình từ Cài đặt
    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissions()
        onPauseOrDispose { }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bảo vệ Ứng dụng & Ngân hàng", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshPermissions() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Làm mới quyền")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // 1. Hero Bento Card: Trực quan hóa trung tâm trạng thái & Master switch
            item {
                Spacer(modifier = Modifier.height(4.dp))
                HeroProtectionCard(
                    uiState = uiState,
                    onToggleMaster = { viewModel.setMasterEnabled(it) },
                    onToggleAutoDetectBanks = { viewModel.updateToggle(autoDetectBanks = it) },
                    onManualRevert = { viewModel.manualRevert() }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // 2. Banner cảnh báo quyền siêu tinh gọn (Chỉ hiện khi thiếu quyền)
            if (!uiState.hasWriteSecureSettings || !uiState.isAccessibilityEnabled) {
                item {
                    CompactPermissionBanner(
                        hasSecure = uiState.hasWriteSecureSettings,
                        hasAccessibility = uiState.isAccessibilityEnabled,
                        onOpenAccessibility = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        },
                        onOpenPermissionGranter = onNavigateToPermissionGranter
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // 3. Tùy chỉnh nâng cao (Progressive Disclosure - Collapsible Accordion)
            item {
                AdvancedSettingsAccordion(
                    isExpanded = isAdvancedSettingsExpanded,
                    onToggleExpand = { isAdvancedSettingsExpanded = !isAdvancedSettingsExpanded },
                    uiState = uiState,
                    onSelectMinutes = { viewModel.setAutoRevertMinutes(it) },
                    onToggleScreenOff = { viewModel.updateToggle(revertOnScreenOff = it) },
                    onToggleAutoShizuku = { viewModel.updateToggle(autoRestartShizuku = it) },
                    onToggleRelaunch = { viewModel.updateToggle(relaunchApp = it) },
                    onToggleDevOptions = { viewModel.updateToggle(hideDevOptions = it) },
                    onToggleAdb = { viewModel.updateToggle(hideAdb = it) },
                    onToggleWirelessAdb = { viewModel.updateToggle(hideWirelessAdb = it) },
                    onToggleAccessibility = { viewModel.updateToggle(hideAccessibility = it) },
                    onManualHide = { viewModel.manualHide() }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 4. Thanh tìm kiếm và Filter Tabs
            item {
                AppFilterSection(
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                    selectedTab = uiState.filterTab,
                    onSelectTab = { viewModel.onFilterTabChanged(it) },
                    totalCount = uiState.apps.size,
                    shieldedCount = uiState.apps.count { it.isShielded },
                    banksCount = uiState.apps.count { it.isPresetBank },
                    onSelectAllBanks = { viewModel.selectAllBanks() }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 5. Danh sách ứng dụng
            if (uiState.isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 3.dp)
                    }
                }
            } else if (uiState.filteredApps.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (uiState.searchQuery.isNotBlank()) "Không tìm thấy ứng dụng '${uiState.searchQuery}'" else "Không có ứng dụng trong mục này",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                items(uiState.filteredApps, key = { it.packageName }) { appItem ->
                    AppShieldRow(
                        item = appItem,
                        onToggle = { isEnabled -> viewModel.toggleAppShield(appItem, isEnabled) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

/**
 * 1. Hero Protection Card (Bento Style): Trực quan hóa trạng thái 3 cấp độ
 * Tối ưu responsive cho màn hình compact (Xiaomi 14): Không tràn viền, không đè Switch.
 */
@Composable
private fun HeroProtectionCard(
    uiState: AppShieldUiState,
    onToggleMaster: (Boolean) -> Unit,
    onToggleAutoDetectBanks: (Boolean) -> Unit,
    onManualRevert: () -> Unit
) {
    val isEnabled = uiState.config.isEnabled
    val isHidden = uiState.isCurrentlyHidden

    val cardBg = when {
        !isEnabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        isHidden -> Color(0xFFFFA000).copy(alpha = 0.12f)
        else -> BatteryFull.copy(alpha = 0.12f)
    }

    val statusIcon = when {
        !isEnabled -> Icons.Default.Security
        isHidden -> Icons.Default.Lock
        else -> Icons.Default.CheckCircle
    }

    val statusColor = when {
        !isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        isHidden -> Color(0xFFE65100)
        else -> BatteryFull
    }

    val statusTitle = when {
        !isEnabled -> "ĐÃ TẮT BẢO VỆ"
        isHidden -> "ĐANG ẨN CÀI ĐẶT HỆ THỐNG"
        else -> "BẢO VỆ TỰ ĐỘNG 24/7"
    }

    val statusSubtitle = when {
        !isEnabled -> "Bật công tắc để kích hoạt bảo vệ tàng hình khi mở ngân hàng"
        isHidden -> "Đang che giấu Developer Options & ADB cho: ${uiState.triggeredPackage}"
        else -> "Tự động ẩn Developer Options & ADB ngay khi mở app nhạy cảm"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Hàng 1: Badge trạng thái + Master Switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(statusColor.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(statusIcon, contentDescription = null, tint = statusColor, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(statusTitle, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = statusColor)
                        Text(
                            statusSubtitle,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggleMaster
                )
            }

            // Nút Khôi phục ngay (Chỉ hiển thị khi đang thực sự ẩn)
            if (isHidden) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onManualRevert,
                    colors = ButtonDefaults.buttonColors(containerColor = BatteryFull),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("⚡ Khôi phục cài đặt ngay", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hàng 2: Quick Metrics Chips (Responsive, không tràn chữ)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🏦", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                "${uiState.autoDetectedCount} Ngân hàng",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "Tự nhận diện",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🛡️", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                "${uiState.manuallyShieldedCount} Chọn thêm",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "Thủ công",
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Hàng 3: Công tắc nhận diện ngân hàng thông minh (Responsive, không bao giờ đè Switch)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onToggleAutoDetectBanks(!uiState.config.autoDetectBanks) }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Tự động nhận diện ngân hàng",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "Khuyên dùng",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                maxLines = 1
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Tự động bảo vệ hơn 60 app tài chính không cần tick tay",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Switch(
                    checked = uiState.config.autoDetectBanks,
                    onCheckedChange = onToggleAutoDetectBanks
                )
            }
        }
    }
}

/**
 * 2. Compact Permission Banner (1 dòng mỏng, chỉ xuất hiện khi thiếu quyền)
 */
@Composable
private fun CompactPermissionBanner(
    hasSecure: Boolean,
    hasAccessibility: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenPermissionGranter: () -> Unit
) {
    val message = when {
        !hasSecure && !hasAccessibility -> "Thiếu quyền Secure Settings & Trợ năng"
        !hasSecure -> "Thiếu quyền WRITE_SECURE_SETTINGS"
        else -> "Chưa bật Dịch vụ Trợ năng AppShield"
    }

    Surface(
        color = BatteryLow.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BatteryLow.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = BatteryLow, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                message,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = BatteryLow,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Button(
                onClick = if (!hasSecure) onOpenPermissionGranter else onOpenAccessibility,
                colors = ButtonDefaults.buttonColors(containerColor = BatteryLow),
                shape = RoundedCornerShape(8.dp),
                contentPadding = ButtonDefaults.TextButtonContentPadding,
                modifier = Modifier.height(30.dp)
            ) {
                Text(if (!hasSecure) "Cấp quyền" else "Bật trợ năng", fontSize = 10.sp, color = Color.White)
            }
        }
    }
}

/**
 * 3. Tùy chỉnh nâng cao (Accordion Collapsible - Progressive Disclosure)
 */
@Composable
private fun AdvancedSettingsAccordion(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    uiState: AppShieldUiState,
    onSelectMinutes: (Int) -> Unit,
    onToggleScreenOff: (Boolean) -> Unit,
    onToggleAutoShizuku: (Boolean) -> Unit,
    onToggleRelaunch: (Boolean) -> Unit,
    onToggleDevOptions: (Boolean) -> Unit,
    onToggleAdb: (Boolean) -> Unit,
    onToggleWirelessAdb: (Boolean) -> Unit,
    onToggleAccessibility: (Boolean) -> Unit,
    onManualHide: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Thanh tiêu đề bấm để mở rộng / thu gọn
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleExpand() }
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tùy chỉnh nâng cao", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${uiState.config.autoRevertMinutes} phút • ${if (uiState.config.revertOnScreenOff) "Khôi phục khi tắt màn hình" else "Đợi hết giờ"}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    // 1. Hẹn giờ hoàn tác
                    Text("HẸN GIỜ TỰ ĐỘNG BẬT LẠI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(2, 5, 10, 15, 30).forEach { mins ->
                            val isSelected = uiState.config.autoRevertMinutes == mins
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectMinutes(mins) },
                                label = {
                                    Text(
                                        if (mins == 10) "10p (Chuẩn)" else "${mins}p",
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.height(30.dp)
                            )
                        }
                    }

                    Slider(
                        value = uiState.config.autoRevertMinutes.toFloat(),
                        onValueChange = { onSelectMinutes(it.toInt()) },
                        valueRange = 1f..60f,
                        steps = 58
                    )
                    Text(
                        "Thời gian: ${uiState.config.autoRevertMinutes} phút",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.align(Alignment.End)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. Các công tắc hoạt động
                    SettingSwitchRow("Bật lại ngay khi khóa màn hình", "Khôi phục khi tắt máy, không cần đợi hết giờ", uiState.config.revertOnScreenOff, onToggleScreenOff)
                    SettingSwitchRow("Tự bật lại Shizuku sau khi khôi phục", "Khởi động Shizuku daemon qua ADB 5555", uiState.config.autoRestartShizuku, onToggleAutoShizuku)
                    SettingSwitchRow("Đóng app trước khi ẩn (Relaunch)", "Xóa cache kiểm tra bảo mật của ngân hàng", uiState.config.relaunchApp, onToggleRelaunch)

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Phạm vi che giấu
                    Text("PHẠM VI CHE GIẤU", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    SettingSwitchRow("Ẩn Tùy chọn nhà phát triển", null, uiState.config.hideDevOptions, onToggleDevOptions)
                    SettingSwitchRow("Ẩn Gỡ lỗi USB (ADB Debugging)", null, uiState.config.hideAdb, onToggleAdb)
                    SettingSwitchRow("Ẩn Gỡ lỗi không dây (Wireless Debugging)", null, uiState.config.hideWirelessAdb, onToggleWirelessAdb)
                    SettingSwitchRow("Tạm thời lọc Dịch vụ Trợ năng khác", "Tránh app ngân hàng chặn vì Accessibility", uiState.config.hideAccessibility, onToggleAccessibility)

                    Spacer(modifier = Modifier.height(10.dp))

                    // Nút thử nghiệm ẩn
                    OutlinedButton(
                        onClick = onManualHide,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(38.dp)
                    ) {
                        Text("🛡️ Ẩn thử nghiệm (Test Shield)", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

/**
 * 4. Thanh tìm kiếm & Tabs lọc ứng dụng (Scrollable linh hoạt, chống vỡ chữ)
 */
@Composable
private fun AppFilterSection(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    selectedTab: AppShieldFilterTab,
    onSelectTab: (AppShieldFilterTab) -> Unit,
    totalCount: Int,
    shieldedCount: Int,
    banksCount: Int,
    onSelectAllBanks: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Thanh Search
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChanged,
            placeholder = { Text("Tìm ứng dụng hoặc package...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { onSearchQueryChanged("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Xóa", modifier = Modifier.size(16.dp))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Hàng Tabs Lọc cuộn ngang mượt mà cho mọi kích thước màn hình
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedTab == AppShieldFilterTab.ALL,
                onClick = { onSelectTab(AppShieldFilterTab.ALL) },
                label = { Text("Tất cả ($totalCount)", fontSize = 11.sp, maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer)
            )

            FilterChip(
                selected = selectedTab == AppShieldFilterTab.SHIELDED,
                onClick = { onSelectTab(AppShieldFilterTab.SHIELDED) },
                label = { Text("Đang bảo vệ ($shieldedCount)", fontSize = 11.sp, maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer)
            )

            FilterChip(
                selected = selectedTab == AppShieldFilterTab.BANKS,
                onClick = { onSelectTab(AppShieldFilterTab.BANKS) },
                label = { Text("Ngân hàng ($banksCount)", fontSize = 11.sp, maxLines = 1) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer)
            )

            // Nút Chọn tất cả ngân hàng dạng Chip nổi bật, không bao giờ bị gãy dòng!
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelectAllBanks() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⭐", fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Chọn tất cả",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * 5. Dòng hiển thị từng Ứng dụng (AppShieldRow)
 * Tối ưu responsive: Tên app co giãn linh hoạt, Tag và Switch luôn chuẩn vị trí.
 */
@Composable
private fun AppShieldRow(
    item: AppShieldUiItem,
    onToggle: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val iconBitmap = remember(item.packageName) {
        PackageHelper.getAppIcon(context, item.packageName)?.toBitmap()?.asImageBitmap()
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle(!item.isShielded) }
            .padding(vertical = 6.dp, horizontal = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        ) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = item.appName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (item.isAutoDetected) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "Tự động",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                maxLines = 1
                            )
                        }
                    } else if (item.isPresetBank) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "Ngân hàng",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.packageName,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Switch(
            checked = item.isShielded,
            onCheckedChange = onToggle
        )
    }
}
