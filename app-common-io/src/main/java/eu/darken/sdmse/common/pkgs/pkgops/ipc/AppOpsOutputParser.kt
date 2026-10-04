package eu.darken.sdmse.common.pkgs.pkgops.ipc

internal object AppOpsOutputParser {

    private val supportedModes = setOf("allow", "ignore", "deny", "default")

    fun parse(
        key: String,
        output: List<String>,
    ): String? {
        val explicit = Regex(
            pattern = """^\s*${Regex.escape(key)}\s*:\s*([A-Za-z_]+)\b""",
            option = RegexOption.IGNORE_CASE,
        )

        output.forEach { line ->
            val mode = explicit.find(line)?.groupValues?.getOrNull(1)?.lowercase()
            if (mode != null) {
                return mode.takeIf { it in supportedModes }
            }
        }

        val hasNoOperations = output.any {
            it.trim().equals("No operations.", ignoreCase = true)
        }
        if (!hasNoOperations) return null

        val defaultMode = Regex(
            pattern = """^\s*Default mode:\s*([A-Za-z_]+)\b""",
            option = RegexOption.IGNORE_CASE,
        )

        return output
            .firstNotNullOfOrNull { line ->
                defaultMode.find(line)?.groupValues?.getOrNull(1)?.lowercase()
            }
            ?.takeIf { it in supportedModes }
    }
}
