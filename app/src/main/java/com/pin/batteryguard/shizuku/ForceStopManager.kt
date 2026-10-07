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

    @Synchronized
    private fun mutateFrozenCache(packageName: String, isFrozen: Boolean, userId: Int) {
        if (userId == frozenCacheUserId) {
            cachedFrozenPackages = if (isFrozen) cachedFrozenPackages + packageName else cachedFrozenPackages - packageName
        }
    }
    // ─────────────────────────────────────────────────────────────────────

    suspend fun forceStopPackage(packageName: String, userId: Int = 0): Result<ActionResult> =
        resultOf(forceStopDetailed(packageName, userId))

    suspend fun freezePackage(packageName: String, userId: Int = 0): Result<ActionResult> =
        resultOf(freezeDetailed(packageName, userId)).also { invalidateFrozenCache() }

    suspend fun unfreezePackage(packageName: String, userId: Int = 0): Result<ActionResult> =
        resultOf(unfreezeDetailed(packageName, userId)).also { invalidateFrozenCache() }

    suspend fun unfreeze(packageName: String, userId: Int = 0): Boolean =
        unfreezeDetailed(packageName, userId).isSuccess

    suspend fun freeze(packageName: String, userId: Int = 0): Boolean =
        freezeDetailed(packageName, userId).isSuccess

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

    private val PROTECTED_PACKAGES = setOf(
        "com.pin.batteryguard",
        "moe.shizuku.privileged.api",
        "com.android.systemui"
    )

    suspend fun freezeDetailed(packageName: String, userId: Int): ActionResult = withContext(Dispatchers.IO) {
        if (!shizukuManager.ensureReady()) return@withContext unavailable("freeze", packageName, userId)
        if (packageName in PROTECTED_PACKAGES) {
            return@withContext ActionResult(
                commandSucceeded = false,
                verified = false,
                exitCode = -1,
                errorMessage = "Không thể đóng băng ứng dụng hệ thống trọng yếu: $packageName"
            )
        }
        val command = execute("pm", "disable-user", "--user", userId.toString(), packageName)
        if (!command.commandSucceeded) return@withContext command

        // Two-Phase Verification:
        // Pha 1: Regex kiểm tra stdout của lệnh pm disable-user
        val disableRegex = Regex("""(?i)new\s+state:\s*(disabled|disabled-user)""")
        var verified = disableRegex.containsMatchIn(command.stdout)

        // Pha 2: Fallback dumpsys scoped theo User hoặc isPackageFrozen
        if (!verified) {
            val dump = execute("dumpsys", "package", packageName)
            val userScopedRegex = Regex("""User\s+$userId:[^\n]*\benabled=[234]\b""")
            verified = userScopedRegex.containsMatchIn(dump.stdout) || run {
                invalidateFrozenCache()
                isPackageFrozen(packageName, userId)
            }
        }

        if (verified) {
            mutateFrozenCache(packageName, isFrozen = true, userId = userId)
        }

        command.copy(verified = verified, errorMessage = if (verified) null else "Disabled state was not verified")
    }

    suspend fun unfreezeDetailed(packageName: String, userId: Int): ActionResult = withContext(Dispatchers.IO) {
        if (!shizukuManager.ensureReady()) return@withContext unavailable("unfreeze", packageName, userId)

        // Multi-Tier Unfreeze
        var command = execute("pm", "enable", "--user", userId.toString(), packageName)
        if (!command.commandSucceeded && userId == 0) {
            val fallbackCommand = execute("pm", "enable", packageName)
            if (fallbackCommand.commandSucceeded) {
                command = fallbackCommand
            }
        }
        if (!command.commandSucceeded) return@withContext command

        // Giải phóng suspend, unhide và sync Launcher
        execute("pm", "unsuspend", "--user", userId.toString(), packageName)
        execute("pm", "unhide", "--user", userId.toString(), packageName)
        execute("am", "broadcast", "-a", "android.intent.action.PACKAGE_CHANGED", "-d", "package:$packageName", "--user", userId.toString(), "-f", "0x01000000")

        // Two-Phase Verification:
        // Pha 1: Regex kiểm tra stdout của lệnh pm enable
        val enableRegex = Regex("""(?i)new\s+state:\s*(enabled|default)""")
        var verified = enableRegex.containsMatchIn(command.stdout)

        // Pha 2: Fallback dumpsys scoped theo User hoặc !isPackageFrozen
        if (!verified) {
            val dump = execute("dumpsys", "package", packageName)
            val userScopedRegex = Regex("""User\s+$userId:[^\n]*\benabled=[01]\b""")
            verified = userScopedRegex.containsMatchIn(dump.stdout) || run {
                invalidateFrozenCache()
                !isPackageFrozen(packageName, userId)
            }
        }

        if (verified) {
            mutateFrozenCache(packageName, isFrozen = false, userId = userId)
        }

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

    suspend fun batchUnfreeze(packages: List<String>, userId: Int = 0): Map<String, Boolean> = withContext(Dispatchers.IO) {
        if (packages.isEmpty()) return@withContext emptyMap()
        if (packages.size <= 3) {
            return@withContext packages.associateWith { unfreezeDetailed(it, userId).isSuccess }.also { invalidateFrozenCache() }
        }
        if (!shizukuManager.ensureReady()) {
            return@withContext packages.associateWith { false }
        }

        val script = buildString {
            append("for pkg in")
            for (pkg in packages) {
                append(" '").append(pkg.replace("'", "")).append("'")
            }
            append("; do ")
            append("if pm enable --user ").append(userId).append(" \"\$pkg\" >/dev/null 2>&1 || pm enable \"\$pkg\" >/dev/null 2>&1; then ")
            append("pm default-state --user ").append(userId).append(" \"\$pkg\" >/dev/null 2>&1; ")
            append("pm unsuspend --user ").append(userId).append(" \"\$pkg\" >/dev/null 2>&1; ")
            append("pm unhide --user ").append(userId).append(" \"\$pkg\" >/dev/null 2>&1; ")
            append("am broadcast -a android.intent.action.PACKAGE_CHANGED -d \"package:\$pkg\" --user ").append(userId).append(" -f 0x01000000 >/dev/null 2>&1; ")
            append("echo \"UNFROZEN:\$pkg\"; ")
            append("fi; ")
            append("done")
        }

        val timeoutMs = (packages.size * 2000L).coerceIn(10000L, 60000L)
        val result = execute("sh", "-c", script, timeoutMs = timeoutMs)
        invalidateFrozenCache()

        val unfrozenSuccessSet = result.stdout.lineSequence()
            .filter { it.startsWith("UNFROZEN:") }
            .map { it.removePrefix("UNFROZEN:").trim() }
            .toSet()

        packages.associateWith { pkg ->
            val success = pkg in unfrozenSuccessSet
            if (success) {
                mutateFrozenCache(pkg, isFrozen = false, userId = userId)
            }
            success
        }
    }

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
