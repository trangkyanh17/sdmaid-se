package eu.darken.sdmse.appcontrol.core.access

import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AppAccessControllerTest : BaseTest() {

    private val inspector = mockk<AppPermissionInspector>()
    private val pkgOps = mockk<PkgOps>()
    private val controller = AppAccessController(inspector, pkgOps)
    private val installId = InstallId(Pkg.Id("com.example.app"), UserHandle2(10))

    @Test
    fun `grant validates runtime permission then forwards exact id and user`() = runTest {
        val permissionId = "android.permission.CAMERA"
        coEvery { inspector.inspect(installId) } returns AppPermissionSnapshot(
            installId,
            listOf(
                AppPermissionSnapshot.Entry(
                    name = permissionId,
                    granted = false,
                    runtimeMutable = true,
                ),
            ),
        )
        coEvery { pkgOps.grantPermission(installId, permissionId) } returns true

        controller.grantRuntimePermission(installId, permissionId) shouldBe true

        coVerify(exactly = 1) { pkgOps.grantPermission(installId, permissionId) }
    }

    @Test
    fun `revoke validates runtime permission then forwards exact id and user`() = runTest {
        val permissionId = "android.permission.RECORD_AUDIO"
        coEvery { inspector.inspect(installId) } returns AppPermissionSnapshot(
            installId,
            listOf(
                AppPermissionSnapshot.Entry(
                    name = permissionId,
                    granted = true,
                    runtimeMutable = true,
                ),
            ),
        )
        coEvery { pkgOps.revokePermission(installId, permissionId) } returns true

        controller.revokeRuntimePermission(installId, permissionId) shouldBe true

        coVerify(exactly = 1) { pkgOps.revokePermission(installId, permissionId) }
    }

    @Test
    fun `non runtime permission is rejected without backend mutation`() = runTest {
        val permissionId = "android.permission.INTERNET"
        coEvery { inspector.inspect(installId) } returns AppPermissionSnapshot(
            installId,
            listOf(
                AppPermissionSnapshot.Entry(
                    name = permissionId,
                    granted = true,
                    runtimeMutable = false,
                ),
            ),
        )

        controller.revokeRuntimePermission(installId, permissionId) shouldBe false

        coVerify(exactly = 0) { pkgOps.revokePermission(any(), any<String>()) }
        coVerify(exactly = 0) { pkgOps.grantPermission(any(), any<String>()) }
    }

    @Test
    fun `permission absent from current snapshot is rejected`() = runTest {
        coEvery { inspector.inspect(installId) } returns AppPermissionSnapshot(
            installId,
            emptyList(),
        )

        controller.grantRuntimePermission(installId, "android.permission.CAMERA") shouldBe false

        coVerify(exactly = 0) { pkgOps.grantPermission(any(), any<String>()) }
    }
    @Test
    fun `set appop forwards exact id key and value`() = runTest {
        val key = PkgOps.AppOpsKey.GET_USAGE_STATS
        val value = PkgOps.AppOpsValue.IGNORE
        coEvery { pkgOps.setAppOps(installId, key, value) } returns true

        controller.setAppOp(installId, key, value) shouldBe true

        coVerify(exactly = 1) { pkgOps.setAppOps(installId, key, value) }
    }
}
