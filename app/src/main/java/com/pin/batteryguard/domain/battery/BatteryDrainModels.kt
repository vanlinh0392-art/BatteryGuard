package com.pin.batteryguard.domain.battery

data class BatteryStatsSnapshot(
    val capturedAtMillis: Long,
    val capturedAtElapsedRealtime: Long,
    val statsStartFingerprint: String,
    val capacityMah: Double,
    val chargeCounterUah: Long?,
    val consumers: Map<Int, UidPowerSnapshot>
)

data class UidPowerSnapshot(
    val uid: Int,
    val userId: Int,
    val totalPowerMah: Double,
    val foregroundTimeMs: Long = 0L,
    val backgroundTimeMs: Long = 0L,
    val cpuPowerMah: Double = 0.0,
    val wakeLockPowerMah: Double = 0.0,
    val audioPowerMah: Double = 0.0,
    val gnssPowerMah: Double = 0.0,
    val sensorPowerMah: Double = 0.0
)

data class DrainEvidence(
    val cpuMah: Double = 0.0,
    val wakeLockMah: Double = 0.0,
    val audioMah: Double = 0.0,
    val gnssMah: Double = 0.0,
    val sensorMah: Double = 0.0,
    val foregroundTimeMs: Long = 0L,
    val backgroundTimeMs: Long = 0L
)

data class UidDrainSample(
    val uid: Int,
    val userId: Int,
    val deltaMah: Double,
    val ratePercentPerHour: Double,
    val durationMillis: Long,
    val evidence: DrainEvidence
)

enum class InvalidDiffReason { NONE, EMPTY_SNAPSHOT, INVALID_DURATION, STATS_RESET, INVALID_CAPACITY }

data class BatteryDrainDiff(
    val isValid: Boolean,
    val invalidReason: InvalidDiffReason = InvalidDiffReason.NONE,
    val durationMillis: Long = 0L,
    val samples: List<UidDrainSample> = emptyList()
)

data class AppInstanceKey(val userId: Int, val uid: Int, val packageName: String)

data class ResolvedUid(val uid: Int, val userId: Int, val packageNames: List<String>) {
    val isShared: Boolean get() = packageNames.size != 1
    val primaryPackage: String? get() = packageNames.singleOrNull()
}

enum class DetectionAction { NONE, WARN, FORCE_STOP, FREEZE }

enum class DecisionGuard {
    NONE, SAMPLE_TOO_SHORT, DELTA_TOO_SMALL, BELOW_THRESHOLD, EXCEPTION, SYSTEM_APP,
    SHARED_UID, PROTECTED_APP, ACTIVE_USE, AUTO_STOP_DISABLED, ACTION_COOLDOWN
}

data class DetectionContext(
    val consecutiveBreaches: Int = 0,
    val verifiedStopsInWindow: Int = 0,
    val isException: Boolean = false,
    val isSystemApp: Boolean = false,
    val isSharedUid: Boolean = false,
    val isProtected: Boolean = false,
    val isActiveUse: Boolean = false,
    val activeUseOverride: Boolean = false,
    val isInActionCooldown: Boolean = false
)

data class DetectionDecision(
    val action: DetectionAction,
    val reason: String,
    val guard: DecisionGuard = DecisionGuard.NONE,
    val nextConsecutiveBreaches: Int = 0
)

data class ActionResult(
    val commandSucceeded: Boolean,
    val verified: Boolean,
    val exitCode: Int,
    val stdout: String = "",
    val stderr: String = "",
    val errorMessage: String? = null
) {
    val isSuccess: Boolean get() = commandSucceeded && verified
}
