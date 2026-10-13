package com.pin.batteryguard.ui.screen.apps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pin.batteryguard.domain.model.AppBatteryInfo
import com.pin.batteryguard.ui.components.AppUsageCard
import com.pin.batteryguard.ui.components.CompactFilterChip
import com.pin.batteryguard.ui.components.CompactSearchBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    viewModel: AppListViewModel = hiltViewModel(),
    onNavigateToPermissionGranter: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var isSearchVisible by remember { mutableStateOf(true) }
    var previousIndex by remember { mutableStateOf(0) }
    var previousScrollOffset by remember { mutableStateOf(0) }
    var selectedAppForMenu by remember { mutableStateOf<AppBatteryInfo?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(lazyListState.firstVisibleItemIndex, lazyListState.firstVisibleItemScrollOffset) {
        val currentIndex = lazyListState.firstVisibleItemIndex
        val currentOffset = lazyListState.firstVisibleItemScrollOffset
        
        if (currentIndex == 0 && currentOffset == 0) {
            isSearchVisible = true
        } else if (currentIndex > previousIndex) {
            isSearchVisible = false
        } else if (currentIndex < previousIndex) {
            isSearchVisible = true
        } else {
            if (currentOffset > previousScrollOffset + 15) {
                isSearchVisible = false
            } else if (currentOffset < previousScrollOffset - 15) {
                isSearchVisible = true
            }
        }
        previousIndex = currentIndex
        previousScrollOffset = currentOffset
    }

    LaunchedEffect(uiState.actionResult) {
        uiState.actionResult?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearActionResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tiêu thụ pin app", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { viewModel.loadApps() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Tải lại")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp)
        ) {
            // Thanh tìm kiếm siêu gọn (height 38dp)
            AnimatedVisibility(
                visible = isSearchVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    CompactSearchBar(
                        query = uiState.searchQuery,
                        onQueryChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = "Tìm ứng dụng...",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }

            // Hàng filter chips gọn gàng chuẩn đồng bộ (height 30dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CompactFilterChip(
                    selected = uiState.selectedFilter == AppFilter.ALL,
                    onClick = { viewModel.onFilterSelected(AppFilter.ALL) },
                    label = "Tất cả"
                )
                CompactFilterChip(
                    selected = uiState.selectedFilter == AppFilter.RUNNING,
                    onClick = { viewModel.onFilterSelected(AppFilter.RUNNING) },
                    label = "Đang chạy"
                )
                CompactFilterChip(
                    selected = uiState.selectedFilter == AppFilter.STOPPED,
                    onClick = { viewModel.onFilterSelected(AppFilter.STOPPED) },
                    label = "Đã dừng"
                )
                CompactFilterChip(
                    selected = uiState.selectedFilter == AppFilter.SYSTEM,
                    onClick = { viewModel.onFilterSelected(AppFilter.SYSTEM) },
                    label = "Ngoại lệ"
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Danh sách ứng dụng
            if (uiState.isLoading) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.filteredApps.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text("Không tìm thấy app", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = uiState.filteredApps,
                        key = { "${it.userId}:${it.uid}:${it.packageName}" }
                    ) { appInfo ->
                        AppUsageCard(
                            appInfo = appInfo,
                            onForceStop = { viewModel.forceStopApp(appInfo) },
                            onFreeze = { viewModel.freezeApp(appInfo) },
                            onAddException = { viewModel.addToException(appInfo.packageName, appInfo.appName) },
                            onAllowActiveUseStop = { viewModel.allowActiveUseStop(appInfo) },
                            onAppClick = { onNavigateToPermissionGranter(appInfo.packageName) },
                            onAppLongClick = { selectedAppForMenu = appInfo }
                        )
                    }
                }
            }
        }
    }

    // ModalBottomSheet Menu tác vụ ngữ cảnh khi nhấn giữ app
    selectedAppForMenu?.let { app ->
        ModalBottomSheet(
            onDismissRequest = { selectedAppForMenu = null },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 28.dp)
            ) {
                // Header: Icon + Tên + Package
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    val iconBitmap = remember(app.packageName) {
                        try {
                            app.appIcon?.toBitmap(96, 96)?.asImageBitmap()
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = app.appName,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = app.appName.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(bottom = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                // 1. Tùy chọn: Buộc dừng (Force Stop)
                if (app.isActionAllowed) {
                    ContextActionItem(
                        icon = Icons.Filled.Stop,
                        iconTint = MaterialTheme.colorScheme.error,
                        iconBg = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                        title = "Buộc dừng ứng dụng",
                        subtitle = "Dừng ngay các tiến trình chạy ngầm",
                        onClick = {
                            viewModel.forceStopApp(app)
                            selectedAppForMenu = null
                        }
                    )
                }

                // 2. Tùy chọn 1 chiều: Đóng băng / Rã đông
                if (app.isFrozen) {
                    ContextActionItem(
                        icon = Icons.Filled.PlayArrow,
                        iconTint = MaterialTheme.colorScheme.primary,
                        iconBg = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        title = "Rã đông ứng dụng",
                        subtitle = "Kích hoạt lại ứng dụng để sử dụng bình thường",
                        onClick = {
                            viewModel.freezeApp(app)
                            selectedAppForMenu = null
                        }
                    )
                } else {
                    ContextActionItem(
                        icon = Icons.Filled.AcUnit,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        iconBg = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        title = "Đóng băng ứng dụng",
                        subtitle = "Vô hiệu hóa ứng dụng, không tiêu tốn tài nguyên",
                        onClick = {
                            viewModel.freezeApp(app)
                            selectedAppForMenu = null
                        }
                    )
                }

                // 3. Tùy chọn 1 chiều: Ngoại lệ / Bỏ ngoại lệ
                if (app.isException) {
                    ContextActionItem(
                        icon = Icons.Filled.Shield,
                        iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
                        iconBg = MaterialTheme.colorScheme.surfaceVariant,
                        title = "Bỏ khỏi danh sách ngoại lệ",
                        subtitle = "Cho phép hệ thống tự động tối ưu hóa và dừng khi cần",
                        onClick = {
                            viewModel.removeFromException(app.packageName, app.appName)
                            selectedAppForMenu = null
                        }
                    )
                } else {
                    ContextActionItem(
                        icon = Icons.Filled.Shield,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        iconBg = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        title = "Thêm vào danh sách ngoại lệ",
                        subtitle = "Bảo vệ ứng dụng không bị tự động dừng hoặc tối ưu",
                        onClick = {
                            viewModel.addToException(app.packageName, app.appName)
                            selectedAppForMenu = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextActionItem(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
