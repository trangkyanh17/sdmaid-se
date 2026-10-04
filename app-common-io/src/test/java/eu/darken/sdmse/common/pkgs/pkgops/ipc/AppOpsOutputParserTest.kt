package eu.darken.sdmse.common.pkgs.pkgops.ipc

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AppOpsOutputParserTest : BaseTest() {

    @Test
    fun `explicit app op mode is parsed`() {
        AppOpsOutputParser.parse(
            key = "GET_USAGE_STATS",
            output = listOf(
                "Uid mode: default",
                "GET_USAGE_STATS: allow; time=+1h2m3s ago",
            ),
        ) shouldBe "allow"
    }

    @Test
    fun `default mode is parsed when operation has no explicit entry`() {
        AppOpsOutputParser.parse(
            key = "MANAGE_EXTERNAL_STORAGE",
            output = listOf(
                "No operations.",
                "Default mode: ignore",
            ),
        ) shouldBe "ignore"
    }

    @Test
    fun `unknown mode is rejected instead of guessed`() {
        AppOpsOutputParser.parse(
            key = "GET_USAGE_STATS",
            output = listOf("GET_USAGE_STATS: foreground"),
        ) shouldBe null
    }

    @Test
    fun `unrelated app op does not satisfy requested key`() {
        AppOpsOutputParser.parse(
            key = "GET_USAGE_STATS",
            output = listOf("CAMERA: allow"),
        ) shouldBe null
    }
}
