package com.pin.batteryguard.domain.battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryDrainDifferTest {
    private val differ = BatteryDrainDiffer()

    @Test
    fun diff_detects_uid_drain_without_integer_battery_level_change() {
        val previous = snapshot(elapsed = 0L, totalMah = 10.0)
        val current = snapshot(elapsed = 30 * 60 * 1000L, totalMah = 21.525)

        val result = differ.diff(previous, current)

        assertTrue(result.isValid)
        assertEquals(1, result.samples.size)
        assertEquals(11.525, result.samples.single().deltaMah, 0.001)
        assertEquals(0.5, result.samples.single().ratePercentPerHour, 0.001)
    }

    @Test
    fun diff_rejects_changed_batterystats_epoch() {
        val previous = snapshot(elapsed = 0L, totalMah = 10.0, fingerprint = "old")
        val current = snapshot(elapsed = 20 * 60 * 1000L, totalMah = 1.0, fingerprint = "new")

        val result = differ.diff(previous, current)

        assertFalse(result.isValid)
        assertEquals(InvalidDiffReason.STATS_RESET, result.invalidReason)
        assertTrue(result.samples.isEmpty())
    }

    private fun snapshot(elapsed: Long, totalMah: Double, fingerprint: String = "epoch") =
        BatteryStatsSnapshot(
            capturedAtMillis = elapsed,
            capturedAtElapsedRealtime = elapsed,
            statsStartFingerprint = fingerprint,
            capacityMah = 4610.0,
            chargeCounterUah = null,
            consumers = mapOf(
                11384 to UidPowerSnapshot(
                    uid = 11384,
                    userId = 0,
                    totalPowerMah = totalMah
                )
            )
        )
}
