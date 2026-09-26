package com.pin.batteryguard.shizuku

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class ProcessExecutionResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val isTimedOut: Boolean = false,
    val isSuccess: Boolean = exitCode == 0 && !isTimedOut
)

/**
 * Thực thi lệnh qua Shizuku process có cơ chế Timeout an toàn, tự động hủy process nếu bị treo.
 */
suspend fun executeShizukuCommandWithTimeout(
    cmd: Array<String>,
    timeoutMs: Long = 5000L
): ProcessExecutionResult = withContext(Dispatchers.IO) {
    var process: java.lang.Process? = null
    try {
        process = shizukuNewProcess(cmd, null, null)
        val p = process

        val result = withTimeoutOrNull(timeoutMs) {
            val stdout = p.inputStream.bufferedReader().readText().trim()
            val stderr = p.errorStream.bufferedReader().readText().trim()
            val exitCode = p.waitFor()
            ProcessExecutionResult(
                exitCode = exitCode,
                stdout = stdout,
                stderr = stderr,
                isTimedOut = false
            )
        }

        if (result != null) {
            result
        } else {
            // Timeout xảy ra -> Hủy tiến trình cưỡng bức
            try {
                p.destroyForcibly()
            } catch (_: Exception) {}
            ProcessExecutionResult(
                exitCode = -1,
                stdout = "",
                stderr = "Lệnh bị hủy do vượt quá thời gian chờ (${timeoutMs}ms): ${cmd.joinToString(" ")}",
                isTimedOut = true
            )
        }
    } catch (e: Exception) {
        ProcessExecutionResult(
            exitCode = -1,
            stdout = "",
            stderr = e.message ?: e.javaClass.simpleName,
            isTimedOut = false
        )
    } finally {
 try {
 process?.inputStream?.close()
 process?.errorStream?.close()
 process?.outputStream?.close()
 } catch (_: Exception) {}
 }
}
