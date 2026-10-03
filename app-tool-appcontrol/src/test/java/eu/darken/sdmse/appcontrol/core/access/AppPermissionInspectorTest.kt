package eu.darken.sdmse.appcontrol.core.access

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AppPermissionInspectorTest : BaseTest() {

    private val pkgOps = mockk<PkgOps>()
    private val inspector = AppPermissionInspector(pkgOps)
    private val pkgId = Pkg.Id("com.test.app")
    private val userHandle = UserHandle2(10)
    private val installId = InstallId(pkgId, userHandle)

    @Test
    fun `inspect queries exact user with GET_PERMISSIONS and maps granted state deterministically`() = runTest {
        val packageInfo = PackageInfo().apply {
            packageName = pkgId.name
            requestedPermissions = arrayOf(
                "android.permission.Z_TEST",
                "android.permission.A_TEST",
            )
            requestedPermissionsFlags = intArrayOf(
                PackageInfo.REQUESTED_PERMISSION_GRANTED,
                0,
            )
        }
        coEvery {
            pkgOps.queryPkg(
                pkgId,
                PackageManager.GET_PERMISSIONS.toLong(),
                userHandle,
            )
        } returns packageInfo

        val result = inspector.inspect(installId)

        result?.installId shouldBe installId
        result?.permissions.shouldContainExactly(
            AppPermissionSnapshot.Entry("android.permission.A_TEST", granted = false),
            AppPermissionSnapshot.Entry("android.permission.Z_TEST", granted = true),
        )
        coVerify(exactly = 1) {
            pkgOps.queryPkg(
                pkgId,
                PackageManager.GET_PERMISSIONS.toLong(),
                userHandle,
            )
        }
    }

    @Test
    fun `missing permission flag fails closed as denied`() = runTest {
        val packageInfo = PackageInfo().apply {
            packageName = pkgId.name
            requestedPermissions = arrayOf(
                "android.permission.A",
                "android.permission.B",
            )
            requestedPermissionsFlags = intArrayOf(PackageInfo.REQUESTED_PERMISSION_GRANTED)
        }
        coEvery {
            pkgOps.queryPkg(pkgId, PackageManager.GET_PERMISSIONS.toLong(), userHandle)
        } returns packageInfo

        val result = inspector.inspect(installId)

        result?.permissions.shouldContainExactly(
            AppPermissionSnapshot.Entry("android.permission.A", granted = true),
            AppPermissionSnapshot.Entry("android.permission.B", granted = false),
        )
    }

    @Test
    fun `no requested permissions produces empty snapshot`() = runTest {
        val packageInfo = PackageInfo().apply {
            packageName = pkgId.name
            requestedPermissions = null
            requestedPermissionsFlags = null
        }
        coEvery {
            pkgOps.queryPkg(pkgId, PackageManager.GET_PERMISSIONS.toLong(), userHandle)
        } returns packageInfo

        val result = inspector.inspect(installId)

        result shouldBe AppPermissionSnapshot(installId, emptyList())
    }

    @Test
    fun `missing package returns null`() = runTest {
        coEvery {
            pkgOps.queryPkg(pkgId, PackageManager.GET_PERMISSIONS.toLong(), userHandle)
        } returns null

        inspector.inspect(installId) shouldBe null
    }
}
