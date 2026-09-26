package com.pin.batteryguard.data.repository

import com.pin.batteryguard.data.db.dao.MonitoringStateDao
import com.pin.batteryguard.data.db.entity.MonitoringSession
import com.pin.batteryguard.data.db.entity.UidBaseline
import com.pin.batteryguard.domain.battery.BatteryStatsSnapshot
import com.pin.batteryguard.domain.battery.UidPowerSnapshot
import javax.inject.Inject
import javax.inject.Singleton

data class PersistedMonitoringState(
    val snapshot: BatteryStatsSnapshot,
    val consecutiveBreaches: Map<Int, Int>,
    val startedAtMillis: Long,
    val startedAtElapsedRealtime: Long
)

@Singleton
class MonitoringStateStore @Inject constructor(
    private val dao: MonitoringStateDao
) {
    suspend fun load(): PersistedMonitoringState? {
        val session = dao.getSession() ?: return null
        val baselines = dao.getBaselines()
        if (baselines.isEmpty()) return null
        return PersistedMonitoringState(
            snapshot = BatteryStatsSnapshot(
                capturedAtMillis = session.lastCapturedAtMillis,
                capturedAtElapsedRealtime = session.lastCapturedAtElapsedRealtime,
                statsStartFingerprint = session.statsStartFingerprint,
                capacityMah = session.capacityMah,
                chargeCounterUah = session.chargeCounterUah,
                consumers = baselines.associate { baseline ->
                    baseline.uid to UidPowerSnapshot(
                        uid = baseline.uid,
                        userId = baseline.userId,
                        totalPowerMah = baseline.totalPowerMah,
                        foregroundTimeMs = baseline.foregroundTimeMs,
                        backgroundTimeMs = baseline.backgroundTimeMs,
                        cpuPowerMah = baseline.cpuPowerMah,
                        wakeLockPowerMah = baseline.wakeLockPowerMah,
                        audioPowerMah = baseline.audioPowerMah,
                        gnssPowerMah = baseline.gnssPowerMah,
                        sensorPowerMah = baseline.sensorPowerMah
                    )
                }
            ),
            consecutiveBreaches = baselines.associate { it.uid to it.consecutiveBreaches },
            startedAtMillis = session.startedAtMillis,
            startedAtElapsedRealtime = session.startedAtElapsedRealtime
        )
    }

    suspend fun save(
        snapshot: BatteryStatsSnapshot,
        breaches: Map<Int, Int>,
        startedAtMillis: Long = snapshot.capturedAtMillis,
        startedAtElapsedRealtime: Long = snapshot.capturedAtElapsedRealtime,
        isCharging: Boolean = false
    ) {
        val session = MonitoringSession(
            startedAtMillis = startedAtMillis,
            startedAtElapsedRealtime = startedAtElapsedRealtime,
            lastCapturedAtMillis = snapshot.capturedAtMillis,
            lastCapturedAtElapsedRealtime = snapshot.capturedAtElapsedRealtime,
            statsStartFingerprint = snapshot.statsStartFingerprint,
            capacityMah = snapshot.capacityMah,
            chargeCounterUah = snapshot.chargeCounterUah,
            isScreenOff = true,
            isCharging = isCharging
        )
        val rows = snapshot.consumers.values.map { consumer ->
            UidBaseline(
                uid = consumer.uid,
                userId = consumer.userId,
                totalPowerMah = consumer.totalPowerMah,
                foregroundTimeMs = consumer.foregroundTimeMs,
                backgroundTimeMs = consumer.backgroundTimeMs,
                cpuPowerMah = consumer.cpuPowerMah,
                wakeLockPowerMah = consumer.wakeLockPowerMah,
                audioPowerMah = consumer.audioPowerMah,
                gnssPowerMah = consumer.gnssPowerMah,
                sensorPowerMah = consumer.sensorPowerMah,
                consecutiveBreaches = breaches[consumer.uid] ?: 0
            )
        }
        dao.replaceState(session, rows)
    }

    suspend fun clear() = dao.clearState()
}
