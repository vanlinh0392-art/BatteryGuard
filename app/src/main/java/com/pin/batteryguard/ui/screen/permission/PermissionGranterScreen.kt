package com.pin.batteryguard.ui.screen.permission

import android.graphics.drawable.Drawable
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
import androidx.compose.foundation.shape.CircleShape
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
import com.pin.batteryguard.permission.model.PermissionType
import com.pin.batteryguard.ui.theme.BatteryFull
import com.pin.batteryguard.ui.theme.BatteryLow

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
                            text = "Hỗ trợ Tasker, MacroDroid & Mọi ứng dụng",
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
            // Selected App Header Card
            uiState.selectedApp?.let { app ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable { showAppPickerSheet = true },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppIconImage(
                            drawable = app.icon,
                            modifier = Modifier
                                .size(48.dp)
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
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = app.packageName,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { showAppPickerSheet = true }) {
                            Icon(imageVector = Icons.Filled.Edit, contentDescription = "Đổi ứng dụng")
                        }
                    }
                }

                // Cảnh báo nếu chọn chính BatteryGuard
                if (app.isSelfApp) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "ℹ️ Lưu ý: Khi cấp quyền cho chính BatteryGuard, hệ điều hành Android sẽ tự động khởi động lại app để áp dụng quyền mới.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                // THẺ CHUYÊN DỤNG: Tối ưu thông báo Xiaomi / HyperOS (FixTBXiaomiChina)
                if (uiState.isXiaomiDevice) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (uiState.isChinaRom) "⚡ Sửa Thông Báo HyperOS China" else "⚡ Sửa Thông Báo Xiaomi/HyperOS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                if (uiState.selectedAppHasXiaomiSnapshot) {
                                    Surface(
                                        color = BatteryFull.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = "Đã tối ưu",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BatteryFull,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Tự khởi chạy (Op 10053/10008) · Miễn trừ Doze · Standby Active · 5 AppOps nền",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                lineHeight = 15.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = { viewModel.fixXiaomiForSelectedApp() },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    enabled = !uiState.isXiaomiFixing
                                ) {
                                    if (uiState.isXiaomiFixing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Đang sửa...", fontSize = 12.sp)
                                    } else {
                                        Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Sửa trễ thông báo", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (uiState.selectedAppHasXiaomiSnapshot) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OutlinedButton(
                                        onClick = { viewModel.restoreXiaomiForSelectedApp() },
                                        shape = RoundedCornerShape(10.dp),
                                        enabled = !uiState.isXiaomiFixing
                                    ) {
                                        Icon(Icons.Filled.Undo, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Hoàn tác", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Batch Action Button: "Cấp tất cả quyền (Grant All)"
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
                    Text(text = "Đang cấp quyền hàng loạt...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "⚡ Cấp tất cả quyền hợp lệ (Grant All)", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Danh sách các quyền nâng cao
            Text(
                text = "DANH MỤC QUYỀN NÂNG CAO (${uiState.permissions.count { it.isGranted }}/${uiState.permissions.size} ĐÃ CẤP)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(uiState.permissions, key = { it.permission.id }) { item ->
                    PermissionCardItem(
                        item = item,
                        onToggle = { enable -> viewModel.requestTogglePermission(item.permission, enable) }
                    )
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
                    "Theo cơ chế bảo mật của Android, khi cấp hoặc thay đổi quyền hệ thống cho chính BatteryGuard, hệ điều hành sẽ tự động đóng ứng dụng để nạp lại quyền mới.\n\nBạn có muốn tiếp tục không?",
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
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("Tìm kiếm theo tên hoặc package...") },
                    leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
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
                        onClick = { viewModel.selectCategory(AppFilterCategory.AUTOMATION) },
                        label = { Text("✨ Tự động hóa (${uiState.allApps.count { it.isAutomationApp }})") }
                    )
                    FilterChip(
                        selected = uiState.activeCategory == AppFilterCategory.CHAT_AND_BANKING,
                        onClick = { viewModel.selectCategory(AppFilterCategory.CHAT_AND_BANKING) },
                        label = { Text("💬 Chat & Ngân hàng (${uiState.allApps.count { it.isChatOrBankApp }})") }
                    )
                    FilterChip(
                        selected = uiState.activeCategory == AppFilterCategory.USER_INSTALLED,
                        onClick = { viewModel.selectCategory(AppFilterCategory.USER_INSTALLED) },
                        label = { Text("Ứng dụng đã cài (${uiState.allApps.count { !it.isSystem }})") }
                    )
                    FilterChip(
                        selected = uiState.activeCategory == AppFilterCategory.ALL,
                        onClick = { viewModel.selectCategory(AppFilterCategory.ALL) },
                        label = { Text("Tất cả (${uiState.allApps.size})") }
                    )
                }

                if (uiState.isXiaomiDevice && uiState.activeCategory == AppFilterCategory.CHAT_AND_BANKING) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = { viewModel.batchFixChatAndBankApps() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        enabled = !uiState.isBatchProcessing
                    ) {
                        if (uiState.isBatchProcessing && uiState.batchProgress != null) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Đang sửa ${uiState.batchProgress?.first}/${uiState.batchProgress?.second}...", fontSize = 12.sp)
                        } else {
                            Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("⚡ Sửa thông báo tất cả app Chat & Ngân hàng", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(uiState.filteredApps, key = { it.packageName }) { app ->
                            val isSelected = app.packageName == uiState.selectedApp?.packageName
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.selectApp(app)
                                        showAppPickerSheet = false
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AppIconImage(
                                        drawable = app.icon,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = app.appName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = app.packageName,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
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

@Composable
fun PermissionCardItem(
    item: PermissionEntryUi,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
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
                        text = item.permission.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Badge Tag: PM / AppOp
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (item.permission.type == PermissionType.RUNTIME_PM) "pm grant" else "appops",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }

                        // Status Badge
                        val isUndeclaredPm = !item.isGranted && !item.isDeclared && item.permission.type == PermissionType.RUNTIME_PM
                        val badgeColor = when {
                            item.isGranted -> BatteryFull
                            isUndeclaredPm -> Color(0xFFE65100)
                            else -> BatteryLow
                        }
                        val badgeText = when {
                            item.isGranted -> "ĐÃ CẤP"
                            isUndeclaredPm -> "CHƯA KHAI BÁO"
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
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
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
                        checked = item.isGranted,
                        onCheckedChange = onToggle
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.permission.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                lineHeight = 16.sp
            )

            if (!item.isGranted && !item.isDeclared && item.permission.type == PermissionType.RUNTIME_PM) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "⚠️ Ứng dụng chưa khai báo quyền này trong AndroidManifest.xml (Android sẽ từ chối nếu cấp qua pm grant).",
                    fontSize = 10.sp,
                    color = Color(0xFFE65100),
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
fun AppIconImage(drawable: Drawable?, modifier: Modifier = Modifier) {
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
