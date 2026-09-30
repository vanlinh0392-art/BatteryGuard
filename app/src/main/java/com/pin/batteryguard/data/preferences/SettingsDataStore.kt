package com.pin.batteryguard.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pin.batteryguard.domain.model.MonitoringConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {
    companion object {
        private const val CURRENT_CONFIG_VERSION = 2
        private val KEY_CONFIG_VERSION = intPreferencesKey("config_schema_version")
        private val KEY_WARNING_THRESHOLD = doublePreferencesKey("warning_threshold_percent_per_hour")
        private val KEY_DRAIN_THRESHOLD = floatPreferencesKey("drain_threshold_percent")
        private val KEY_CRITICAL_THRESHOLD = doublePreferencesKey("critical_threshold_percent_per_hour")
        private val KEY_MIN_DELTA = doublePreferencesKey("minimum_delta_mah")
        private val KEY_MIN_SAMPLE_MINUTES = intPreferencesKey("minimum_sample_minutes")
        private val KEY_REQUIRED_SAMPLES = intPreferencesKey("required_consecutive_samples")
        private val KEY_PERIOD_MINUTES = intPreferencesKey("monitoring_period_minutes")
        private val KEY_AUTO_FORCE_STOP = booleanPreferencesKey("auto_force_stop")
        private val KEY_NOTIFY_BEFORE_STOP = booleanPreferencesKey("notify_before_stop")
        private val KEY_MONITOR_SCREEN_OFF = booleanPreferencesKey("monitor_only_screen_off")
        private val KEY_FREEZE_MODE = booleanPreferencesKey("enable_freeze_mode")
        private val KEY_FREEZE_THRESHOLD = floatPreferencesKey("freeze_threshold_percent")
        private val KEY_EXCLUDE_SYSTEM_APPS = booleanPreferencesKey("exclude_system_apps")
        private val KEY_MONITORING_ENABLED = booleanPreferencesKey("is_monitoring_enabled")
        private val KEY_SETUP_COMPLETED = booleanPreferencesKey("is_setup_completed")
        private val KEY_SHIZUKU_GRANTED = booleanPreferencesKey("shizuku_permission_granted")
        private val KEY_AUTO_START_SHIZUKU = booleanPreferencesKey("auto_start_shizuku")
        private val KEY_SHIZUKU_RETRY_MINUTES = intPreferencesKey("shizuku_retry_minutes")

        // App Shield Keys
        private val KEY_SHIELD_ENABLED = booleanPreferencesKey("shield_enabled")
        private val KEY_SHIELD_AUTO_DETECT_BANKS = booleanPreferencesKey("shield_auto_detect_banks")
        private val KEY_SHIELD_REVERT_MINUTES = intPreferencesKey("shield_revert_minutes")
        private val KEY_SHIELD_REVERT_ON_SCREEN_OFF = booleanPreferencesKey("shield_revert_on_screen_off")
        private val KEY_SHIELD_HIDE_DEV_OPTIONS = booleanPreferencesKey("shield_hide_dev_options")
        private val KEY_SHIELD_HIDE_ADB = booleanPreferencesKey("shield_hide_adb")
        private val KEY_SHIELD_HIDE_WIRELESS_ADB = booleanPreferencesKey("shield_hide_wireless_adb")
        private val KEY_SHIELD_HIDE_ACCESSIBILITY = booleanPreferencesKey("shield_hide_accessibility")
        private val KEY_SHIELD_HIDE_OVERLAY = booleanPreferencesKey("shield_hide_overlay")
        private val KEY_SHIELD_RELAUNCH_APP = booleanPreferencesKey("shield_relaunch_app")
        private val KEY_SHIELD_AUTO_RESTART_SHIZUKU = booleanPreferencesKey("shield_auto_restart_shizuku")
    }

    val configFlow: Flow<MonitoringConfig> = context.dataStore.data.map { preferences ->
        MonitoringConfig(
            warningThresholdPercentPerHour = preferences[KEY_WARNING_THRESHOLD] ?: 0.3,
            drainThresholdPercent = preferences[KEY_DRAIN_THRESHOLD] ?: 0.5f,
            criticalThresholdPercentPerHour = preferences[KEY_CRITICAL_THRESHOLD] ?: 1.5,
            minimumDeltaMah = preferences[KEY_MIN_DELTA] ?: 1.0,
            minimumSampleMinutes = (preferences[KEY_MIN_SAMPLE_MINUTES] ?: 10).coerceAtLeast(10),
            requiredConsecutiveSamples = (preferences[KEY_REQUIRED_SAMPLES] ?: 2).coerceAtLeast(2),
            monitoringPeriodMinutes = (preferences[KEY_PERIOD_MINUTES] ?: 15).coerceIn(10, 30),
            autoForceStop = preferences[KEY_AUTO_FORCE_STOP] ?: true,
            notifyBeforeStop = preferences[KEY_NOTIFY_BEFORE_STOP] ?: true,
            monitorOnlyScreenOff = preferences[KEY_MONITOR_SCREEN_OFF] ?: true,
            enableFreezeMode = preferences[KEY_FREEZE_MODE] ?: false,
            freezeThresholdPercent = preferences[KEY_FREEZE_THRESHOLD] ?: 1.5f,
            excludeSystemApps = preferences[KEY_EXCLUDE_SYSTEM_APPS] ?: true,
            isMonitoringEnabled = preferences[KEY_MONITORING_ENABLED] ?: true,
            enableAutoStartShizuku = preferences[KEY_AUTO_START_SHIZUKU] ?: true,
            shizukuRetryMinutes = (preferences[KEY_SHIZUKU_RETRY_MINUTES] ?: 15).coerceIn(10, 60)
        ).normalized()
    }.distinctUntilChanged()

    val isSetupCompletedFlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_SETUP_COMPLETED] ?: false }.distinctUntilChanged()

    suspend fun migrateIfNeeded() {
        context.dataStore.edit { preferences ->
            if ((preferences[KEY_CONFIG_VERSION] ?: 0) >= CURRENT_CONFIG_VERSION) return@edit
            val oldPeriod = preferences[KEY_PERIOD_MINUTES]
            preferences[KEY_CONFIG_VERSION] = CURRENT_CONFIG_VERSION
            preferences[KEY_WARNING_THRESHOLD] = 0.3
            preferences[KEY_DRAIN_THRESHOLD] = 0.5f
            preferences[KEY_CRITICAL_THRESHOLD] = 1.5
            preferences[KEY_MIN_DELTA] = 1.0
            preferences[KEY_MIN_SAMPLE_MINUTES] = 10
            preferences[KEY_REQUIRED_SAMPLES] = 2
            preferences[KEY_PERIOD_MINUTES] = (oldPeriod ?: 15).coerceIn(10, 30)
            preferences[KEY_FREEZE_THRESHOLD] = 1.5f
        }
    }

    suspend fun saveSetupCompleted(completed: Boolean) {
        context.dataStore.edit { it[KEY_SETUP_COMPLETED] = completed }
    }

    suspend fun updateConfig(config: MonitoringConfig) {
        val safeConfig = config.normalized()
        context.dataStore.edit { preferences ->
            preferences[KEY_CONFIG_VERSION] = CURRENT_CONFIG_VERSION
            preferences[KEY_WARNING_THRESHOLD] = safeConfig.warningThresholdPercentPerHour
            preferences[KEY_DRAIN_THRESHOLD] = safeConfig.drainThresholdPercent
            preferences[KEY_CRITICAL_THRESHOLD] = safeConfig.criticalThresholdPercentPerHour
            preferences[KEY_MIN_DELTA] = safeConfig.minimumDeltaMah
            preferences[KEY_MIN_SAMPLE_MINUTES] = safeConfig.minimumSampleMinutes
            preferences[KEY_REQUIRED_SAMPLES] = safeConfig.requiredConsecutiveSamples
            preferences[KEY_PERIOD_MINUTES] = safeConfig.monitoringPeriodMinutes
            preferences[KEY_AUTO_FORCE_STOP] = safeConfig.autoForceStop
            preferences[KEY_NOTIFY_BEFORE_STOP] = safeConfig.notifyBeforeStop
            preferences[KEY_MONITOR_SCREEN_OFF] = safeConfig.monitorOnlyScreenOff
            preferences[KEY_FREEZE_MODE] = safeConfig.enableFreezeMode
            preferences[KEY_FREEZE_THRESHOLD] = safeConfig.freezeThresholdPercent
            preferences[KEY_EXCLUDE_SYSTEM_APPS] = safeConfig.excludeSystemApps
            preferences[KEY_MONITORING_ENABLED] = safeConfig.isMonitoringEnabled
            preferences[KEY_AUTO_START_SHIZUKU] = safeConfig.enableAutoStartShizuku
            preferences[KEY_SHIZUKU_RETRY_MINUTES] = safeConfig.shizukuRetryMinutes
        }
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MONITORING_ENABLED] = enabled }
    }

    val shieldConfigFlow: Flow<com.pin.batteryguard.domain.model.AppShieldConfig> = context.dataStore.data.map { preferences ->
        com.pin.batteryguard.domain.model.AppShieldConfig(
            isEnabled = preferences[KEY_SHIELD_ENABLED] ?: true,
            autoDetectBanks = preferences[KEY_SHIELD_AUTO_DETECT_BANKS] ?: true,
            autoRevertMinutes = (preferences[KEY_SHIELD_REVERT_MINUTES] ?: 10).coerceIn(1, 120),
            revertOnScreenOff = preferences[KEY_SHIELD_REVERT_ON_SCREEN_OFF] ?: true,
            hideDevOptions = preferences[KEY_SHIELD_HIDE_DEV_OPTIONS] ?: true,
            hideAdb = preferences[KEY_SHIELD_HIDE_ADB] ?: true,
            hideWirelessAdb = preferences[KEY_SHIELD_HIDE_WIRELESS_ADB] ?: true,
            hideAccessibility = preferences[KEY_SHIELD_HIDE_ACCESSIBILITY] ?: false,
            hideOverlay = preferences[KEY_SHIELD_HIDE_OVERLAY] ?: false,
            relaunchApp = preferences[KEY_SHIELD_RELAUNCH_APP] ?: true,
            autoRestartShizuku = preferences[KEY_SHIELD_AUTO_RESTART_SHIZUKU] ?: true
        )
    }.distinctUntilChanged()

    suspend fun updateShieldConfig(config: com.pin.batteryguard.domain.model.AppShieldConfig) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SHIELD_ENABLED] = config.isEnabled
            preferences[KEY_SHIELD_AUTO_DETECT_BANKS] = config.autoDetectBanks
            preferences[KEY_SHIELD_REVERT_MINUTES] = config.autoRevertMinutes.coerceIn(1, 120)
            preferences[KEY_SHIELD_REVERT_ON_SCREEN_OFF] = config.revertOnScreenOff
            preferences[KEY_SHIELD_HIDE_DEV_OPTIONS] = config.hideDevOptions
            preferences[KEY_SHIELD_HIDE_ADB] = config.hideAdb
            preferences[KEY_SHIELD_HIDE_WIRELESS_ADB] = config.hideWirelessAdb
            preferences[KEY_SHIELD_HIDE_ACCESSIBILITY] = config.hideAccessibility
            preferences[KEY_SHIELD_HIDE_OVERLAY] = config.hideOverlay
            preferences[KEY_SHIELD_RELAUNCH_APP] = config.relaunchApp
            preferences[KEY_SHIELD_AUTO_RESTART_SHIZUKU] = config.autoRestartShizuku
        }
    }

    suspend fun setShieldEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SHIELD_ENABLED] = enabled }
    }
}
