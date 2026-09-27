package com.pin.batteryguard.domain.battery

class BatteryStatsSnapshotParser {
    private val capacityPattern = Regex("Capacity:\\s*([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
    private val estimatedCapacityPattern = Regex("Estimated battery capacity:\\s*([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
    private val startPattern = Regex("Start clock time:\\s*(.+)", RegexOption.IGNORE_CASE)
    private val uidPattern = Regex("^\\s*UID\\s+(\\S+):\\s*([0-9]+(?:\\.[0-9]+)?)(.*)$", RegexOption.IGNORE_CASE)
    private val durationPattern = Regex("(\\d+)(ms|d|h|m|s)")

    fun parse(dump: String, capturedAtMillis: Long, capturedAtElapsedRealtime: Long, chargeCounterUah: Long? = null, fallbackCapacityMah: Double = 5000.0): BatteryStatsSnapshot {
        if (!dump.contains("Estimated power use (mAh):")) return emptySnapshot(capturedAtMillis, capturedAtElapsedRealtime, chargeCounterUah, fallbackCapacityMah)
        val lines = dump.lineSequence().toList()
        var capacity = lines.firstNotNullOfOrNull { capacityPattern.find(it)?.groupValues?.get(1)?.toDoubleOrNull() } ?: 0.0
        if (capacity <= 0.0) {
            capacity = lines.firstNotNullOfOrNull { estimatedCapacityPattern.find(it)?.groupValues?.get(1)?.toDoubleOrNull() } ?: fallbackCapacityMah
        }
        val fingerprint = lines.firstNotNullOfOrNull { startPattern.find(it)?.groupValues?.get(1)?.trim() }.orEmpty()
        val consumers = linkedMapOf<Int, UidPowerSnapshot>()
        var inPowerSection = false
        var pendingUid: UidPowerSnapshot? = null
        var awaitingDetail = false

        for (line in lines) {
            if (line.contains("Estimated power use (mAh):")) {
                inPowerSection = true
                continue
            }
            if (!inPowerSection) continue
            if (line.isNotBlank() && line.firstOrNull()?.isWhitespace() == false) break
            val uidMatch = uidPattern.matchEntire(line)
            if (uidMatch != null) {
                pendingUid?.let { consumers[it.uid] = it }
                val uid = parseUid(uidMatch.groupValues[1])
                val totalPower = uidMatch.groupValues[2].toDoubleOrNull()
                if (uid == null || totalPower == null) {
                    pendingUid = null
                    awaitingDetail = false
                    continue
                }
                val suffix = uidMatch.groupValues[3]
                pendingUid = UidPowerSnapshot(
                    uid = uid,
                    userId = userIdFromUid(uid),
                    totalPowerMah = totalPower,
                    foregroundTimeMs = durationForState(suffix, "fg"),
                    backgroundTimeMs = durationForState(suffix, "bg") + durationForState(suffix, "fgs")
                )
                awaitingDetail = true
                continue
            }
            if (!awaitingDetail || line.trimStart().startsWith("(") || line.isBlank()) continue
            val current = pendingUid ?: continue
            pendingUid = current.copy(
                cpuPowerMah = metric(line, "cpu"),
                wakeLockPowerMah = metric(line, "wakelock"),
                audioPowerMah = metric(line, "audio"),
                gnssPowerMah = metric(line, "gnss"),
                sensorPowerMah = metric(line, "sensors")
            )
            awaitingDetail = false
        }
        pendingUid?.let { consumers[it.uid] = it }
        return BatteryStatsSnapshot(capturedAtMillis, capturedAtElapsedRealtime, fingerprint, capacity, chargeCounterUah, consumers)
    }

    private fun emptySnapshot(wall: Long, elapsed: Long, chargeCounterUah: Long?, capacity: Double = 0.0) =
        BatteryStatsSnapshot(wall, elapsed, "", capacity, chargeCounterUah, emptyMap())

    /**
     * Extract metric value using String.indexOf instead of dynamic Regex construction.
     * Avoids creating a new Regex object on every call (was called 5× per UID line).
     */
    private fun metric(line: String, name: String): Double {
        val needle = "$name="
        var idx = line.indexOf(needle)
        while (idx >= 0) {
            // Ensure it's at start of line or preceded by whitespace
            if (idx == 0 || line[idx - 1].isWhitespace()) {
                val valueStart = idx + needle.length
                val valueEnd = findNumberEnd(line, valueStart)
                if (valueEnd > valueStart) {
                    return line.substring(valueStart, valueEnd).toDoubleOrNull() ?: 0.0
                }
            }
            idx = line.indexOf(needle, idx + 1)
        }
        return 0.0
    }

    /**
     * Extract duration for a state (fg, bg, fgs) using String.indexOf instead of dynamic Regex.
     */
    private fun durationForState(suffix: String, state: String): Long {
        val needle = "$state:"
        var idx = suffix.indexOf(needle)
        while (idx >= 0) {
            if (idx == 0 || suffix[idx - 1].isWhitespace()) {
                // Find the parenthesized duration part: "fg: 1.23 (1h 2m 3s)"
                val afterColon = idx + needle.length
                val parenOpen = suffix.indexOf('(', afterColon)
                if (parenOpen >= 0) {
                    val parenClose = suffix.indexOf(')', parenOpen)
                    if (parenClose > parenOpen) {
                        return parseDuration(suffix.substring(parenOpen + 1, parenClose))
                    }
                }
                return 0L
            }
            idx = suffix.indexOf(needle, idx + 1)
        }
        return 0L
    }

    private fun findNumberEnd(s: String, start: Int): Int {
        var i = start
        var hasDot = false
        while (i < s.length) {
            val c = s[i]
            if (c in '0'..'9') { i++; continue }
            if (c == '.' && !hasDot) { hasDot = true; i++; continue }
            break
        }
        return i
    }

    private fun parseDuration(value: String): Long = durationPattern.findAll(value).sumOf {
        val amount = it.groupValues[1].toLongOrNull() ?: 0L
        when (it.groupValues[2]) {
            "d" -> amount * 24L * 60L * 60L * 1000L
            "h" -> amount * 60L * 60L * 1000L
            "m" -> amount * 60L * 1000L
            "s" -> amount * 1000L
            else -> amount
        }
    }

    internal fun parseUid(raw: String): Int? {
        raw.toIntOrNull()?.let { return it }
        val match = UID_REGEX.matchEntire(raw)
        if (match != null) {
            val userId = match.groupValues[1].toIntOrNull() ?: return null
            val appId = match.groupValues[2].toIntOrNull() ?: return null
            return userId * PER_USER_RANGE + FIRST_APPLICATION_UID + appId
        }
        return when (raw.lowercase()) {
            "system" -> 1000
            "phone" -> 1001
            "bluetooth" -> 1002
            "media" -> 1013
            else -> null
        }
    }

    companion object {
        const val PER_USER_RANGE = 100_000
        const val FIRST_APPLICATION_UID = 10_000
        private val UID_REGEX = Regex("u(\\d+)_?a(\\d+)", RegexOption.IGNORE_CASE)
        fun userIdFromUid(uid: Int): Int = uid / PER_USER_RANGE
    }
}
