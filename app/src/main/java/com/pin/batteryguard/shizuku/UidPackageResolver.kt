package com.pin.batteryguard.shizuku

import com.pin.batteryguard.domain.battery.BatteryStatsSnapshotParser
import com.pin.batteryguard.domain.battery.ResolvedUid
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UidPackageResolver @Inject constructor(
    private val shizukuManager: ShizukuManager
) {
    @Volatile private var cachedAtMillis: Long = 0L
    @Volatile private var cached: Map<Int, ResolvedUid> = emptyMap()

    suspend fun resolveAll(forceRefresh: Boolean = false): Map<Int, ResolvedUid> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cached.isNotEmpty() && now - cachedAtMillis < CACHE_TTL_MILLIS) return@withContext cached
        if (!shizukuManager.ensureReady()) return@withContext cached

        val userIds = execute("pm", "list", "users")
            .lineSequence()
            .mapNotNull { USER_PATTERN.find(it)?.groupValues?.get(1)?.toIntOrNull() }
            .toSet()
            .ifEmpty { setOf(0) }
        val packagesByUid = linkedMapOf<Int, MutableSet<String>>()
        for (userId in userIds) {
            execute("pm", "list", "packages", "-U", "--user", userId.toString())
                .lineSequence()
                .forEach { line ->
                    val match = PACKAGE_PATTERN.find(line) ?: return@forEach
                    val packageName = match.groupValues[1]
                    val uid = match.groupValues[2].toIntOrNull() ?: return@forEach
                    packagesByUid.getOrPut(uid) { linkedSetOf() }.add(packageName)
                }
        }

        cached = packagesByUid.mapValues { (uid, packages) ->
            ResolvedUid(
                uid = uid,
                userId = BatteryStatsSnapshotParser.userIdFromUid(uid),
                packageNames = packages.sorted()
            )
        }
        cachedAtMillis = now
        cached
    }

    fun invalidate() {
        cachedAtMillis = 0L
        cached = emptyMap()
    }

    private suspend fun execute(vararg command: String): String {
        val result = executeShizukuCommandWithTimeout(command.toList().toTypedArray(), timeoutMs = 6000L)
        return if (result.isSuccess) result.stdout else ""
    }

    companion object {
        private const val CACHE_TTL_MILLIS = 5 * 60 * 1000L
        private val USER_PATTERN = Regex("UserInfo\\{(\\d+):")
        private val PACKAGE_PATTERN = Regex("package:(\\S+)\\s+uid:(\\d+)")
    }
}
