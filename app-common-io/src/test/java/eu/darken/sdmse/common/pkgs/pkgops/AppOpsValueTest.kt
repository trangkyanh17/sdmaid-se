package eu.darken.sdmse.common.pkgs.pkgops

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AppOpsValueTest : BaseTest() {

    @Test
    fun `supported appops values expose canonical shell modes`() {
        PkgOps.AppOpsValue.entries.map { it.raw } shouldBe listOf(
            "allow",
            "ignore",
            "deny",
            "default",
        )
    }
}
