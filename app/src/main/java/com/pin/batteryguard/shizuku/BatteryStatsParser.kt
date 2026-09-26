package com.pin.batteryguard.shizuku

import android.content.Context
import android.os.BatteryManager
import android.os.SystemClock
import com.pin.batteryguard.domain.battery.BatteryStatsSnapshot
import com.pin.batteryguard.domain.battery.BatteryStatsSnapshotParser
import com.pin.batteryguard.domain.model.AppBatteryInfo
import com.pin.batteryguard.util.PackageHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BatteryStatsParser @Inject constructor(
    @ApplicationContext private val context: Context,
    private val shizukuManager: ShizukuManager,
    private val uidPackageResolver: UidPackageResolver
) {
    private val snapshotParser = BatteryStatsSnapshotParser()

    data class RawPowerStat(
        val uid: Int,
        val packageName: String,
        val appName: String,
        val powerMah: Float,
        val foregroundTimeMs: Long,
        val backgroundTimeMs: Long,
        val userId: Int = 0,
        val isSharedUid: Boolean = false
    )

    suspend fun captureSnapshot(): BatteryStatsSnapshot = withContext(Dispatchers.IO) {
        val wallTime = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val chargeCounter = (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
            .getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            .takeUnless { it == Long.MIN_VALUE || it <= 0L }
        snapshotParser.parse(
            dump = getBatteryStatsDump(),
            capturedAtMillis = wallTime,
            capturedAtElapsedRealtime = elapsed,
            chargeCounterUah = chargeCounter,
            fallbackCapacityMah = getBatteryDesignCapacity()
        )
    }

    private fun getBatteryDesignCapacity(): Double {
        return try {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val powerProfile = powerProfileClass.getConstructor(Context::class.java).newInstance(context)
            val cap = powerProfileClass.getMethod("getBatteryCapacity").invoke(powerProfile) as Double
            if (cap > 0.0) cap else 5000.0
        } catch (_: Exception) {
            5000.0
        }
    }

    suspend fun getBatteryStatsDump(): String = withContext(Dispatchers.IO) {
        if (!shizukuManager.ensureReady()) return@withContext ""
        val result = executeShizukuCommandWithTimeout(arrayOf("dumpsys", "batterystats", "--charged"), timeoutMs = 8000L)
        if (result.isSuccess) {
            result.stdout
        } else {
            android.util.Log.e(TAG, "batterystats failed (${result.exitCode}): ${result.stderr}")
            ""
        }
    }

    suspend fun parsePerAppStats(totalBatteryCapacityMah: Float = 0f): List<RawPowerStat> {
        val snapshot = captureSnapshot()
        val resolved = uidPackageResolver.resolveAll()
        return snapshot.consumers.values.mapNotNull { consumer ->
            val packages = resolved[consumer.uid]?.packageNames.orEmpty()
            val packageName = packages.firstOrNull() ?: return@mapNotNull null
            RawPowerStat(
                uid = consumer.uid,
                packageName = packageName,
                appName = appName(packages),
                powerMah = consumer.totalPowerMah.toFloat(),
                foregroundTimeMs = consumer.foregroundTimeMs,
                backgroundTimeMs = consumer.backgroundTimeMs,
                userId = consumer.userId,
                isSharedUid = packages.size != 1
            )
        }
    }

    @Suppress("UNUSED_PARAMETER")
    suspend fun getAppBatteryUsageList(
        totalBatteryDropPercent: Float = 0f,
        totalBatteryCapacityMah: Float = 0f
    ): List<AppBatteryInfo> {
        val snapshot = captureSnapshot()
        if (snapshot.capacityMah <= 0.0) return emptyList()
        val resolved = uidPackageResolver.resolveAll()
        return snapshot.consumers.values.mapNotNull { consumer ->
            val packages = resolved[consumer.uid]?.packageNames.orEmpty()
            val primary = packages.firstOrNull() ?: return@mapNotNull null
            val percent = consumer.totalPowerMah / snapshot.capacityMah * 100.0
            AppBatteryInfo(
                packageName = primary,
                appName = appName(packages),
                appIcon = PackageHelper.getAppIcon(context, primary),
                drainPercent = percent.toFloat(),
                foregroundTimeMs = consumer.foregroundTimeMs,
                backgroundTimeMs = consumer.backgroundTimeMs,
                isRunning = PackageHelper.isAppRunning(context, primary),
                isException = false,
                uid = consumer.uid,
                userId = consumer.userId,
                packageNames = packages,
                isSharedUid = packages.size != 1,
                deltaMah = consumer.totalPowerMah,
                evidence = "cumulative snapshot; waiting for screen-off delta"
            )
        }.sortedByDescending { it.deltaMah }
    }

    private fun appName(packages: List<String>): String {
        val first = packages.firstOrNull() ?: return "Unknown UID"
        val base = PackageHelper.getAppName(context, first)
        return if (packages.size == 1) base else "$base +${packages.size - 1} (shared UID)"
    }

    companion object {
        private const val TAG = "BatteryStatsParser"
    }
}
