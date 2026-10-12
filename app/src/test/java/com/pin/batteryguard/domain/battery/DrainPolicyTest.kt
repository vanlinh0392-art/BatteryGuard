package com.pin.batteryguard.domain.battery

import com.pin.batteryguard.domain.model.MonitoringConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class DrainPolicyTest {
    private val policy = DrainPolicy()
    private val config = MonitoringConfig()

    @Test
    fun evaluate_stops_exceeded_threshold_immediately() {
        val sample = sample(rate = 0.6, delta = 4.0)

        val first = policy.evaluate(sample, context(consecutiveBreaches = 0), config)
        val second = policy.evaluate(sample, context(consecutiveBreaches = 1), config)

        assertEquals(DetectionAction.FORCE_STOP, first.action)
        assertEquals("exceeded_threshold", first.reason)
        assertEquals(DetectionAction.FORCE_STOP, second.action)
        assertEquals("exceeded_threshold", second.reason)
    }

    @Test
    fun evaluate_stops_critical_sample_immediately() {
        val decision = policy.evaluate(sample(rate = 1.6, delta = 12.0), context(), config)

        assertEquals(DetectionAction.FORCE_STOP, decision.action)
        assertEquals("exceeded_threshold", decision.reason)
    }

    @Test
    fun evaluate_reports_but_never_stops_protected_or_shared_uid() {
        val protected = policy.evaluate(sample(rate = 2.0, delta = 20.0), context(isProtected = true), config)
        val shared = policy.evaluate(sample(rate = 2.0, delta = 20.0), context(isSharedUid = true), config)

        assertEquals(DetectionAction.WARN, protected.action)
        assertEquals(DecisionGuard.PROTECTED_APP, protected.guard)
        assertEquals(DetectionAction.WARN, shared.action)
        assertEquals(DecisionGuard.SHARED_UID, shared.guard)
    }

    @Test
    fun evaluate_honors_notify_only_setting() {
        val decision = policy.evaluate(
            sample(rate = 2.0, delta = 20.0),
            context(),
            config.copy(autoForceStop = false)
        )

        assertEquals(DetectionAction.WARN, decision.action)
        assertEquals(DecisionGuard.AUTO_STOP_DISABLED, decision.guard)
    }

    @Test
    fun evaluate_freezes_only_after_two_verified_stops_and_another_breach() {
        val decision = policy.evaluate(
            sample(rate = 0.7, delta = 5.0),
            context(consecutiveBreaches = 1, verifiedStopsInWindow = 2),
            config.copy(enableFreezeMode = true)
        )

        assertEquals(DetectionAction.FREEZE, decision.action)
    }

    private fun sample(rate: Double, delta: Double) = UidDrainSample(
        uid = 11384,
        userId = 0,
        deltaMah = delta,
        ratePercentPerHour = rate,
        durationMillis = 15 * 60 * 1000L,
        evidence = DrainEvidence()
    )

    private fun context(
        consecutiveBreaches: Int = 0,
        verifiedStopsInWindow: Int = 0,
        isProtected: Boolean = false,
        isSharedUid: Boolean = false
    ) = DetectionContext(
        consecutiveBreaches = consecutiveBreaches,
        verifiedStopsInWindow = verifiedStopsInWindow,
        isProtected = isProtected,
        isSharedUid = isSharedUid
    )
}
