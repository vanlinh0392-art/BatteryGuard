package com.pin.batteryguard.domain.model

data class MonitoringConfig(
    val warningThresholdPercentPerHour: Double = 0.3,
    val drainThresholdPercent: Float = 0.5f,
    val criticalThresholdPercentPerHour: Double = 1.5,
    val minimumDeltaMah: Double = 1.0,
    val minimumSampleMinutes: Int = 10,
    val requiredConsecutiveSamples: Int = 2,
    val monitoringPeriodMinutes: Int = 15,
    val autoForceStop: Boolean = true,
    val notifyBeforeStop: Boolean = true,
    val monitorOnlyScreenOff: Boolean = true,
    val enableFreezeMode: Boolean = false,
    val freezeThresholdPercent: Float = 1.5f,
    /** Hide system packages from the Applications tab by default. */
    val excludeSystemApps: Boolean = true,
    val isMonitoringEnabled: Boolean = true,
    val enableAutoStartShizuku: Boolean = false,
    val shizukuRetryMinutes: Int = 15
) {
    /**
     * Keep values safe even when they come from an older DataStore schema or
     * are changed by a non-UI caller.  Critical must remain above force-stop
     * so a critical sample is never silently downgraded to the confirmation
     * path.
     */
    fun normalized(): MonitoringConfig {
        val forceStop = drainThresholdPercent.coerceIn(MIN_FORCE_STOP_PERCENT_PER_HOUR, MAX_FORCE_STOP_PERCENT_PER_HOUR)
        return copy(
            warningThresholdPercentPerHour = forceStop.toDouble(),
            drainThresholdPercent = forceStop,
            criticalThresholdPercentPerHour = forceStop.toDouble(),
            minimumDeltaMah = minimumDeltaMah.coerceAtLeast(0.0),
            minimumSampleMinutes = minimumSampleMinutes.coerceAtLeast(10),
            requiredConsecutiveSamples = 1,
            monitoringPeriodMinutes = monitoringPeriodMinutes.coerceIn(10, 30),
            freezeThresholdPercent = freezeThresholdPercent.coerceIn(MIN_FORCE_STOP_PERCENT_PER_HOUR, MAX_FORCE_STOP_PERCENT_PER_HOUR),
            shizukuRetryMinutes = shizukuRetryMinutes.coerceIn(10, 60)
        )
    }

    companion object {
        const val MIN_FORCE_STOP_PERCENT_PER_HOUR = 0.3f
        const val MAX_FORCE_STOP_PERCENT_PER_HOUR = 5.0f
        const val MIN_CRITICAL_PERCENT_PER_HOUR = 0.5
        const val MAX_CRITICAL_PERCENT_PER_HOUR = 10.0
        const val THRESHOLD_GAP_PERCENT_PER_HOUR = 0.1
    }
}
