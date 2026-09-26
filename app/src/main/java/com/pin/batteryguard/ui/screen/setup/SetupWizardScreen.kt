package com.pin.batteryguard.ui.screen.setup

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import com.pin.batteryguard.ui.theme.BatteryFull
import com.pin.batteryguard.ui.theme.BatteryLow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pin.batteryguard.R
import com.pin.batteryguard.shizuku.ShizukuStatus
import com.pin.batteryguard.ui.components.PermissionCard
import com.pin.batteryguard.util.XiaomiHelper

@Composable
fun SetupWizardScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Cập nhật permission mỗi lần app quay lại từ Settings (Lifecycle ON_RESUME)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        
        // Bước tiêu đề
        Text(
            text = "Thiết lập BatteryGuard",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Text(
            text = "Bước ${uiState.currentStep + 1} trên 4",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Nội dung từng bước
        Column(
            modifier = Modifier.weight(1f)
        ) {
            AnimatedContent(
                targetState = uiState.currentStep,
                label = "SetupSteps"
            ) { step ->
                when (step) {
                    0 -> StepWelcome()
                    1 -> StepShizuku(uiState.shizukuStatus, onRequestPermission = { viewModel.requestShizukuPermission() })
                    2 -> StepPermissions(
                        uiState = uiState,
                        onGrantUsage = {
                            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        },
                        onGrantNotif = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        },
                        onGrantBattery = {
                            XiaomiHelper.requestBatteryWhitelist(context)
                        }
                    )
                    3 -> StepXiaomi(context = context)
                }
            }
        }

        // Điều khiển chuyển bước
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.currentStep > 0) {
                OutlinedButton(
                    onClick = { viewModel.prevStep() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Quay lại")
                }
                Spacer(modifier = Modifier.width(16.dp))
            }
            
            Button(
                onClick = {
                    if (uiState.currentStep < 3) {
                        viewModel.nextStep()
                    } else {
                        viewModel.completeSetup(onSetupComplete)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text(if (uiState.currentStep < 3) "Tiếp tục" else "Bắt đầu")
            }
        }
    }
}

@Composable
fun StepWelcome() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Icon(
            imageVector = Icons.Filled.ElectricBolt,
            contentDescription = "BatteryGuard Logo",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Quản lý pin thông minh",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "BatteryGuard giúp tự động force stop các app tiêu thụ quá % pin cài đặt khi màn hình khóa.\n\nSử dụng dịch vụ chạy ngầm được tối ưu hóa để kéo dài tuổi thọ pin thiết bị.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

@Composable
fun StepShizuku(
    status: ShizukuStatus,
    onRequestPermission: () -> Unit
) {
    val context = LocalContext.current
    Column {
        Text(
            text = "Kết nối dịch vụ Shizuku",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "BatteryGuard sử dụng framework Shizuku để ra lệnh force stop không cần root. Nếu chưa cài Shizuku, hãy click button dưới đây để tải về.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Trạng thái Shizuku:",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        
        val statusText = when (status) {
            ShizukuStatus.READY -> "Đã kết nối ✓"
            ShizukuStatus.RUNNING_NO_PERMISSION -> "Chờ cấp quyền..."
            ShizukuStatus.INSTALLED_NOT_RUNNING -> "Chưa chạy (Hãy mở Shizuku và Start Wireless Debugging)"
            ShizukuStatus.NOT_INSTALLED -> "Chưa cài đặt"
        }
        val statusColor = if (status == ShizukuStatus.READY) BatteryFull else BatteryLow

        Text(
            text = statusText,
            color = statusColor,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases"))
                    context.startActivity(intent)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Tải Shizuku")
            }

            if (status == ShizukuStatus.RUNNING_NO_PERMISSION) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cấp quyền")
                }
            }

            if (status == ShizukuStatus.INSTALLED_NOT_RUNNING) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                        if (launchIntent != null) {
                            context.startActivity(launchIntent)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Mở Shizuku")
                }
            }
        }
    }
}

@Composable
fun StepPermissions(
    uiState: SetupUiState,
    onGrantUsage: () -> Unit,
    onGrantNotif: () -> Unit,
    onGrantBattery: () -> Unit
) {
    Column {
        Text(
            text = "Cấp quyền hệ thống",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        PermissionCard(
            title = "Quyền truy cập dữ liệu sử dụng",
            description = "Dùng để theo dõi thời gian chạy nền của ứng dụng",
            isGranted = uiState.usageAccessGranted,
            onGrant = onGrantUsage
        )
        
        PermissionCard(
            title = "Quyền thông báo",
            description = "Dùng hiển thị thông báo giám sát pin và danh sách app đã đóng",
            isGranted = uiState.notificationGranted,
            onGrant = onGrantNotif
        )
        
        PermissionCard(
            title = "Bỏ qua tối ưu hóa pin",
            description = "Tránh bị hệ thống Android mặc định kill service chạy ngầm",
            isGranted = uiState.batteryOptimizationIgnored,
            onGrant = onGrantBattery
        )
    }
}

@Composable
fun StepXiaomi(context: android.content.Context) {
    Column {
        Text(
            text = "Tối ưu hóa Xiaomi / HyperOS",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Hệ điều hành HyperOS giới hạn app chạy ngầm rất khắt khe. Hãy thực hiện 2 bước dưới đây để service không bị tắt đột ngột:",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(16.dp))

        XiaomiHelper.getXiaomiSetupSteps().forEach { step ->
            CardSetupStep(step = step, context = context)
        }
    }
}

@Composable
fun CardSetupStep(
    step: XiaomiHelper.SetupStep,
    context: android.content.Context
) {
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
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(step.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(step.description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = { step.action(context) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(step.buttonText, fontSize = 12.sp)
            }
        }
    }
}
