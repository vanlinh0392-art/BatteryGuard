package com.pin.batteryguard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.pin.batteryguard.data.repository.AppRepository
import com.pin.batteryguard.ui.navigation.BatteryGuardNavHost
import com.pin.batteryguard.ui.theme.BatteryGuardTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var appRepository: AppRepository

    @Inject
    lateinit var settingsDataStore: com.pin.batteryguard.data.preferences.SettingsDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val config = settingsDataStore.configFlow.first()
                if (config.isMonitoringEnabled) {
                    val serviceIntent = android.content.Intent(this@MainActivity, com.pin.batteryguard.service.BatteryMonitorService::class.java)
                    startForegroundService(serviceIntent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Tự động kiểm tra và khởi tạo danh sách ngoại lệ mặc định nếu danh sách trống (do Room Migration)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val currentExceptions = appRepository.getExceptionPackages()
                if (currentExceptions.isEmpty()) {
                    appRepository.insertDefaultExceptions()
                } else {
                    appRepository.cleanUpNonInstalledExceptions()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        setContent {
            BatteryGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BatteryGuardNavHost()
                }
            }
        }
    }
}
