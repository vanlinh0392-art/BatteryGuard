package com.pin.batteryguard.domain.battery

import com.pin.batteryguard.domain.model.MonitoringConfig

class DrainPolicy {
    /**
     * Option B: Single-threshold policy. If rate >= drainThresholdPercent → immediate FORCE_STOP.
     * No more "2 consecutive samples" requirement — simpler, more predictable, and actually works
     * across screen on/off cycles where breach counters used to be reset.
     */
    fun evaluate(sample: UidDrainSample, context: DetectionContext, config: MonitoringConfig): DetectionDecision {
        if (sample.durationMillis < config.minimumSampleMinutes * 60_000L) return none("sample_too_short", DecisionGuard.SAMPLE_TOO_SHORT)
        if (sample.deltaMah < config.minimumDeltaMah) return none("delta_too_small", DecisionGuard.DELTA_TOO_SMALL)
        if (sample.ratePercentPerHour < config.drainThresholdPercent) return none("below_threshold", DecisionGuard.BELOW_THRESHOLD)
        guardedContext(context)?.let { guard ->
            return DetectionDecision(DetectionAction.WARN, "guarded_${guard.name.lowercase()}", guard)
        }
        if (!config.autoForceStop) return DetectionDecision(DetectionAction.WARN, "auto_stop_disabled", DecisionGuard.AUTO_STOP_DISABLED)
        if (context.isInActionCooldown) return DetectionDecision(DetectionAction.WARN, "action_cooldown", DecisionGuard.ACTION_COOLDOWN)
        if (config.enableFreezeMode && context.verifiedStopsInWindow >= 2) {
            return DetectionDecision(DetectionAction.FREEZE, "repeated_verified_offender")
        }
        return DetectionDecision(DetectionAction.FORCE_STOP, "exceeded_threshold")
    }

    private fun guardedContext(context: DetectionContext): DecisionGuard? = when {
        context.isException -> DecisionGuard.EXCEPTION
        context.isSystemApp -> DecisionGuard.SYSTEM_APP
        context.isSharedUid -> DecisionGuard.SHARED_UID
        context.isProtected -> DecisionGuard.PROTECTED_APP
        context.isActiveUse && !context.activeUseOverride -> DecisionGuard.ACTIVE_USE
        else -> null
    }

    private fun none(reason: String, guard: DecisionGuard) = DetectionDecision(DetectionAction.NONE, reason, guard)
}
