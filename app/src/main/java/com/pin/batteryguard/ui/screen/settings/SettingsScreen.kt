package com.pin.batteryguard.ui.screen.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import com.pin.batteryguard.domain.model.MonitoringConfig
import com.pin.batteryguard.ui.theme.BatteryFull
import com.pin.batteryguard.ui.theme.BatteryLow
import com.pin.batteryguard.util.XiaomiHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToSetup: () -> Unit,
    onNavigateToPermissionGranter: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var showClearDialog by remember { mutableStateOf(false) }
    var periodMenuExpanded by remember { mutableStateOf(false) }

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
            // Section 1: Giám sát
            Text("GIÁM SÁT PIN", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Bật giám sát", fontWeight = FontWeight.SemiBold)
                    Text("Quét khi tắt màn", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Switch(
                    checked = uiState.config.isMonitoringEnabled,
                    onCheckedChange = { viewModel.updateConfig(uiState.config.copy(isMonitoringEnabled = it)) }
                )
            }

            // Slider: Threshold % pin
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = String.format("Ngưỡng dừng: %.2f%%/h", uiState.config.drainThresholdPercent),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Dừng app vượt ngưỡng",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Slider(
                    value = uiState.config.drainThresholdPercent,
                    onValueChange = { 
                        viewModel.updateConfig(uiState.config.copy(
                            drainThresholdPercent = it
                        ))
                    },
                    valueRange = MonitoringConfig.MIN_FORCE_STOP_PERCENT_PER_HOUR..MonitoringConfig.MAX_FORCE_STOP_PERCENT_PER_HOUR,
                    steps = 46
                )
                Text(
                    "Khuyến nghị: 1.0%/h",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
            }

            // Dropdown: Period quét (phút)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Chu kỳ quét", fontWeight = FontWeight.SemiBold)
                    Text("Tần suất quét ngầm", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                
                ExposedDropdownMenuBox(
                    expanded = periodMenuExpanded,
                    onExpandedChange = { periodMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = "${uiState.config.monitoringPeriodMinutes} phút",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = periodMenuExpanded) },
                        modifier = Modifier.width(130.dp).menuAnchor(),
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    ExposedDropdownMenu(
                        expanded = periodMenuExpanded,
                        onDismissRequest = { periodMenuExpanded = false }
                    ) {
                        listOf(10, 15, 20, 30).forEach { mins ->
                            DropdownMenuItem(
                                text = { Text("$mins phút") },
                                onClick = {
                                    viewModel.updateConfig(uiState.config.copy(monitoringPeriodMinutes = mins))
                                    periodMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Toggles
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tự động dừng app", fontWeight = FontWeight.SemiBold)
                    Text("Dừng thay vì cảnh báo", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Switch(
                    checked = uiState.config.autoForceStop,
                    onCheckedChange = { viewModel.updateConfig(uiState.config.copy(autoForceStop = it)) }
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hiện thông báo", fontWeight = FontWeight.SemiBold)
                    Text("Báo cáo khi dừng", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
                Switch(
                    checked = uiState.config.notifyBeforeStop,
                    onCheckedChange = { viewModel.updateConfig(uiState.config.copy(notifyBeforeStop = it)) }
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Ẩn app hệ thống", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Chỉ hiện app cài",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = uiState.config.excludeSystemApps,
                    onCheckedChange = {
                        viewModel.updateConfig(uiState.config.copy(excludeSystemApps = it))
                    }
                )
            }

            // Section 2: Chế độ xử lý (Force Stop vs Freeze)
            Spacer(modifier = Modifier.height(12.dp))
            Text("CHẾ ĐỘ XỬ LÝ", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
            
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        viewModel.updateConfig(uiState.config.copy(enableFreezeMode = false))
                    }.padding(vertical = 6.dp)
                ) {
                    RadioButton(
                        selected = !uiState.config.enableFreezeMode,
                        onClick = { viewModel.updateConfig(uiState.config.copy(enableFreezeMode = false)) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Chỉ dừng app", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Không đóng băng ngầm", fontSize = 11.sp, color = Color.Gray)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable {
                        viewModel.updateConfig(uiState.config.copy(enableFreezeMode = true))
                    }.padding(vertical = 6.dp)
                ) {
                    RadioButton(
                        selected = uiState.config.enableFreezeMode,
                        onClick = { viewModel.updateConfig(uiState.config.copy(enableFreezeMode = true)) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Đóng băng tái phạm", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Đóng băng khi lặp", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }

            // Section: CÔNG CỤ HỆ THỐNG
            Spacer(modifier = Modifier.height(12.dp))
            Text("CÔNG CỤ HỆ THỐNG", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToPermissionGranter() },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Trình cấp quyền",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Cấp quyền qua Shizuku",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
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
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Tự khởi chạy", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { XiaomiHelper.openBatterySaverSettings(context) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Tiết kiệm pin", fontSize = 12.sp)
                    }
                }
            }

            // Section 5: Dữ liệu
            Spacer(modifier = Modifier.height(16.dp))

            // Section 6: Phiên bản & Cập nhật GitHub
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Phiên bản", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("v${uiState.appVersion}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.checkForUpdate(isUserInitiated = true) },
                            enabled = !uiState.isCheckingUpdate,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (uiState.isCheckingUpdate) {
                                androidx.compose.material3.CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Đang kiểm tra...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Kiểm tra cập nhật", fontSize = 12.sp)
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Xóa lịch sử dữ liệu")
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    viewModel.clearFrozenApps()
                    android.widget.Toast.makeText(context, "Đã rã băng tất cả!", android.widget.Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Rã băng toàn bộ app")
            }

            // Reset Setup button (để debug/chạy lại setup wizard)
            TextButton(
                onClick = {
                    viewModel.resetSetup()
                    onNavigateToSetup()
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
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
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Tải & Cài đặt")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissUpdateDialog() }) {
                        Text("Để sau")
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
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Xác nhận")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("Hủy")
                    }
                }
            )
        }
    }
}
