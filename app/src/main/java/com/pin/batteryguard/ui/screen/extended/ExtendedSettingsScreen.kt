package com.pin.batteryguard.ui.screen.extended

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.ScreenSearchDesktop
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pin.batteryguard.data.settings.SystemSettingsManager.PowerKeyAction
import com.pin.batteryguard.ui.components.CompactFilterChip
import com.pin.batteryguard.ui.components.CompactTonalChipButton
import com.pin.batteryguard.ui.theme.BatteryLow
import com.pin.batteryguard.ui.theme.Green40
import com.pin.batteryguard.ui.theme.Green80

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtendedSettingsScreen(
    onBack: () -> Unit,
    viewModel: ExtendedSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showCustomDnsDialog by remember { mutableStateOf(false) }
    var customDnsInput by remember { mutableStateOf("") }
    var showRestoreDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Cài đặt mở rộng",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Tinh chỉnh chuyên sâu Xiaomi 14",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadSettings() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Làm mới trạng thái")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. MASTER CONTROL STRIP (~52-54dp)
            item(key = "master_control_card") {
                CompactExtendedMasterControlCard(
                    isShizukuReady = uiState.isShizukuReady,
                    isAutoSentinelEnabled = uiState.isAutoSentinelEnabled,
                    onRestoreClick = { showRestoreDialog = true }
                )
            }

            // 2. SECTION HEADER
            item(key = "section_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DANH SÁCH TINH CHỈNH",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(5 mục)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 3. CARD 1: PHÍM NGUỒN & GEMINI
            item(key = "section_power_gemini") {
                val isExpanded = uiState.expandedSection == SettingSection.POWER_GEMINI
                val summary = when (uiState.powerKeyAction) {
                    PowerKeyAction.GEMINI_ASSISTANT -> "Gemini"
                    PowerKeyAction.XIAO_AI -> "XiaoAI"
                    PowerKeyAction.POWER_MENU -> "Menu nguồn"
                }

                SettingAccordionCard(
                    title = "Phím Nguồn & Gemini",
                    icon = Icons.Filled.Bolt,
                    summaryChip = summary,
                    isExpanded = isExpanded,
                    onToggleExpand = { viewModel.toggleSection(SettingSection.POWER_GEMINI) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        if (!uiState.isGeminiInstalled && uiState.powerKeyAction == PowerKeyAction.GEMINI_ASSISTANT) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                            ) {
                                Text(
                                    text = "⚠️ Chưa cài đặt Google Gemini trên máy",
                                    fontSize = 11.sp,
                                    color = BatteryLow,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }

                        // Mode Selector Chips
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CompactFilterChip(
                                label = "⚡ Gemini",
                                selected = uiState.powerKeyAction == PowerKeyAction.GEMINI_ASSISTANT,
                                onClick = { viewModel.setPowerKeyAction(PowerKeyAction.GEMINI_ASSISTANT) },
                                modifier = Modifier.weight(1f)
                            )
                            CompactFilterChip(
                                label = "🇨🇳 XiaoAI",
                                selected = uiState.powerKeyAction == PowerKeyAction.XIAO_AI,
                                onClick = { viewModel.setPowerKeyAction(PowerKeyAction.XIAO_AI) },
                                modifier = Modifier.weight(1f)
                            )
                            CompactFilterChip(
                                label = "📴 Menu nguồn",
                                selected = uiState.powerKeyAction == PowerKeyAction.POWER_MENU,
                                onClick = { viewModel.setPowerKeyAction(PowerKeyAction.POWER_MENU) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Auto-Sentinel Switch
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.setAutoSentinel(!uiState.isAutoSentinelEnabled) }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "Duy trì Gemini (Auto-Sentinel)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = uiState.isAutoSentinelEnabled,
                                onCheckedChange = { viewModel.setAutoSentinel(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Compact Actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CompactTonalChipButton(
                                text = "🚀 Thử Gemini",
                                onClick = { viewModel.launchGeminiTest() },
                                modifier = Modifier.weight(1f)
                            )
                            CompactTonalChipButton(
                                text = "⚙️ AOSP",
                                onClick = { viewModel.openAssistSettings() },
                                modifier = Modifier.weight(1f)
                            )
                            CompactTonalChipButton(
                                text = "🇨🇳 XiaoAI gốc",
                                onClick = { viewModel.setPowerKeyAction(PowerKeyAction.XIAO_AI) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 4. CARD 2: DỊCH & TÌM KIẾM MÀN HÌNH
            item(key = "section_screen_translate") {
                val isExpanded = uiState.expandedSection == SettingSection.SCREEN_TRANSLATE
                val summary = if (uiState.isHomeLongPressSearchEnabled) {
                    if (uiState.isMiCTSInstalled) "MiCTS (CTS)" else "Lens"
                } else {
                    "Đã tắt"
                }

                SettingAccordionCard(
                    title = "Dịch & Tìm kiếm",
                    icon = Icons.Filled.ScreenSearchDesktop,
                    summaryChip = summary,
                    isExpanded = isExpanded,
                    onToggleExpand = { viewModel.toggleSection(SettingSection.SCREEN_TRANSLATE) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        // Switch Cử chỉ đáy gọi MiCTS (CTS) - Gọn, không subtext dài dòng
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.setHomeLongPressSearch(!uiState.isHomeLongPressSearchEnabled) }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = if (uiState.isMiCTSInstalled) "Cử chỉ đáy gọi MiCTS (CTS)" else "Cử chỉ đáy gọi Dịch / Tìm kiếm",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = uiState.isHomeLongPressSearchEnabled,
                                onCheckedChange = { viewModel.setHomeLongPressSearch(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Compact Actions (30dp): Thử MiCTS ngay, Thử Lens, AOSP
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CompactTonalChipButton(
                                text = "🔍 Thử MiCTS ngay",
                                onClick = { viewModel.launchMiCTS() },
                                modifier = Modifier.weight(1f)
                            )
                            CompactTonalChipButton(
                                text = "🌐 Thử Lens",
                                onClick = { viewModel.launchLensTranslateDirect() },
                                modifier = Modifier.weight(1f)
                            )
                            CompactTonalChipButton(
                                text = "⚙️ AOSP",
                                onClick = { viewModel.openAssistSettings() },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // 5. CARD 3: MÀN HÌNH & HIỂN THỊ
            item(key = "section_display") {
                val isExpanded = uiState.expandedSection == SettingSection.DISPLAY
                val timeoutShort = when (uiState.screenTimeoutMs) {
                    15000 -> "15s"
                    30000 -> "30s"
                    60000 -> "1p"
                    120000 -> "2p"
                    300000 -> "5p"
                    600000 -> "10p"
                    Int.MAX_VALUE -> "Không tắt"
                    else -> "${uiState.screenTimeoutMs / 1000}s"
                }
                val summary = "$timeoutShort • ${uiState.refreshRate}Hz"

                SettingAccordionCard(
                    title = "Màn hình & Hiển thị",
                    icon = Icons.Filled.DisplaySettings,
                    summaryChip = summary,
                    isExpanded = isExpanded,
                    onToggleExpand = { viewModel.toggleSection(SettingSection.DISPLAY) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        // 1. Timeout
                        Text("Khóa màn hình:", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                15000 to "15s",
                                30000 to "30s",
                                60000 to "1p",
                                120000 to "2p",
                                300000 to "5p",
                                600000 to "10p",
                                Int.MAX_VALUE to "Không tắt"
                            ).forEach { (ms, text) ->
                                CompactFilterChip(
                                    label = text,
                                    selected = uiState.screenTimeoutMs == ms,
                                    onClick = { viewModel.setScreenTimeout(ms) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 2. Refresh Rate
                        Text("Tần số quét:", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CompactFilterChip(
                                label = "60Hz",
                                selected = uiState.refreshRate <= 60,
                                onClick = { viewModel.setRefreshRate(60) }
                            )
                            CompactFilterChip(
                                label = "120Hz",
                                selected = uiState.refreshRate > 60,
                                onClick = { viewModel.setRefreshRate(120) }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 3. Animation Scale
                        Text("Tốc độ hoạt họa:", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            CompactFilterChip(
                                label = "0x (Tắt)",
                                selected = uiState.animationScale == 0.0f,
                                onClick = { viewModel.setAnimationScale(0.0f) }
                            )
                            CompactFilterChip(
                                label = "0.5x ⭐",
                                selected = uiState.animationScale == 0.5f,
                                onClick = { viewModel.setAnimationScale(0.5f) }
                            )
                            CompactFilterChip(
                                label = "1.0x",
                                selected = uiState.animationScale == 1.0f,
                                onClick = { viewModel.setAnimationScale(1.0f) }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 4. OLED Dark Mode (Gọn, không subtext)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.setOledDarkMode(!uiState.isOledDarkMode) }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "Giao diện tối OLED (Pure Black)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = uiState.isOledDarkMode,
                                onCheckedChange = { viewModel.setOledDarkMode(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }

            // 6. CARD 4: HIỆU NĂNG & HỆ THỐNG
            item(key = "section_performance") {
                val isExpanded = uiState.expandedSection == SettingSection.PERFORMANCE
                val summary = "Doze • RAM"

                SettingAccordionCard(
                    title = "Hiệu năng & Hệ thống",
                    icon = Icons.Filled.Speed,
                    summaryChip = summary,
                    isExpanded = isExpanded,
                    onToggleExpand = { viewModel.toggleSection(SettingSection.PERFORMANCE) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        // 1. Quản lý sạc pin (Đã chuyển về Tab Tự động hóa)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "Quản lý sạc pin & HyperCharge",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            CompactTonalChipButton(
                                text = "⚡ Tab Tự động",
                                onClick = { onBack() }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 2. Deep Doze (Không subtext)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "Kích hoạt Deep Doze",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            CompactTonalChipButton(
                                text = "🌙 Ngủ sâu ngay",
                                onClick = { viewModel.triggerDeepDoze() }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // 3. RAM Extension
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Memory,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "RAM ảo Xiaomi: ${uiState.ramExtensionStatus.ifEmpty { "Không rõ" }}",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 7. CARD 5: MẠNG & BẢO MẬT DNS
            item(key = "section_network_dns") {
                val isExpanded = uiState.expandedSection == SettingSection.NETWORK_DNS
                val summary = when (uiState.privateDnsMode) {
                    "hostname" -> if (uiState.privateDnsHost.contains("adguard")) "AdGuard"
                                  else if (uiState.privateDnsHost.contains("one.one")) "Cloudflare"
                                  else "Tùy chỉnh"
                    "opportunistic" -> "Tự động"
                    else -> "Tắt"
                }

                SettingAccordionCard(
                    title = "Mạng & DNS",
                    icon = Icons.Filled.Dns,
                    summaryChip = summary,
                    isExpanded = isExpanded,
                    onToggleExpand = { viewModel.toggleSection(SettingSection.NETWORK_DNS) }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        // 1. Mobile Data Always On (Không subtext)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.setMobileDataAlwaysOn(!uiState.isMobileDataAlwaysOn) }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "Giữ mạng di động khi có Wi-Fi",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = uiState.isMobileDataAlwaysOn,
                                onCheckedChange = { viewModel.setMobileDataAlwaysOn(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 2. Private DNS (Không subtext)
                        Text("Private DNS:", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CompactFilterChip(
                                label = "🛡️ AdGuard",
                                selected = uiState.privateDnsMode == "hostname" && uiState.privateDnsHost == "dns.adguard-dns.com",
                                onClick = { viewModel.setPrivateDns("hostname", "dns.adguard-dns.com") }
                            )
                            CompactFilterChip(
                                label = "⚡ Cloudflare",
                                selected = uiState.privateDnsMode == "hostname" && uiState.privateDnsHost == "one.one.one.one",
                                onClick = { viewModel.setPrivateDns("hostname", "one.one.one.one") }
                            )
                            CompactFilterChip(
                                label = "Tự động",
                                selected = uiState.privateDnsMode == "opportunistic",
                                onClick = { viewModel.setPrivateDns("opportunistic") }
                            )
                            CompactFilterChip(
                                label = "Tắt",
                                selected = uiState.privateDnsMode == "off",
                                onClick = { viewModel.setPrivateDns("off") }
                            )
                            CompactFilterChip(
                                label = "Tùy chỉnh...",
                                selected = uiState.privateDnsMode == "hostname" && uiState.privateDnsHost != "dns.adguard-dns.com" && uiState.privateDnsHost != "one.one.one.one",
                                onClick = {
                                    customDnsInput = uiState.privateDnsHost
                                    showCustomDnsDialog = true
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 3. Master Sync (Không subtext)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.setMasterSync(!uiState.isMasterSync) }
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "Đồng bộ tài khoản tự động (Master Sync)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = uiState.isMasterSync,
                                onCheckedChange = { viewModel.setMasterSync(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }

            // 8. COMPACT FOOTER
            item(key = "footer_tips") {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "⚡ Tinh chỉnh áp dụng tức thì qua Shizuku • Chạm thẻ để mở rộng",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Dialog nhập Private DNS tùy chỉnh
    if (showCustomDnsDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDnsDialog = false },
            title = { Text("Nhập hostname Private DNS", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Địa chỉ DoT hợp lệ (vd: dns.adguard-dns.com):", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customDnsInput,
                        onValueChange = { customDnsInput = it },
                        singleLine = true,
                        placeholder = { Text("vd: dns.adguard-dns.com") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.setPrivateDns("hostname", customDnsInput.trim())
                        showCustomDnsDialog = false
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Áp dụng", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCustomDnsDialog = false },
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Hủy", fontSize = 12.sp)
                }
            }
        )
    }

    // Dialog xác nhận Khôi phục
    if (showRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDialog = false },
            title = { Text("Khôi phục thiết lập gốc?") },
            text = { Text("Toàn bộ các giá trị tinh chỉnh sẽ được đưa về trạng thái snapshot lúc mở tính năng lần đầu.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.restoreDefaults()
                        showRestoreDialog = false
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Xác nhận", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestoreDialog = false },
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Hủy", fontSize = 12.sp)
                }
            }
        )
    }
}

/**
 * Thẻ điều khiển tổng siêu gọn (~52-54dp): Shizuku status, Auto-Sentinel status, Restore snapshot button.
 */
@Composable
private fun CompactExtendedMasterControlCard(
    isShizukuReady: Boolean,
    isAutoSentinelEnabled: Boolean,
    onRestoreClick: () -> Unit
) {
    val gradientColors = if (isShizukuReady) {
        listOf(
            Green40.copy(alpha = 0.22f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    } else {
        listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        border = BorderStroke(
            1.dp,
            if (isShizukuReady) Green80.copy(alpha = 0.35f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isShizukuReady) 1.5.dp else 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(gradientColors))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon bo góc nhỏ gọn 38dp
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (isShizukuReady) Green80.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    .border(
                        1.dp,
                        if (isShizukuReady) Green80.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = if (isShizukuReady) Green80 else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Thông tin trung tâm
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Điều khiển Xiaomi 14",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isShizukuReady) Green80.copy(alpha = 0.18f) else BatteryLow.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = if (isShizukuReady) "Sẵn sàng" else "Cần Shizuku",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                            color = if (isShizukuReady) Green80 else BatteryLow,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Auto-Sentinel: ",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isAutoSentinelEnabled) "Đang bảo vệ" else "Đã tắt",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isAutoSentinelEnabled) Green80 else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Restore snapshot button
            Surface(
                onClick = onRestoreClick,
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Khôi phục",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * Accordion Card chuẩn (~52-54dp collapsed) mở rộng mượt mà theo style AutomationCard
 */
@Composable
private fun SettingAccordionCard(
    title: String,
    icon: ImageVector,
    summaryChip: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    expandedContent: @Composable () -> Unit
) {
    val borderColor = if (isExpanded) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    } else {
        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
    }

    val containerColor = if (isExpanded) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded) 1.5.dp else 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header row siêu mỏng (~52-54dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon bo góc nhỏ gọn 38dp
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isExpanded) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = if (isExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Tiêu đề ngắn
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Capsule Badge tóm tắt giá trị hiện tại
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = summaryChip,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Expand arrow
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (isExpanded) "Thu gọn" else "Mở rộng",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Expanded child controls
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )
                    expandedContent()
                }
            }
        }
    }
}
