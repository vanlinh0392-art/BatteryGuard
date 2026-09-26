package com.pin.batteryguard.ui.screen.apps

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pin.batteryguard.ui.components.AppUsageCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    viewModel: AppListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    var isSearchVisible by remember { mutableStateOf(true) }
    var previousIndex by remember { mutableStateOf(0) }
    var previousScrollOffset by remember { mutableStateOf(0) }

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
                title = { Text("Mức tiêu thụ của ứng dụng", fontSize = 20.sp) },
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
                .padding(horizontal = 16.dp)
        ) {
            // Search Input
            AnimatedVisibility(
                visible = isSearchVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        placeholder = { Text("Tìm tên hoặc package app...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Tìm kiếm") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // Filter row chips
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                FilterChip(
                    selected = uiState.selectedFilter == AppFilter.ALL,
                    onClick = { viewModel.onFilterSelected(AppFilter.ALL) },
                    label = { Text("Tất cả", fontSize = 12.sp) }
                )
                Spacer(modifier = Modifier.width(6.dp))
                FilterChip(
                    selected = uiState.selectedFilter == AppFilter.RUNNING,
                    onClick = { viewModel.onFilterSelected(AppFilter.RUNNING) },
                    label = { Text("Đang chạy", fontSize = 12.sp) }
                )
                Spacer(modifier = Modifier.width(6.dp))
                FilterChip(
                    selected = uiState.selectedFilter == AppFilter.STOPPED,
                    onClick = { viewModel.onFilterSelected(AppFilter.STOPPED) },
                    label = { Text("Đã dừng", fontSize = 12.sp) }
                )
                Spacer(modifier = Modifier.width(6.dp))
                FilterChip(
                    selected = uiState.selectedFilter == AppFilter.SYSTEM,
                    onClick = { viewModel.onFilterSelected(AppFilter.SYSTEM) },
                    label = { Text("Ngoại lệ", fontSize = 12.sp) }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Lazy list
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
                    Text("Không tìm thấy ứng dụng phù hợp.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(
                    state = lazyListState,
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
                            onAllowActiveUseStop = { viewModel.allowActiveUseStop(appInfo) }
                        )
                    }
                }
            }
        }
    }
}
