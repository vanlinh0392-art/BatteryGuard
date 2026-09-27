package com.pin.batteryguard.automation

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pin.batteryguard.ui.screen.automation.DayNightRingerParams
import com.pin.batteryguard.ui.screen.automation.DeepScreenOffParams
import com.pin.batteryguard.ui.screen.automation.LowBatterySaverParams
import com.pin.batteryguard.ui.screen.automation.OvernightChargingParams
import com.pin.batteryguard.ui.screen.automation.RingerTargetMode
import com.pin.batteryguard.ui.screen.automation.TargetSimSelection
import com.pin.batteryguard.ui.screen.automation.WifiAutoDataParams
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.automationDataStore: DataStore<Preferences> by preferencesDataStore(name = "automation_settings")

data class AutomationMasterConfig(
    val isMasterEnabled: Boolean = true,
    val wifiAutoDataEnabled: Boolean = true,
    val wifiAutoDataParams: WifiAutoDataParams = WifiAutoDataParams(),
    val dayNightRingerEnabled: Boolean = true,
    val dayNightRingerParams: DayNightRingerParams = DayNightRingerParams(
        startHour = 21,
        startMinute = 30,
        endHour = 6,
        endMinute = 0,
        nightMode = RingerTargetMode.VIBRATE
    ),
    val overnightChargingEnabled: Boolean = false,
    val overnightChargingParams: OvernightChargingParams = OvernightChargingParams(),
    val lowBatterySaverEnabled: Boolean = true,
    val lowBatterySaverParams: LowBatterySaverParams = LowBatterySaverParams(),
    val deepScreenOffEnabled: Boolean = false,
    val deepScreenOffParams: DeepScreenOffParams = DeepScreenOffParams()
)

@Singleton
class AutomationDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_MASTER_ENABLED = booleanPreferencesKey("automation_master_enabled")

        // 1. Wifi Auto Data
        private val KEY_WIFI_DATA_ENABLED = booleanPreferencesKey("wifi_data_enabled")
        private val KEY_WIFI_DATA_DELAY = intPreferencesKey("wifi_data_delay")
        private val KEY_WIFI_DATA_RESTORE = booleanPreferencesKey("wifi_data_restore")
        private val KEY_WIFI_DATA_SIM = stringPreferencesKey("wifi_data_sim")

        // 2. Day/Night Ringer
        private val KEY_RINGER_ENABLED = booleanPreferencesKey("ringer_enabled")
        private val KEY_RINGER_START_H = intPreferencesKey("ringer_start_hour")
        private val KEY_RINGER_START_M = intPreferencesKey("ringer_start_min")
        private val KEY_RINGER_END_H = intPreferencesKey("ringer_end_hour")
        private val KEY_RINGER_END_M = intPreferencesKey("ringer_end_min")
        private val KEY_RINGER_MODE = stringPreferencesKey("ringer_mode")

        // 3. Overnight Charging
        private val KEY_OVERNIGHT_ENABLED = booleanPreferencesKey("overnight_charging_enabled")
        private val KEY_OVERNIGHT_LIMIT = intPreferencesKey("overnight_charging_limit")

        // 4. Low Battery Saver
        private val KEY_LOW_BATTERY_ENABLED = booleanPreferencesKey("low_battery_enabled")
        private val KEY_LOW_BATTERY_THRESHOLD = intPreferencesKey("low_battery_threshold")

        // 5. Deep Screen Off
        private val KEY_SCREEN_OFF_ENABLED = booleanPreferencesKey("screen_off_enabled")
        private val KEY_SCREEN_OFF_DELAY = intPreferencesKey("screen_off_delay")
    }

    val configFlow: Flow<AutomationMasterConfig> = context.automationDataStore.data.map { prefs ->
        val simName = prefs[KEY_WIFI_DATA_SIM] ?: TargetSimSelection.AUTO.name
        val simSelection = try {
            TargetSimSelection.valueOf(simName)
        } catch (_: Exception) {
            TargetSimSelection.AUTO
        }

        val ringerModeName = prefs[KEY_RINGER_MODE] ?: RingerTargetMode.VIBRATE.name
        val ringerMode = try {
            RingerTargetMode.valueOf(ringerModeName)
        } catch (_: Exception) {
            RingerTargetMode.VIBRATE
        }

        AutomationMasterConfig(
            isMasterEnabled = prefs[KEY_MASTER_ENABLED] ?: true,
            wifiAutoDataEnabled = prefs[KEY_WIFI_DATA_ENABLED] ?: true,
            wifiAutoDataParams = WifiAutoDataParams(
                delaySeconds = prefs[KEY_WIFI_DATA_DELAY] ?: 15,
                autoRestoreDataOnDisconnect = prefs[KEY_WIFI_DATA_RESTORE] ?: true,
                targetSim = simSelection
            ),
            dayNightRingerEnabled = prefs[KEY_RINGER_ENABLED] ?: true,
            dayNightRingerParams = DayNightRingerParams(
                startHour = prefs[KEY_RINGER_START_H] ?: 21,
                startMinute = prefs[KEY_RINGER_START_M] ?: 30,
                endHour = prefs[KEY_RINGER_END_H] ?: 6,
                endMinute = prefs[KEY_RINGER_END_M] ?: 0,
                nightMode = ringerMode
            ),
            overnightChargingEnabled = prefs[KEY_OVERNIGHT_ENABLED] ?: false,
            overnightChargingParams = OvernightChargingParams(
                maxChargeLimitPercent = prefs[KEY_OVERNIGHT_LIMIT] ?: 80
            ),
            lowBatterySaverEnabled = prefs[KEY_LOW_BATTERY_ENABLED] ?: true,
            lowBatterySaverParams = LowBatterySaverParams(
                thresholdPercent = prefs[KEY_LOW_BATTERY_THRESHOLD] ?: 20
            ),
            deepScreenOffEnabled = prefs[KEY_SCREEN_OFF_ENABLED] ?: false,
            deepScreenOffParams = DeepScreenOffParams(
                delayMinutes = prefs[KEY_SCREEN_OFF_DELAY] ?: 3
            )
        )
    }

    suspend fun setMasterEnabled(enabled: Boolean) {
        context.automationDataStore.edit { it[KEY_MASTER_ENABLED] = enabled }
    }

    suspend fun setRuleEnabled(ruleId: com.pin.batteryguard.ui.screen.automation.AutomationRuleId, enabled: Boolean) {
        context.automationDataStore.edit { prefs ->
            when (ruleId) {
                com.pin.batteryguard.ui.screen.automation.AutomationRuleId.WIFI_AUTO_DATA ->
                    prefs[KEY_WIFI_DATA_ENABLED] = enabled
                com.pin.batteryguard.ui.screen.automation.AutomationRuleId.DAY_NIGHT_RINGER ->
                    prefs[KEY_RINGER_ENABLED] = enabled
                com.pin.batteryguard.ui.screen.automation.AutomationRuleId.OVERNIGHT_CHARGING ->
                    prefs[KEY_OVERNIGHT_ENABLED] = enabled
                com.pin.batteryguard.ui.screen.automation.AutomationRuleId.LOW_BATTERY_SAVER ->
                    prefs[KEY_LOW_BATTERY_ENABLED] = enabled
                com.pin.batteryguard.ui.screen.automation.AutomationRuleId.DEEP_SCREEN_OFF ->
                    prefs[KEY_SCREEN_OFF_ENABLED] = enabled
            }
        }
    }

    suspend fun updateWifiAutoDataParams(params: WifiAutoDataParams) {
        context.automationDataStore.edit { prefs ->
            prefs[KEY_WIFI_DATA_DELAY] = params.delaySeconds
            prefs[KEY_WIFI_DATA_RESTORE] = params.autoRestoreDataOnDisconnect
            prefs[KEY_WIFI_DATA_SIM] = params.targetSim.name
        }
    }

    suspend fun updateDayNightRingerParams(params: DayNightRingerParams) {
        context.automationDataStore.edit { prefs ->
            prefs[KEY_RINGER_START_H] = params.startHour
            prefs[KEY_RINGER_START_M] = params.startMinute
            prefs[KEY_RINGER_END_H] = params.endHour
            prefs[KEY_RINGER_END_M] = params.endMinute
            prefs[KEY_RINGER_MODE] = params.nightMode.name
        }
    }

    suspend fun updateOvernightChargingParams(params: OvernightChargingParams) {
        context.automationDataStore.edit { prefs ->
            prefs[KEY_OVERNIGHT_LIMIT] = params.maxChargeLimitPercent
        }
    }

    suspend fun updateLowBatterySaverParams(params: LowBatterySaverParams) {
        context.automationDataStore.edit { prefs ->
            prefs[KEY_LOW_BATTERY_THRESHOLD] = params.thresholdPercent
        }
    }

    suspend fun updateDeepScreenOffParams(params: DeepScreenOffParams) {
        context.automationDataStore.edit { prefs ->
            prefs[KEY_SCREEN_OFF_DELAY] = params.delayMinutes
        }
    }
}
