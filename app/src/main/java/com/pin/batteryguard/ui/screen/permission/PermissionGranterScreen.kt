package com.pin.batteryguard.ui.screen.permission

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pin.batteryguard.permission.model.DynamicPermissionItem
import com.pin.batteryguard.permission.model.PermissionCategory
import com.pin.batteryguard.permission.model.PermissionPreset
import com.pin.batteryguard.permission.model.PermissionStatus
import com.pin.batteryguard.ui.components.*
import com.pin.batteryguard.ui.theme.BatteryFull
import com.pin.batteryguard.ui.theme.BatteryLow
import com.pin.batteryguard.util.PackageHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionGranterScreen(
    onNavigateBack: () -> Unit,
    viewModel: PermissionGranterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAppPickerSheet by remember { mutableStateOf(false) }

    // Hiển thị Toast khi có phản hồi
    LaunchedEffect(uiState.statusFeedback) {
        uiState.statusFeedback?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearFeedback()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Trình Cấp Quyền Nâng Cao",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Động theo Manifest · Sạch 100% · Cấp tốc độ cao",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshPermissionsForSelectedApp() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Làm mới")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            // Selected App Header Card (Tối ưu padding và kích thước)
            uiState.selectedApp?.let { app ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { showAppPickerSheet = true },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIconLazyImage(
                            packageName = app.packageName,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = app.appName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (app.isSelfApp) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiary,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "App này",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiary,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        )
                                    }
                                } else if (app.isAutomationApp) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "Auto",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        )
                                    }
                                } else if (app.isChatOrBankApp) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondary,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "Chat/Bank",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondary,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        )
                                    }
                                }
                                if (app.isLockedInRecents) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "🔒 Recents",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = app.packageName,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { showAppPickerSheet = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Đổi ứng dụng",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Thanh kịch bản cấp quyền 1-chạm (1-Tap Presets Toolbar) - Tinh gọn, đồng bộ
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1. Nền Bất Tử
                CompactTonalChipButton(
                    text = "🚀 Nền Bất Tử",
                    onClick = { viewModel.applyPreset(PermissionPreset.UNLIMITED_BACKGROUND) },
                    enabled = !uiState.isBatchProcessing && uiState.selectedApp != null
                )

                // 2. Safe Runtime
                CompactTonalChipButton(
                    text = "🛡️ Full Runtime",
                    onClick = { viewModel.applyPreset(PermissionPreset.SAFE_RUNTIME) },
                    enabled = !uiState.isBatchProcessing && uiState.selectedApp != null
                )

                // 3. Tool & Mod Nổi
                CompactTonalChipButton(
                    text = "⚙️ Tool/Overlay",
                    onClick = { viewModel.applyPreset(PermissionPreset.TOOLS_OVERLAY) },
                    enabled = !uiState.isBatchProcessing && uiState.selectedApp != null
                )

                // 4. Xiaomi Full (nếu là thiết bị Xiaomi)
                if (uiState.isXiaomiDevice) {
                    CompactTonalChipButton(
                        text = "⚡ Fix Xiaomi",
                        onClick = { viewModel.applyPreset(PermissionPreset.XIAOMI_FULL) },
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        enabled = !uiState.isBatchProcessing && uiState.selectedApp != null
                    )
                }

                // 5. Hoàn tác Snapshot (Rollback)
                if (uiState.hasSnapshotToRollback) {
                    CompactTonalChipButton(
                        text = if (uiState.isRollingBack) "Đang hoàn tác..." else "⏪ Hoàn tác",
                        onClick = { viewModel.rollbackLatestSnapshot() },
                        isLoading = uiState.isRollingBack,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        enabled = !uiState.isRollingBack && uiState.selectedApp != null
                    )
                }
            }

            // Nút "Cấp tất cả quyền hợp lệ" chuẩn 40dp
            StandardActionButton(
                text = if (uiState.isBatchProcessing) "Đang thực thi siêu tốc..." else "⚡ Cấp tất cả quyền (${uiState.permissions.count { it.status == PermissionStatus.DENIED && it.isGrantable }})",
                icon = if (uiState.isBatchProcessing) null else Icons.Filled.Check,
                onClick = { viewModel.requestGrantAllPermissions() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                enabled = !uiState.isBatchProcessing && uiState.selectedApp != null,
                isLoading = uiState.isBatchProcessing,
                height = 40.dp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Danh sách các quyền thực tế theo Manifest
            val grantedCount = uiState.permissions.count { it.status == PermissionStatus.GRANTED }
            val totalCount = uiState.permissions.size

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "QUYỀN ĐÃ KHAI BÁO ($grantedCount/$totalCount ĐÃ CẤP)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "100% Hợp lệ",
                    fontSize = 10.sp,
                    color = BatteryFull,
                    fontWeight = FontWeight.Bold
                )
            }

            if (uiState.permissions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Ứng dụng này không khai báo quyền đặc biệt nào trong AndroidManifest.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    items(uiState.permissions, key = { it.name }) { item ->
                        DynamicPermissionCardItem(
                            item = item,
                            onToggle = { enable -> viewModel.requestTogglePermission(item, enable) }
                        )
                    }
                }
            }
        }
    }

    // Hộp thoại xác nhận khi cấp quyền cho chính BatteryGuard
    if (uiState.showSelfGrantDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSelfGrantDialog() },
            title = {
                Text("Xác nhận cấp quyền cho BatteryGuard", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text(
                    "Theo cơ chế bảo mật của Android, khi cấp hoặc thay đổi quyền hệ thống cho chính BatteryGuard, hệ điều hành sẽ tự động khởi động lại ứng dụng để nạp quyền mới.\n\nBạn có muốn tiếp tục không?",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmSelfGrant() }) {
                    Text("Đồng ý")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { viewModel.dismissSelfGrantDialog() }) {
                    Text("Hủy")
                }
            }
        )
    }

    // Modal Bottom Sheet để chọn ứng dụng
    if (showAppPickerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAppPickerSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                // Header gọn gàng: Tiêu đề nhỏ + Nút đóng nhanh
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Chọn ứng dụng cấp quyền",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = { showAppPickerSheet = false },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Đóng",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // 1. Thanh tìm kiếm siêu gọn (ĐẨY LÊN CAO NHẤT)
                CompactSearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = { viewModel.setSearchQuery(it) },
                    placeholder = "Tìm theo tên hoặc package...",
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 2. Category Chips gọn gàng cao 28-30dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CompactFilterChip(
                        selected = uiState.activeCategory == AppFilterCategory.AUTOMATION,
                        onClick = { viewModel.setCategory(AppFilterCategory.AUTOMATION) },
                        label = "Tự động (${uiState.allApps.count { it.isAutomationApp }})",
                        icon = "✨"
                    )
                    CompactFilterChip(
                        selected = uiState.activeCategory == AppFilterCategory.CHAT_AND_BANKING,
                        onClick = { viewModel.setCategory(AppFilterCategory.CHAT_AND_BANKING) },
                        label = "Chat/Bank (${uiState.allApps.count { it.isChatOrBankApp }})",
                        icon = "💬"
                    )
                    CompactFilterChip(
                        selected = uiState.activeCategory == AppFilterCategory.USER_INSTALLED,
                        onClick = { viewModel.setCategory(AppFilterCategory.USER_INSTALLED) },
                        label = "Đã cài (${uiState.allApps.count { !it.isSystem }})"
                    )
                    CompactFilterChip(
                        selected = uiState.activeCategory == AppFilterCategory.ALL,
                        onClick = { viewModel.setCategory(AppFilterCategory.ALL) },
                        label = "Tất cả (${uiState.allApps.size})"
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(uiState.filteredApps, key = { it.packageName }) { app ->
                            CompactAppPickerItem(
                                appName = app.appName,
                                packageName = app.packageName,
                                isLockedInRecents = app.isLockedInRecents,
                                isSelected = uiState.selectedApp?.packageName == app.packageName,
                                onClick = {
                                    viewModel.selectApp(app)
                                    showAppPickerSheet = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Thẻ hiển thị quyền động (Clean 100% - Tinh gọn, đồng bộ)
 */
@Composable
fun DynamicPermissionCardItem(
    item: DynamicPermissionItem,
    onToggle: (Boolean) -> Unit
) {
    val isGranted = item.status == PermissionStatus.GRANTED
    val isSignature = item.category == PermissionCategory.SIGNATURE_SYSTEM

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Category Badge
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "${item.category.emoji} ${item.category.displayName}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                            )
                        }

                        // Status Badge
                        val badgeColor = when {
                            isGranted -> BatteryFull
                            isSignature -> Color(0xFFC62828)
                            else -> BatteryLow
                        }
                        val badgeText = when {
                            isGranted -> "ĐÃ CẤP"
                            isSignature -> "KHÓA (ROM KEY)"
                            else -> "CHƯA CẤP"
                        }
                        Surface(
                            color = badgeColor.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = badgeText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                            )
                        }
                    }
                }

                if (item.isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Switch(
                        checked = isGranted,
                        onCheckedChange = onToggle,
                        enabled = item.isGrantable && !isSignature
                    )
                }
            }

            if (!item.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.description,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    lineHeight = 15.sp
                )
            }

            // Ghi chú tên quyền hệ thống nhỏ gọn ở dưới
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = item.name,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
