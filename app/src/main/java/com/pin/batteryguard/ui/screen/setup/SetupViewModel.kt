package com.pin.batteryguard.ui.screen.setup

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pin.batteryguard.data.preferences.SettingsDataStore
import com.pin.batteryguard.data.repository.AppRepository
import com.pin.batteryguard.shizuku.ShizukuManager
import com.pin.batteryguard.shizuku.ShizukuStatus
import com.pin.batteryguard.util.XiaomiHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupUiState(
    val currentStep: Int = 0,
    val shizukuStatus: ShizukuStatus = ShizukuStatus.NOT_INSTALLED,
    val usageAccessGranted: Boolean = false,
    val notificationGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false,
    val isXiaomi: Boolean = false
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager,
    private val settingsDataStore: SettingsDataStore,
    private val appRepository: AppRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState(isXiaomi = XiaomiHelper.isXiaomi()))
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        // Collect Shizuku status
        viewModelScope.launch {
            shizukuManager.status.collectLatest { status ->
                _uiState.update { it.copy(shizukuStatus = status) }
                if (status == ShizukuStatus.READY) {
                    shizukuManager.grantSystemPermissions()
                }
            }
        }
        checkPermissions()
    }

    fun nextStep() {
        _uiState.update { it.copy(currentStep = (it.currentStep + 1).coerceAtMost(3)) }
    }

    fun prevStep() {
        _uiState.update { it.copy(currentStep = (it.currentStep - 1).coerceAtLeast(0)) }
    }

    fun checkPermissions() {
        shizukuManager.updateStatus()
        val usageGranted = isUsageAccessGranted()
        val notifGranted = isNotificationPermissionGranted()
        val batteryIgnored = XiaomiHelper.isIgnoringBatteryOptimizations(context)

        _uiState.update {
            it.copy(
                usageAccessGranted = usageGranted,
                notificationGranted = notifGranted,
                batteryOptimizationIgnored = batteryIgnored
            )
        }
    }

    fun requestShizukuPermission() {
        shizukuManager.requestPermission(100)
    }

    private fun isUsageAccessGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == 
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    fun completeSetup(onDone: () -> Unit) {
        viewModelScope.launch {
            settingsDataStore.saveSetupCompleted(true)
            appRepository.insertDefaultExceptions()
            onDone()
        }
    }
}
