package eu.darken.sdmse.appcontrol.core.access

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PermissionInfo
import android.content.pm.PackageManager
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AppPermissionInspectorTest : BaseTest() {

    private val pkgOps = mockk<PkgOps>()
    private val context = mockk<Context>()
    private val packageManager = mockk<PackageManager>()
    private val inspector = AppPermissionInspector(pkgOps, context)

    init {
        every { context.packageManager } returns packageManager
    }
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
            AppPermissionSnapshot.Entry("android.permission.A_TEST", granted = false, runtimeMutable = false),
            AppPermissionSnapshot.Entry("android.permission.Z_TEST", granted = true, runtimeMutable = false),
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
            AppPermissionSnapshot.Entry("android.permission.A", granted = true, runtimeMutable = false),
            AppPermissionSnapshot.Entry("android.permission.B", granted = false, runtimeMutable = false),
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
    fun `dangerous permission is marked runtime mutable while normal and unknown stay read only`() = runTest {
        val dangerous = "android.permission.CAMERA"
        val normal = "android.permission.INTERNET"
        val unknown = "com.example.UNKNOWN"

        val packageInfo = PackageInfo().apply {
            packageName = pkgId.name
            requestedPermissions = arrayOf(dangerous, normal, unknown)
            requestedPermissionsFlags = intArrayOf(
                PackageInfo.REQUESTED_PERMISSION_GRANTED,
                PackageInfo.REQUESTED_PERMISSION_GRANTED,
                0,
            )
        }
        coEvery {
            pkgOps.queryPkg(pkgId, PackageManager.GET_PERMISSIONS.toLong(), userHandle)
        } returns packageInfo
        every { packageManager.getPermissionInfo(dangerous, 0) } returns PermissionInfo().apply {
            name = dangerous
            protectionLevel = PermissionInfo.PROTECTION_DANGEROUS
        }
        every { packageManager.getPermissionInfo(normal, 0) } returns PermissionInfo().apply {
            name = normal
            protectionLevel = PermissionInfo.PROTECTION_NORMAL
        }
        every { packageManager.getPermissionInfo(unknown, 0) } throws PackageManager.NameNotFoundException()

        val result = inspector.inspect(installId)

        result?.permissions.shouldContainExactly(
            AppPermissionSnapshot.Entry(dangerous, granted = true, runtimeMutable = true),
            AppPermissionSnapshot.Entry(normal, granted = true, runtimeMutable = false),
            AppPermissionSnapshot.Entry(unknown, granted = false, runtimeMutable = false),
        )
    }

    @Test
    fun `missing package returns null`() = runTest {
        coEvery {
            pkgOps.queryPkg(pkgId, PackageManager.GET_PERMISSIONS.toLong(), userHandle)
        } returns null

        inspector.inspect(installId) shouldBe null
    }
}
