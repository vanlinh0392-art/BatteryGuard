package com.pin.batteryguard.ui.screen.exceptions

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pin.batteryguard.data.db.entity.ExceptionApp
import com.pin.batteryguard.util.PackageHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExceptionListScreen(
    viewModel: ExceptionListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Danh sách ngoại lệ", fontSize = 20.sp) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddDialog(true) }
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Thêm ngoại lệ")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Các ứng dụng trong danh sách này sẽ hoàn toàn được bỏ qua, không bị tự động dừng hay đóng băng khi quét.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            if (uiState.exceptions.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text("Danh sách ngoại lệ trống.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(
                        items = uiState.exceptions,
                        key = { it.packageName }
                    ) { app ->
                        CardExceptionItem(
                            app = app,
                            onRemove = { viewModel.removeException(app.packageName) },
                            context = context
                        )
                    }
                }
            }
        }

        // Add exception dialog
        if (uiState.showAddDialog) {
            DialogAddException(
                uiState = uiState,
                onDismiss = { viewModel.showAddDialog(false) },
                onSearchChanged = { viewModel.onSearchQueryChanged(it) },
                onAppSelected = { pkg, name, reason ->
                    viewModel.addException(pkg, name, reason)
                },
                context = context
            )
        }
    }
}

@Composable
fun CardExceptionItem(
    app: ExceptionApp,
    onRemove: () -> Unit,
    context: android.content.Context
) {
    val dateStr = remember(app.addedAt) {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(app.addedAt))
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(12.dp)
        ) {
            val icon = remember(app.packageName) { PackageHelper.getAppIcon(context, app.packageName) }
            if (icon != null) {
                Image(
                    bitmap = icon.toBitmap().asImageBitmap(),
                    contentDescription = app.appName,
                    modifier = Modifier.size(38.dp)
                )
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                    modifier = Modifier.size(38.dp)
                ) {}
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(app.appName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (app.reason.isNotEmpty()) {
                    Text(
                        text = app.reason,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Text(
                    text = "Đã thêm: $dateStr",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (!app.isSystemDefault) {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Xóa ngoại lệ",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Text(
                    text = "Hệ thống",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }
    }
}

@Composable
fun DialogAddException(
    uiState: ExceptionListUiState,
    onDismiss: () -> Unit,
    onSearchChanged: (String) -> Unit,
    onAppSelected: (pkg: String, name: String, reason: String) -> Unit,
    context: android.content.Context
) {
    var selectedPkg by remember { mutableStateOf<String?>(null) }
    var selectedName by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thêm vào ngoại lệ", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (selectedPkg == null) {
                    // Màn hình chọn app
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchChanged,
                        placeholder = { Text("Tìm ứng dụng...") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Tìm kiếm") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                    ) {
                        LazyColumn {
                            items(uiState.filteredAvailableApps) { (pkg, name) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedPkg = pkg
                                            selectedName = name
                                        }
                                        .padding(10.dp)
                                ) {
                                    val icon = remember(pkg) { PackageHelper.getAppIcon(context, pkg) }
                                    if (icon != null) {
                                        Image(
                                            bitmap = icon.toBitmap().asImageBitmap(),
                                            contentDescription = name,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(name, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                } else {
                    // Màn hình nhập lý do
                    Text("Nhập lý do ngoại lệ cho $selectedName:", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        placeholder = { Text("Lý do (Ví dụ: Game chạy ngầm, Nhạc...)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (selectedPkg != null) {
                Button(
                    onClick = {
                        selectedPkg?.let { pkg ->
                            onAppSelected(pkg, selectedName, reason)
                        }
                    }
                ) {
                    Text("Xác nhận")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (selectedPkg != null) {
                        selectedPkg = null // Quay lại chọn app
                    } else {
                        onDismiss()
                    }
                }
            ) {
                Text(if (selectedPkg != null) "Quay lại" else "Hủy")
            }
        }
    )
}
