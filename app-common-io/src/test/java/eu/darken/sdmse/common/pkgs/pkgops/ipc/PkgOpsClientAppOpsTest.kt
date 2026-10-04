package eu.darken.sdmse.common.pkgs.pkgops.ipc

import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class PkgOpsClientAppOpsTest : BaseTest() {

    private val connection = mockk<PkgOpsConnection>()
    private val client = PkgOpsClient(connection)
    private val installId = InstallId(Pkg.Id("com.example.app"), UserHandle2(10))

    @Test
    fun `query appops forwards exact package user and key`() {
        every {
            connection.getAppOpsMode("com.example.app", 10, "GET_USAGE_STATS")
        } returns "ignore"

        client.getAppOpsMode(installId, "GET_USAGE_STATS") shouldBe "ignore"

        verify(exactly = 1) {
            connection.getAppOpsMode("com.example.app", 10, "GET_USAGE_STATS")
        }
    }
}
