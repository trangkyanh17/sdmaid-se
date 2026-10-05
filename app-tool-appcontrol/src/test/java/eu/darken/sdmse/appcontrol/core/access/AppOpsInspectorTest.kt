package eu.darken.sdmse.appcontrol.core.access

import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.collections.shouldContainExactly
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AppOpsInspectorTest : BaseTest() {

    @Test
    fun `inspect queries all declared keys for exact install id in enum order`() = runTest {
        val id = InstallId(Pkg.Id("com.example.app"), UserHandle2(10))
        val pkgOps = mockk<PkgOps>()

        coEvery { pkgOps.queryAppOps(id, PkgOps.AppOpsKey.GET_USAGE_STATS) } returns PkgOps.AppOpsValue.ALLOW
        coEvery { pkgOps.queryAppOps(id, PkgOps.AppOpsKey.MANAGE_EXTERNAL_STORAGE) } returns PkgOps.AppOpsValue.IGNORE
        coEvery { pkgOps.queryAppOps(id, PkgOps.AppOpsKey.ACCESS_RESTRICTED_SETTINGS) } returns PkgOps.AppOpsValue.DEFAULT

        AppOpsInspector.inspect(pkgOps, id, apiLevel = 36).shouldContainExactly(
            AppOpEntry(PkgOps.AppOpsKey.GET_USAGE_STATS, PkgOps.AppOpsValue.ALLOW),
            AppOpEntry(PkgOps.AppOpsKey.MANAGE_EXTERNAL_STORAGE, PkgOps.AppOpsValue.IGNORE),
            AppOpEntry(PkgOps.AppOpsKey.ACCESS_RESTRICTED_SETTINGS, PkgOps.AppOpsValue.DEFAULT),
        )

        PkgOps.AppOpsKey.entries.forEach { key ->
            coVerify(exactly = 1) { pkgOps.queryAppOps(id, key) }
        }
    }
    @Test
    fun `older Android skips unavailable appops keys`() = runTest {
        val id = InstallId(Pkg.Id("com.example.app"), UserHandle2(10))
        val pkgOps = mockk<PkgOps>()
        coEvery {
            pkgOps.queryAppOps(id, PkgOps.AppOpsKey.GET_USAGE_STATS)
        } returns PkgOps.AppOpsValue.DEFAULT

        AppOpsInspector.inspect(pkgOps, id, apiLevel = 29).shouldContainExactly(
            AppOpEntry(PkgOps.AppOpsKey.GET_USAGE_STATS, PkgOps.AppOpsValue.DEFAULT),
        )

        coVerify(exactly = 1) {
            pkgOps.queryAppOps(id, PkgOps.AppOpsKey.GET_USAGE_STATS)
        }
        coVerify(exactly = 0) {
            pkgOps.queryAppOps(id, PkgOps.AppOpsKey.MANAGE_EXTERNAL_STORAGE)
        }
        coVerify(exactly = 0) {
            pkgOps.queryAppOps(id, PkgOps.AppOpsKey.ACCESS_RESTRICTED_SETTINGS)
        }
    }
}
