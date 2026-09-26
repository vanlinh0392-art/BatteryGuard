package com.pin.batteryguard.ui.screen.shield

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                title = { Text("Bảo vệ Ngân hàng & Ứng dụng", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
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
            // 1. Thẻ Trạng thái Hoạt động
            item {
                LiveStatusCard(
                    uiState = uiState,
                    onToggleMaster = { viewModel.setMasterEnabled(it) },
                    onManualHide = { viewModel.manualHide() },
                    onManualRevert = { viewModel.manualRevert() }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 2. Cảnh báo quyền nếu thiếu
            if (!uiState.hasWriteSecureSettings || !uiState.isAccessibilityEnabled) {
                item {
                    PermissionWarningCard(
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
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // 3. Cấu hình Hẹn giờ Tự động Bật lại (Auto Re-enable Timer)
            item {
                AutoRevertTimerCard(
                    autoRevertMinutes = uiState.config.autoRevertMinutes,
                    revertOnScreenOff = uiState.config.revertOnScreenOff,
                    autoRestartShizuku = uiState.config.autoRestartShizuku,
                    relaunchApp = uiState.config.relaunchApp,
                    onSelectMinutes = { viewModel.setAutoRevertMinutes(it) },
                    onToggleScreenOff = { viewModel.updateToggle(revertOnScreenOff = it) },
                    onToggleAutoShizuku = { viewModel.updateToggle(autoRestartShizuku = it) },
                    onToggleRelaunch = { viewModel.updateToggle(relaunchApp = it) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 4. Các thiết lập cần ẩn (Scope)
            item {
                ProtectionScopeCard(
                    hideDevOptions = uiState.config.hideDevOptions,
                    hideAdb = uiState.config.hideAdb,
                    hideWirelessAdb = uiState.config.hideWirelessAdb,
                    hideAccessibility = uiState.config.hideAccessibility,
                    onToggleDevOptions = { viewModel.updateToggle(hideDevOptions = it) },
                    onToggleAdb = { viewModel.updateToggle(hideAdb = it) },
                    onToggleWirelessAdb = { viewModel.updateToggle(hideWirelessAdb = it) },
                    onToggleAccessibility = { viewModel.updateToggle(hideAccessibility = it) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 2.5 Cơ chế Tự động phát hiện Ngân hàng & Ví điện tử
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.config.autoDetectBanks)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Tự động nhận diện Ngân hàng",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "Khuyên dùng",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Tự động phát hiện & bảo vệ tất cả ứng dụng ngân hàng, ví điện tử, chứng khoán mà không cần phải tick chọn thủ công.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                lineHeight = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = uiState.config.autoDetectBanks,
                            onCheckedChange = { viewModel.updateToggle(autoDetectBanks = it) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // 5. Tiêu đề danh sách ứng dụng chọn thêm & Tìm kiếm
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "ỨNG DỤNG CHỌN THÊM",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Chọn thêm các ứng dụng hoặc game bạn muốn bảo vệ",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Button(
                        onClick = { viewModel.selectAllBanks() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text("⭐ Chọn ngân hàng", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = { Text("Tìm ứng dụng hoặc package...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 6. Danh sách ứng dụng
            if (uiState.isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (uiState.filteredApps.isEmpty()) {
                item {
                    Text(
                        "Không tìm thấy ứng dụng phù hợp.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
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
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun LiveStatusCard(
    uiState: AppShieldUiState,
    onToggleMaster: (Boolean) -> Unit,
    onManualHide: () -> Unit,
    onManualRevert: () -> Unit
) {
    val statusColor = when {
        !uiState.config.isEnabled -> Color.Gray
        uiState.isCurrentlyHidden -> Color(0xFFFFA000) // Cam cảnh báo
        else -> BatteryFull // Xanh an toàn
    }

    val statusText = when {
        !uiState.config.isEnabled -> "ĐANG TẮT BẢO VỆ"
        uiState.isCurrentlyHidden -> "ĐANG ẨN CÀI ĐẶT (ĐANG BẢO VỆ)"
        else -> "SẴN SÀNG BẢO VỆ"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(statusColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(statusText, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = statusColor)
                    if (uiState.isCurrentlyHidden && uiState.triggeredPackage.isNotBlank()) {
                        Text(
                            "Đang ẩn cho: ${uiState.triggeredPackage}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Switch(
                    checked = uiState.config.isEnabled,
                    onCheckedChange = onToggleMaster
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onManualRevert,
                    colors = ButtonDefaults.buttonColors(containerColor = BatteryFull),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("⚡ Khôi phục ngay", fontSize = 12.sp, color = Color.White)
                }

                OutlinedButton(
                    onClick = onManualHide,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🛡️ Ẩn thử nghiệm", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PermissionWarningCard(
    hasSecure: Boolean,
    hasAccessibility: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenPermissionGranter: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = BatteryLow.copy(alpha = 0.1f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = BatteryLow, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Chưa đủ quyền hoạt động tự động", fontWeight = FontWeight.Bold, color = BatteryLow, fontSize = 13.sp)
            }
            Spacer(modifier = Modifier.height(6.dp))

            if (!hasSecure) {
                Text("• Thiếu quyền WRITE_SECURE_SETTINGS để can thiệp cài đặt hệ thống.", fontSize = 12.sp)
            }
            if (!hasAccessibility) {
                Text("• Chưa bật Dịch vụ Trợ năng để phát hiện khi mở app ngân hàng.", fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!hasSecure) {
                    Button(
                        onClick = onOpenPermissionGranter,
                        colors = ButtonDefaults.buttonColors(containerColor = BatteryLow),
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text("Cấp quyền qua Shizuku", fontSize = 11.sp, color = Color.White)
                    }
                }
                if (!hasAccessibility) {
                    OutlinedButton(
                        onClick = onOpenAccessibility,
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text("Mở Cài đặt Trợ năng", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoRevertTimerCard(
    autoRevertMinutes: Int,
    revertOnScreenOff: Boolean,
    autoRestartShizuku: Boolean,
    relaunchApp: Boolean,
    onSelectMinutes: (Int) -> Unit,
    onToggleScreenOff: (Boolean) -> Unit,
    onToggleAutoShizuku: (Boolean) -> Unit,
    onToggleRelaunch: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("TỰ ĐỘNG BẬT LẠI (AUTO RE-ENABLE TIMER)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Thời gian tự động bật lại cài đặt sau khi mở app:", fontSize = 12.sp)

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(2, 5, 10, 15, 30).forEach { mins ->
                    val isSelected = autoRevertMinutes == mins
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectMinutes(mins) },
                        label = {
                            Text(
                                if (mins == 10) "10p (Chuẩn)" else "${mins}p",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            Slider(
                value = autoRevertMinutes.toFloat(),
                onValueChange = { onSelectMinutes(it.toInt()) },
                valueRange = 1f..60f,
                steps = 58
            )
            Text(
                "Tùy chỉnh: $autoRevertMinutes phút",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.End)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Switch Screen Off
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Bật lại ngay khi khóa màn hình", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Khôi phục cài đặt khi tắt máy, không cần đợi hết giờ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Switch(checked = revertOnScreenOff, onCheckedChange = onToggleScreenOff)
            }

            // Switch Auto Shizuku
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tự bật lại Shizuku sau khi khôi phục", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Dùng ShizukuAutoStarter kích hoạt Shizuku qua ADB 5555", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Switch(checked = autoRestartShizuku, onCheckedChange = onToggleAutoShizuku)
            }

            // Switch Relaunch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Đóng ứng dụng trước khi ẩn (Relaunch)", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text("Xóa cache quét bảo mật của ngân hàng lúc khởi động", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Switch(checked = relaunchApp, onCheckedChange = onToggleRelaunch)
            }
        }
    }
}

@Composable
private fun ProtectionScopeCard(
    hideDevOptions: Boolean,
    hideAdb: Boolean,
    hideWirelessAdb: Boolean,
    hideAccessibility: Boolean,
    onToggleDevOptions: (Boolean) -> Unit,
    onToggleAdb: (Boolean) -> Unit,
    onToggleWirelessAdb: (Boolean) -> Unit,
    onToggleAccessibility: (Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("CÁC THÀNH PHẦN CẦN ẨN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text("Ẩn Tùy chọn nhà phát triển (Dev Options)", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = hideDevOptions, onCheckedChange = onToggleDevOptions)
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text("Ẩn Gỡ lỗi USB (ADB Debugging)", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = hideAdb, onCheckedChange = onToggleAdb)
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text("Ẩn Gỡ lỗi không dây (Wireless Debugging)", fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = hideWirelessAdb, onCheckedChange = onToggleWirelessAdb)
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tạm thời lọc Dịch vụ Trợ năng khác", fontSize = 13.sp)
                    Text("Tránh app ngân hàng chặn vì phát hiện Accessibility", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Switch(checked = hideAccessibility, onCheckedChange = onToggleAccessibility)
            }
        }
    }
}

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
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!item.isShielded) }
            .padding(vertical = 6.dp, horizontal = 4.dp)
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.appName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.isAutoDetected) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "Tự động nhận diện",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                } else if (item.isPresetBank) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "Ngân hàng",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
            Text(
                text = item.packageName,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Switch(
            checked = item.isShielded,
            onCheckedChange = onToggle
        )
    }
}
