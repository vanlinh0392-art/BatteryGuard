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
    suspend fun forceStopPackage(packageName: String, userId: Int = 0): Result<ActionResult> =
        resultOf(forceStopDetailed(packageName, userId))

    suspend fun freezePackage(packageName: String, userId: Int = 0): Result<ActionResult> =
        resultOf(freezeDetailed(packageName, userId))

    suspend fun unfreezePackage(packageName: String, userId: Int = 0): Result<ActionResult> =
        resultOf(unfreezeDetailed(packageName, userId))

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
     * process for every row in the Applications screen. */
    suspend fun listFrozenPackages(userId: Int = 0): Set<String> = withContext(Dispatchers.IO) {
        if (!shizukuManager.ensureReady()) return@withContext emptySet()
        val result = execute("pm", "list", "packages", "-d", "--user", userId.toString())
        if (!result.commandSucceeded) return@withContext emptySet()
        result.stdout.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:") }
            .filter { it.isNotBlank() }
            .toSet()
    }

    suspend fun batchForceStop(packages: List<String>, userId: Int = 0): Map<String, Boolean> =
        packages.associateWith { forceStopDetailed(it, userId).isSuccess }

    suspend fun batchFreeze(packages: List<String>, userId: Int = 0): Map<String, Boolean> =
        packages.associateWith { freezeDetailed(it, userId).isSuccess }

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
