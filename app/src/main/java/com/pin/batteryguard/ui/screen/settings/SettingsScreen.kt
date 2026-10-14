package com.pin.batteryguard.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pin.batteryguard.shizuku.ShizukuStatus
import com.pin.batteryguard.util.XiaomiHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToSetup: () -> Unit,
    onNavigateToPermissionGranter: () -> Unit = {},
    onNavigateToExtendedSettings: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.xiaomiFixMessage) {
        uiState.xiaomiFixMessage?.let { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearXiaomiFixMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cài đặt", fontSize = 20.sp) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            // Section: CÔNG CỤ HỆ THỐNG
            Spacer(modifier = Modifier.height(8.dp))
            Text("CÔNG CỤ HỆ THỐNG", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToPermissionGranter() }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Trình cấp quyền",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Cấp quyền qua Shizuku",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }

                    HorizontalDivider(
                        thickness = 0.8.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToExtendedSettings() }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cài đặt mở rộng",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Màn hình, tần số quét, DNS",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            // Section 4: Xiaomi settings (Chỉ hiện nếu là máy Xiaomi)
            if (uiState.isXiaomi) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("CÀI ĐẶT XIAOMI", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { XiaomiHelper.openAutoStartSettings(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(38.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Tự khởi chạy", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { XiaomiHelper.openBatterySaverSettings(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(38.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text("Tiết kiệm pin", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { viewModel.batchFixChatAndBankApps() },
                        modifier = Modifier.weight(1f).height(38.dp),
                        enabled = !uiState.isFixingXiaomi,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        if (uiState.isFixingXiaomi) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            val progressText = uiState.xiaomiProgress?.let { " (${it.first}/${it.second})" } ?: ""
                            Text("Đang tối ưu$progressText...", fontSize = 11.sp, maxLines = 1)
                        } else {
                            Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Thông báo Bank",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.checkForUpdate(isUserInitiated = true) },
                        modifier = Modifier.weight(1f).height(38.dp),
                        enabled = !uiState.isCheckingUpdate,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        if (uiState.isCheckingUpdate) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Đang kiểm tra...", fontSize = 11.sp, maxLines = 1)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Kiểm tra cập nhật",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { viewModel.openFcmDiagnostics() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(38.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Chẩn đoán FCM", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.reconnectFcm() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(38.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Đánh thức FCM", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }

            // Section 5: Dữ liệu
            Spacer(modifier = Modifier.height(16.dp))

            // Section 6: Phiên bản
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Phiên bản", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("v${uiState.appVersion}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }

                    if (!uiState.isXiaomi) {
                        Button(
                            onClick = { viewModel.checkForUpdate(isUserInitiated = true) },
                            enabled = !uiState.isCheckingUpdate,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (uiState.isCheckingUpdate) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Đang kiểm tra...", fontSize = 12.sp, maxLines = 1)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Kiểm tra cập nhật", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section 7: Xóa dữ liệu & Reset
            Text(
                "Quản lý dữ liệu",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Button(
                onClick = { showClearDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(38.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Text("Xóa lịch sử dữ liệu", fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reset Setup button (để debug/chạy lại setup wizard)
            TextButton(
                onClick = {
                    viewModel.resetSetup()
                    onNavigateToSetup()
                },
                modifier = Modifier.fillMaxWidth().height(36.dp).padding(vertical = 2.dp)
            ) {
                Text("Chạy lại hướng dẫn", fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Thông báo Toast từ ViewModel
        LaunchedEffect(uiState.updateMessage) {
            uiState.updateMessage?.let {
                android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
                viewModel.clearUpdateMessage()
            }
        }

        // Dialog cập nhật phiên bản mới từ GitHub
        uiState.updateInfo?.let { updateInfo ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissUpdateDialog() },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎉 Bản cập nhật ${updateInfo.latestVersion}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Column {
                        Text("Đã có phiên bản mới trên GitHub!", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = updateInfo.releaseNotes.ifBlank { "Không có mô tả chi tiết." },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            lineHeight = 16.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.downloadAndInstallUpdate(updateInfo) },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Tải & Cài đặt", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { viewModel.dismissUpdateDialog() },
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Để sau", fontSize = 12.sp)
                    }
                }
            )
        }

        // Dialog clear data
        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Xác nhận xóa dữ liệu?") },
                text = { Text("Toàn bộ lịch sử biểu đồ pin và log dừng app sẽ bị xóa vĩnh viễn. Hành động này không thể hoàn tác.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.clearAllData()
                            showClearDialog = false
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Xác nhận", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showClearDialog = false },
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Hủy", fontSize = 12.sp)
                    }
                }
            )
        }
    }
}
