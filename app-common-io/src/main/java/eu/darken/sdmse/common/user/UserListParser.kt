package eu.darken.sdmse.common.user

/**
 * Pure parser for Android's user-list shell output.
 *
 * Android 13+ exposes a typed format via `cmd user list -v`, while older releases use the
 * `pm list users` UserInfo format. Keep parsing isolated from shell execution so format drift
 * fails closed and can be covered without a device.
 */
internal object UserListParser {

    data class ParsedUser(
        val id: Int,
        val name: String?,
        val code: String?,
        val rawType: String?,
        val flags: Set<String>,
        val isRunning: Boolean,
        val isCurrent: Boolean,
        val isVisible: Boolean,
        val isQuietMode: Boolean,
        val type: UserProfile2.Type,
    )

    fun parse(lines: Iterable<String>): List<ParsedUser> = lines.mapNotNull { line ->
        parseModern(line) ?: parseLegacy(line)
    }

    private fun parseModern(rawLine: String): ParsedUser? {
        val line = rawLine.trim()
        val idMarker = "id="
        val nameMarker = ", name="
        val typeMarker = ", type="
        val flagsMarker = ", flags="

        val idStart = line.indexOf(idMarker)
        val nameStartMarker = line.indexOf(nameMarker, startIndex = (idStart + idMarker.length).coerceAtLeast(0))
        val flagsStartMarker = line.lastIndexOf(flagsMarker)
        val typeStartMarker = if (flagsStartMarker > 0) line.lastIndexOf(typeMarker, flagsStartMarker - 1) else -1

        if (idStart < 0 || nameStartMarker < 0 || typeStartMarker < 0 || flagsStartMarker < 0) return null
        if (!(idStart < nameStartMarker && nameStartMarker < typeStartMarker && typeStartMarker < flagsStartMarker)) {
            return null
        }

        val id = line.substring(idStart + idMarker.length, nameStartMarker).trim().toIntOrNull() ?: return null
        val name = line.substring(nameStartMarker + nameMarker.length, typeStartMarker)
            .trim()
            .takeUnless { it == "null" }
        val rawType = line.substring(typeStartMarker + typeMarker.length, flagsStartMarker)
            .trim()
            .takeIf { it.isNotEmpty() && it != "null" }

        val flagsAndStatus = line.substring(flagsStartMarker + flagsMarker.length).trim()
        val firstStatus = flagsAndStatus.indexOf(" (")
        val flagsPart = if (firstStatus >= 0) flagsAndStatus.substring(0, firstStatus) else flagsAndStatus
        val statusPart = if (firstStatus >= 0) flagsAndStatus.substring(firstStatus) else ""

        val flags = flagsPart
            .split('|')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        return ParsedUser(
            id = id,
            name = name,
            code = null,
            rawType = rawType,
            flags = flags,
            isRunning = statusPart.contains("(running)"),
            isCurrent = statusPart.contains("(current)"),
            isVisible = statusPart.contains("(visible)"),
            isQuietMode = "QUIET_MODE" in flags,
            type = classify(rawType = rawType, flags = flags),
        )
    }

    private fun parseLegacy(rawLine: String): ParsedUser? {
        val marker = "UserInfo{"
        val start = rawLine.indexOf(marker)
        if (start < 0) return null
        val close = rawLine.indexOf('}', startIndex = start + marker.length)
        if (close < 0) return null

        val body = rawLine.substring(start + marker.length, close)
        val firstColon = body.indexOf(':')
        val lastColon = body.lastIndexOf(':')
        if (firstColon <= 0 || lastColon <= firstColon) return null

        val id = body.substring(0, firstColon).trim().toIntOrNull() ?: return null
        val name = body.substring(firstColon + 1, lastColon)
            .trim()
            .takeUnless { it == "null" }
        val code = body.substring(lastColon + 1).trim()
        val bits = code.toIntOrNull(radix = 16) ?: return null
        val flags = legacyFlags(bits)
        val tail = rawLine.substring(close + 1)

        return ParsedUser(
            id = id,
            name = name,
            code = code,
            rawType = null,
            flags = flags,
            isRunning = tail.contains("running"),
            isCurrent = false,
            isVisible = false,
            isQuietMode = bits and FLAG_QUIET_MODE != 0,
            type = classify(rawType = null, flags = flags),
        )
    }

    internal fun classify(rawType: String?, flags: Set<String>): UserProfile2.Type = when {
        rawType?.endsWith("profile.PRIVATE", ignoreCase = true) == true ->
            UserProfile2.Type.PRIVATE_PROFILE

        rawType?.endsWith("profile.MANAGED", ignoreCase = true) == true || "MANAGED_PROFILE" in flags ->
            UserProfile2.Type.WORK_PROFILE

        rawType?.contains(".profile.", ignoreCase = true) == true || "PROFILE" in flags ->
            UserProfile2.Type.OTHER_PROFILE

        rawType?.endsWith("full.SYSTEM", ignoreCase = true) == true || "SYSTEM" in flags ->
            UserProfile2.Type.SYSTEM

        rawType?.contains(".full.", ignoreCase = true) == true || "FULL" in flags ->
            UserProfile2.Type.FULL_USER

        else -> UserProfile2.Type.UNKNOWN
    }

    private fun legacyFlags(bits: Int): Set<String> = buildSet {
        if (bits and FLAG_MANAGED_PROFILE != 0) add("MANAGED_PROFILE")
        if (bits and FLAG_QUIET_MODE != 0) add("QUIET_MODE")
        if (bits and FLAG_FULL != 0) add("FULL")
        if (bits and FLAG_SYSTEM != 0) add("SYSTEM")
        if (bits and FLAG_PROFILE != 0) add("PROFILE")
    }

    private const val FLAG_MANAGED_PROFILE = 0x00000020
    private const val FLAG_QUIET_MODE = 0x00000080
    private const val FLAG_FULL = 0x00000400
    private const val FLAG_SYSTEM = 0x00000800
    private const val FLAG_PROFILE = 0x00001000
}
