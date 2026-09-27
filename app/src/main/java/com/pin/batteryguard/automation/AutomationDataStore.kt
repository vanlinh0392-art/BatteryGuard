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
    val dayNightRingerParams: DayNightRingerParams = DayNightRingerParams(),
    val overnightChargingEnabled: Boolean = false,
    val overnightChargingParams: OvernightChargingParams = OvernightChargingParams(),
    val lowBatterySaverEnabled: Boolean = true,
    val lowBatterySaverParams: LowBatterySaverParams = LowBatterySaverParams(),
    val deepScreenOffEnabled: Boolean = false,
    val deepScreenOffParams: DeepScreenOffParams = DeepScreenOffParams(),
    val autoSortActiveToTop: Boolean = true
)

@Singleton
class AutomationDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_MASTER_ENABLED = booleanPreferencesKey("automation_master_enabled")
        private val KEY_AUTO_SORT_ACTIVE = booleanPreferencesKey("auto_sort_active_to_top")

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
        private val KEY_RINGER_APPLY_WEEKENDS = booleanPreferencesKey("ringer_apply_weekends")

        // 3. Overnight Charging
        private val KEY_OVERNIGHT_ENABLED = booleanPreferencesKey("overnight_charging_enabled")
        private val KEY_OVERNIGHT_LIMIT = intPreferencesKey("overnight_charging_limit")
        private val KEY_OVERNIGHT_SOUND = booleanPreferencesKey("overnight_alert_sound")
        private val KEY_OVERNIGHT_OVERHEAT = booleanPreferencesKey("overnight_prevent_overheat")
        private val KEY_OVERNIGHT_START_H = intPreferencesKey("overnight_start_hour")
        private val KEY_OVERNIGHT_START_M = intPreferencesKey("overnight_start_min")
        private val KEY_OVERNIGHT_END_H = intPreferencesKey("overnight_end_hour")
        private val KEY_OVERNIGHT_END_M = intPreferencesKey("overnight_end_min")

        // 4. Low Battery Saver
        private val KEY_LOW_BATTERY_ENABLED = booleanPreferencesKey("low_battery_enabled")
        private val KEY_LOW_BATTERY_THRESHOLD = intPreferencesKey("low_battery_threshold")
        private val KEY_LOW_BATTERY_DIM = booleanPreferencesKey("low_battery_dim_brightness")
        private val KEY_LOW_BATTERY_POWER_SAVER = booleanPreferencesKey("low_battery_system_power_saver")
        private val KEY_LOW_BATTERY_AOD = booleanPreferencesKey("low_battery_turn_off_aod")
        private val KEY_LOW_BATTERY_SYNC = booleanPreferencesKey("low_battery_restrict_sync")

        // 5. Deep Screen Off
        private val KEY_SCREEN_OFF_ENABLED = booleanPreferencesKey("screen_off_enabled")
        private val KEY_SCREEN_OFF_DELAY = intPreferencesKey("screen_off_delay")
        private val KEY_SCREEN_OFF_FORCE_STOP = booleanPreferencesKey("screen_off_force_stop")
        private val KEY_SCREEN_OFF_DEEP_DOZE = booleanPreferencesKey("screen_off_deep_doze")
        private val KEY_SCREEN_OFF_HOTSPOT = booleanPreferencesKey("screen_off_hotspot")
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
                nightMode = ringerMode,
                applyOnWeekends = prefs[KEY_RINGER_APPLY_WEEKENDS] ?: true
            ),
            overnightChargingEnabled = prefs[KEY_OVERNIGHT_ENABLED] ?: false,
            overnightChargingParams = OvernightChargingParams(
                maxChargeLimitPercent = prefs[KEY_OVERNIGHT_LIMIT] ?: 80,
                alertSoundOnTarget = prefs[KEY_OVERNIGHT_SOUND] ?: true,
                preventOverheat = prefs[KEY_OVERNIGHT_OVERHEAT] ?: true,
                startHour = prefs[KEY_OVERNIGHT_START_H] ?: 22,
                endHour = prefs[KEY_OVERNIGHT_END_H] ?: 6
            ),
            lowBatterySaverEnabled = prefs[KEY_LOW_BATTERY_ENABLED] ?: true,
            lowBatterySaverParams = LowBatterySaverParams(
                thresholdPercent = prefs[KEY_LOW_BATTERY_THRESHOLD] ?: 20,
                dimDisplayBrightness = prefs[KEY_LOW_BATTERY_DIM] ?: true,
                enableSystemPowerSaver = prefs[KEY_LOW_BATTERY_POWER_SAVER] ?: true,
                turnOffAodAndRadios = prefs[KEY_LOW_BATTERY_AOD] ?: true,
                restrictBackgroundSync = prefs[KEY_LOW_BATTERY_SYNC] ?: true
            ),
            deepScreenOffEnabled = prefs[KEY_SCREEN_OFF_ENABLED] ?: false,
            deepScreenOffParams = DeepScreenOffParams(
                delayMinutes = prefs[KEY_SCREEN_OFF_DELAY] ?: 3,
                forceStopBackgroundDrainers = prefs[KEY_SCREEN_OFF_FORCE_STOP] ?: true,
                enableDeepDoze = prefs[KEY_SCREEN_OFF_DEEP_DOZE] ?: true,
                turnOffHotspotIfIdle = prefs[KEY_SCREEN_OFF_HOTSPOT] ?: true
            ),
            autoSortActiveToTop = prefs[KEY_AUTO_SORT_ACTIVE] ?: true
        )
    }

    suspend fun setMasterEnabled(enabled: Boolean) {
        context.automationDataStore.edit { it[KEY_MASTER_ENABLED] = enabled }
    }

    suspend fun setAutoSortActiveToTop(enabled: Boolean) {
        context.automationDataStore.edit { it[KEY_AUTO_SORT_ACTIVE] = enabled }
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
            prefs[KEY_RINGER_APPLY_WEEKENDS] = params.applyOnWeekends
        }
    }

    suspend fun updateOvernightChargingParams(params: OvernightChargingParams) {
        context.automationDataStore.edit { prefs ->
            prefs[KEY_OVERNIGHT_LIMIT] = params.maxChargeLimitPercent
            prefs[KEY_OVERNIGHT_SOUND] = params.alertSoundOnTarget
            prefs[KEY_OVERNIGHT_OVERHEAT] = params.preventOverheat
            prefs[KEY_OVERNIGHT_START_H] = params.startHour
            prefs[KEY_OVERNIGHT_END_H] = params.endHour
        }
    }

    suspend fun updateLowBatterySaverParams(params: LowBatterySaverParams) {
        context.automationDataStore.edit { prefs ->
            prefs[KEY_LOW_BATTERY_THRESHOLD] = params.thresholdPercent
            prefs[KEY_LOW_BATTERY_DIM] = params.dimDisplayBrightness
            prefs[KEY_LOW_BATTERY_POWER_SAVER] = params.enableSystemPowerSaver
            prefs[KEY_LOW_BATTERY_AOD] = params.turnOffAodAndRadios
            prefs[KEY_LOW_BATTERY_SYNC] = params.restrictBackgroundSync
        }
    }

    suspend fun updateDeepScreenOffParams(params: DeepScreenOffParams) {
        context.automationDataStore.edit { prefs ->
            prefs[KEY_SCREEN_OFF_DELAY] = params.delayMinutes
            prefs[KEY_SCREEN_OFF_FORCE_STOP] = params.forceStopBackgroundDrainers
            prefs[KEY_SCREEN_OFF_DEEP_DOZE] = params.enableDeepDoze
            prefs[KEY_SCREEN_OFF_HOTSPOT] = params.turnOffHotspotIfIdle
        }
    }
}
