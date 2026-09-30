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
            // Selected App Header Card (Lazy Icon Load)
            uiState.selectedApp?.let { app ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clickable { showAppPickerSheet = true },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIconLazyImage(
                            packageName = app.packageName,
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = app.appName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (app.isSelfApp) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiary,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "App này",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                } else if (app.isAutomationApp) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "Auto",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                } else if (app.isChatOrBankApp) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondary,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "Chat/Bank",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = app.packageName,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { showAppPickerSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Đổi ứng dụng",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Thanh kịch bản cấp quyền 1-chạm (1-Tap Presets Toolbar)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Nền Bất Tử
                FilledTonalButton(
                    onClick = { viewModel.applyPreset(PermissionPreset.UNLIMITED_BACKGROUND) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !uiState.isBatchProcessing && uiState.selectedApp != null
                ) {
                    Text("🚀 Nền Bất Tử", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // 2. Safe Runtime
                FilledTonalButton(
                    onClick = { viewModel.applyPreset(PermissionPreset.SAFE_RUNTIME) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !uiState.isBatchProcessing && uiState.selectedApp != null
                ) {
                    Text("🛡️ Full Runtime", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // 3. Tool & Mod Nổi
                FilledTonalButton(
                    onClick = { viewModel.applyPreset(PermissionPreset.TOOLS_OVERLAY) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp),
                    enabled = !uiState.isBatchProcessing && uiState.selectedApp != null
                ) {
                    Text("⚙️ Tool/Overlay", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // 4. Xiaomi Full (nếu là thiết bị Xiaomi)
                if (uiState.isXiaomiDevice) {
                    FilledTonalButton(
                        onClick = { viewModel.applyPreset(PermissionPreset.XIAOMI_FULL) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        ),
                        enabled = !uiState.isBatchProcessing && uiState.selectedApp != null
                    ) {
                        Text("⚡ Fix Xiaomi", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // 5. Hoàn tác Snapshot (Rollback)
                if (uiState.hasSnapshotToRollback) {
                    OutlinedButton(
                        onClick = { viewModel.rollbackLatestSnapshot() },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !uiState.isRollingBack && uiState.selectedApp != null
                    ) {
                        if (uiState.isRollingBack) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                        } else {
                            Icon(Icons.Filled.Undo, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text("⏪ Hoàn tác", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Nút "Cấp tất cả quyền hợp lệ"
            Button(
                onClick = { viewModel.requestGrantAllPermissions() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !uiState.isBatchProcessing && uiState.selectedApp != null
            ) {
                if (uiState.isBatchProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Đang thực thi siêu tốc qua Shell Pipeline...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "⚡ Cấp tất cả quyền hợp lệ (${uiState.permissions.count { it.status == PermissionStatus.DENIED && it.isGrantable }})", fontWeight = FontWeight.Bold)
                }
            }

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
                    .fillMaxHeight(0.85f)
                    .padding(16.dp)
            ) {
                Text(
                    text = "Chọn ứng dụng cần cấp quyền",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Thanh tìm kiếm
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Tìm kiếm theo tên hoặc package...") },
                    leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(imageVector = Icons.Filled.Close, contentDescription = "Xóa")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Category Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = uiState.activeCategory == AppFilterCategory.AUTOMATION,
                        onClick = { viewModel.setCategory(AppFilterCategory.AUTOMATION) },
                        label = { Text("✨ Tự động hóa (${uiState.allApps.count { it.isAutomationApp }})") }
                    )
                    FilterChip(
                        selected = uiState.activeCategory == AppFilterCategory.CHAT_AND_BANKING,
                        onClick = { viewModel.setCategory(AppFilterCategory.CHAT_AND_BANKING) },
                        label = { Text("💬 Chat & Ngân hàng (${uiState.allApps.count { it.isChatOrBankApp }})") }
                    )
                    FilterChip(
                        selected = uiState.activeCategory == AppFilterCategory.USER_INSTALLED,
                        onClick = { viewModel.setCategory(AppFilterCategory.USER_INSTALLED) },
                        label = { Text("Ứng dụng đã cài (${uiState.allApps.count { !it.isSystem }})") }
                    )
                    FilterChip(
                        selected = uiState.activeCategory == AppFilterCategory.ALL,
                        onClick = { viewModel.setCategory(AppFilterCategory.ALL) },
                        label = { Text("Tất cả (${uiState.allApps.size})") }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(uiState.filteredApps, key = { it.packageName }) { app ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectApp(app)
                                        showAppPickerSheet = false
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (uiState.selectedApp?.packageName == app.packageName)
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppIconLazyImage(
                                        packageName = app.packageName,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.appName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = app.packageName,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Thẻ hiển thị quyền động (Clean 100% - Không hiển thị quyền chưa khai báo)
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
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
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }

                        // Status Badge: Chỉ có ĐÃ CẤP hoặc CHƯA CẤP (Hoàn toàn không có rác "CHƯA KHAI BÁO")
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
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (item.isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
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
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = item.description,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    lineHeight = 16.sp
                )
            }

            // Ghi chú tên quyền hệ thống nhỏ gọn ở dưới
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.name,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        }
    }
}

/**
 * Lazy Image nạp Icon theo packageName qua PackageHelper (Zero-Bitmap State)
 */
@Composable
fun AppIconLazyImage(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val drawable = remember(packageName) {
        PackageHelper.getAppIcon(context, packageName)
    }

    if (drawable != null) {
        val bitmap = remember(drawable) {
            try {
                drawable.toBitmap(96, 96)
            } catch (_: Exception) {
                null
            }
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = modifier
            )
            return
        }
    }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.PhoneAndroid,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
