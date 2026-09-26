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

    private fun metric(line: String, name: String): Double =
        Regex("(?:^|\\s)${Regex.escape(name)}=([0-9]+(?:\\.[0-9]+)?)").find(line)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

    private fun durationForState(suffix: String, state: String): Long {
        val match = Regex("(?:^|\\s)${Regex.escape(state)}:\\s*[0-9.]+(?:\\s*\\(([^)]*)\\))?").find(suffix) ?: return 0L
        return parseDuration(match.groupValues.getOrElse(1) { "" })
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
        val match = Regex("u(\\d+)_?a(\\d+)", RegexOption.IGNORE_CASE).matchEntire(raw)
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
        fun userIdFromUid(uid: Int): Int = uid / PER_USER_RANGE
    }
}
