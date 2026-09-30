package com.pin.batteryguard.shizuku

import com.pin.batteryguard.domain.battery.ActionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForceStopManager @Inject constructor(
    private val shizukuManager: ShizukuManager
) {
    // ── C5: Frozen-packages cache with 30s TTL ──────────────────────────
    @Volatile private var cachedFrozenPackages: Set<String> = emptySet()
    @Volatile private var frozenCacheUserId: Int = -1
    @Volatile private var frozenCacheTime: Long = 0L
    private val FROZEN_CACHE_TTL = 30_000L // 30 seconds

    fun invalidateFrozenCache() {
        frozenCacheTime = 0L
    }
    // ─────────────────────────────────────────────────────────────────────

    suspend fun forceStopPackage(packageName: String, userId: Int = 0): Result<ActionResult> =
        resultOf(forceStopDetailed(packageName, userId))

    suspend fun freezePackage(packageName: String, userId: Int = 0): Result<ActionResult> =
        resultOf(freezeDetailed(packageName, userId)).also { invalidateFrozenCache() }

    suspend fun unfreezePackage(packageName: String, userId: Int = 0): Result<ActionResult> =
        resultOf(unfreezeDetailed(packageName, userId)).also { invalidateFrozenCache() }

    /**
     * Fast-path force-stop cho AppShield:
     * - Chỉ kiểm tra `isReady()` (KHÔNG gọi `ensureReady()` để tránh vô tình auto-enable Wireless Debugging)
     * - Không delay(250)
     * - Không dumpsys package
     * - Tốc độ thực thi ~15ms
     */
    suspend fun fastForceStop(packageName: String, userId: Int = 0): Boolean = withContext(Dispatchers.IO) {
        if (!shizukuManager.isReady()) return@withContext false
        val command = execute("am", "force-stop", "--user", userId.toString(), packageName, timeoutMs = 2000L)
        command.commandSucceeded
    }

    suspend fun forceStopDetailed(packageName: String, userId: Int): ActionResult = withContext(Dispatchers.IO) {
        if (!shizukuManager.ensureReady()) return@withContext unavailable("force-stop", packageName, userId)
        val command = execute("am", "force-stop", "--user", userId.toString(), packageName)
        if (!command.commandSucceeded) return@withContext command
        delay(250)
        val dump = execute("dumpsys", "package", packageName)
        val verified = Regex("User\\s+$userId:[^\\n]*stopped=true").containsMatchIn(dump.stdout)
        command.copy(
            verified = verified,
            errorMessage = if (verified) null else "Package stopped state was not verified for user $userId"
        )
    }

    suspend fun freezeDetailed(packageName: String, userId: Int): ActionResult = withContext(Dispatchers.IO) {
        if (!shizukuManager.ensureReady()) return@withContext unavailable("freeze", packageName, userId)
        val command = execute("pm", "disable-user", "--user", userId.toString(), packageName)
        if (!command.commandSucceeded) return@withContext command
        val verified = isPackageFrozen(packageName, userId)
        command.copy(verified = verified, errorMessage = if (verified) null else "Disabled state was not verified")
    }

    suspend fun unfreezeDetailed(packageName: String, userId: Int): ActionResult = withContext(Dispatchers.IO) {
        if (!shizukuManager.ensureReady()) return@withContext unavailable("unfreeze", packageName, userId)
        val command = execute("pm", "enable", "--user", userId.toString(), packageName)
        if (!command.commandSucceeded) return@withContext command
        val verified = !isPackageFrozen(packageName, userId)
        command.copy(verified = verified, errorMessage = if (verified) null else "Enabled state was not verified")
    }

    suspend fun isPackageFrozen(packageName: String, userId: Int = 0): Boolean = withContext(Dispatchers.IO) {
        packageName in listFrozenPackages(userId)
    }

    /** Read disabled packages once per profile instead of spawning one shell
     * process for every row in the Applications screen.
     * C5: Results are cached for [FROZEN_CACHE_TTL] ms to avoid repeated shell calls. */
    suspend fun listFrozenPackages(userId: Int = 0): Set<String> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (userId == frozenCacheUserId && (now - frozenCacheTime) < FROZEN_CACHE_TTL) {
            return@withContext cachedFrozenPackages
        }
        if (!shizukuManager.ensureReady()) return@withContext emptySet()
        val result = execute("pm", "list", "packages", "-d", "--user", userId.toString())
        if (!result.commandSucceeded) return@withContext emptySet()
        val packages = result.stdout.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:") }
            .filter { it.isNotBlank() }
            .toSet()
        cachedFrozenPackages = packages
        frozenCacheUserId = userId
        frozenCacheTime = now
        packages
    }

    suspend fun batchForceStop(packages: List<String>, userId: Int = 0): Map<String, Boolean> =
        packages.associateWith { forceStopDetailed(it, userId).isSuccess }

    suspend fun batchFreeze(packages: List<String>, userId: Int = 0): Map<String, Boolean> =
        packages.associateWith { freezeDetailed(it, userId).isSuccess }.also { invalidateFrozenCache() }

    suspend fun batchUnfreeze(packages: List<String>, userId: Int = 0): Map<String, Boolean> =
        packages.associateWith { unfreezeDetailed(it, userId).isSuccess }.also { invalidateFrozenCache() }

    private suspend fun resultOf(action: ActionResult): Result<ActionResult> =
        if (action.isSuccess) Result.success(action) else Result.failure(ActionFailedException(action))

    private fun unavailable(action: String, packageName: String, userId: Int) = ActionResult(
        commandSucceeded = false,
        verified = false,
        exitCode = -1,
        errorMessage = "Shizuku unavailable: cannot $action $packageName for user $userId"
    )

    private suspend fun execute(vararg command: String, timeoutMs: Long = 5000L): ActionResult {
        val result = executeShizukuCommandWithTimeout(command.toList().toTypedArray(), timeoutMs)
        return ActionResult(
            commandSucceeded = result.isSuccess,
            verified = false,
            exitCode = result.exitCode,
            stdout = result.stdout,
            stderr = result.stderr,
            errorMessage = if (result.isSuccess) null else result.stderr.ifBlank { "Command failed with exitCode ${result.exitCode}" }
        )
    }
}

class ActionFailedException(val actionResult: ActionResult) : Exception(
    actionResult.errorMessage ?: actionResult.stderr.ifBlank { "Action could not be verified" }
)
