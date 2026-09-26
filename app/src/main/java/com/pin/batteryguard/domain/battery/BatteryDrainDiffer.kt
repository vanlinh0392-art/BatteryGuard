package com.pin.batteryguard.domain.battery

import kotlin.math.max

class BatteryDrainDiffer {
    fun diff(previous: BatteryStatsSnapshot, current: BatteryStatsSnapshot): BatteryDrainDiff {
        if (previous.consumers.isEmpty() || current.consumers.isEmpty()) return invalid(InvalidDiffReason.EMPTY_SNAPSHOT)
        if (previous.statsStartFingerprint.isNotBlank() && current.statsStartFingerprint.isNotBlank() &&
            previous.statsStartFingerprint != current.statsStartFingerprint) return invalid(InvalidDiffReason.STATS_RESET)
        val duration = current.capturedAtElapsedRealtime - previous.capturedAtElapsedRealtime
        if (duration <= 0L) return invalid(InvalidDiffReason.INVALID_DURATION)
        val capacity = current.capacityMah.takeIf { it > 0.0 } ?: previous.capacityMah
        if (capacity <= 0.0) return invalid(InvalidDiffReason.INVALID_CAPACITY)
        val common = current.consumers.keys.intersect(previous.consumers.keys)
        if (common.any { uid -> current.consumers.getValue(uid).totalPowerMah < previous.consumers.getValue(uid).totalPowerMah - RESET_EPSILON_MAH }) {
            return invalid(InvalidDiffReason.STATS_RESET)
        }
        val durationHours = duration.toDouble() / HOUR_MILLIS
        val samples = common.mapNotNull { uid ->
            val old = previous.consumers.getValue(uid)
            val now = current.consumers.getValue(uid)
            val delta = max(0.0, now.totalPowerMah - old.totalPowerMah)
            if (delta <= 0.0) return@mapNotNull null
            UidDrainSample(uid, now.userId, delta, delta / capacity * 100.0 / durationHours, duration,
                DrainEvidence(
                    cpuMah = positiveDelta(now.cpuPowerMah, old.cpuPowerMah),
                    wakeLockMah = positiveDelta(now.wakeLockPowerMah, old.wakeLockPowerMah),
                    audioMah = positiveDelta(now.audioPowerMah, old.audioPowerMah),
                    gnssMah = positiveDelta(now.gnssPowerMah, old.gnssPowerMah),
                    sensorMah = positiveDelta(now.sensorPowerMah, old.sensorPowerMah),
                    foregroundTimeMs = max(0L, now.foregroundTimeMs - old.foregroundTimeMs),
                    backgroundTimeMs = max(0L, now.backgroundTimeMs - old.backgroundTimeMs)
                ))
        }.sortedByDescending { it.ratePercentPerHour }
        return BatteryDrainDiff(true, durationMillis = duration, samples = samples)
    }

    private fun positiveDelta(current: Double, previous: Double) = max(0.0, current - previous)
    private fun invalid(reason: InvalidDiffReason) = BatteryDrainDiff(false, invalidReason = reason)

    companion object {
        private const val HOUR_MILLIS = 60.0 * 60.0 * 1000.0
        private const val RESET_EPSILON_MAH = 0.05
    }
}
