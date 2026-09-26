package com.pin.batteryguard.domain.battery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryStatsSnapshotParserTest {
    private val parser = BatteryStatsSnapshotParser()

    @Test
    fun parse_android16_power_section_keeps_uid_totals_once() {
        val dump = """
            Battery History:
            Start clock time: 2026-07-30-01-00-00
            Estimated power use (mAh):
              Capacity: 4610, Computed drain: 100, actual drain: 98
              UID u0a1384: 33.9 fg: 0.651 (3m 35s 938ms) bg: 28.0 (14h 13m 34s 204ms) cached: 1.06
                  screen=4.19 cpu=29.4 (16m 48s 884ms) audio=0.233 (35s 440ms) sensors=1.25 gnss=0.0136 (748ms) wakelock=0.0398 (8s 684ms)
                  (on battery, screen off/doze) cpu=25.3 (12m 37s 258ms)
              UID u999a42: 2.50 bg: 2.00 (20m 0s)
                  cpu=1.25 (10m 0s) wakelock=0.50 (5m 0s)
        """.trimIndent()

        val snapshot = parser.parse(dump, capturedAtMillis = 1000L, capturedAtElapsedRealtime = 500L)

        assertEquals(4610.0, snapshot.capacityMah, 0.001)
        assertEquals("2026-07-30-01-00-00", snapshot.statsStartFingerprint)
        assertEquals(2, snapshot.consumers.size)

        val zalo = snapshot.consumers.getValue(11384)
        assertEquals(0, zalo.userId)
        assertEquals(33.9, zalo.totalPowerMah, 0.001)
        assertEquals(29.4, zalo.cpuPowerMah, 0.001)
        assertEquals(0.0398, zalo.wakeLockPowerMah, 0.0001)
        assertEquals(14 * 60 * 60 * 1000L + 13 * 60 * 1000L + 34 * 1000L + 204L, zalo.backgroundTimeMs)

        val clone = snapshot.consumers.getValue(99_910_042)
        assertEquals(999, clone.userId)
        assertEquals(2.5, clone.totalPowerMah, 0.001)
    }

    @Test
    fun parse_malformed_dump_returns_empty_snapshot_instead_of_fabricating_usage() {
        val snapshot = parser.parse("not a batterystats dump", 10L, 5L)

        assertTrue(snapshot.consumers.isEmpty())
        assertEquals(0.0, snapshot.capacityMah, 0.0)
    }
}
