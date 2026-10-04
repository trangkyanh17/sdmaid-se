package eu.darken.sdmse.common.pkgs.pkgops.ipc

import eu.darken.sdmse.common.permissions.Permission
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class PkgOpsClientPermissionTest : BaseTest() {

    private val connection = mockk<PkgOpsConnection>()
    private val client = PkgOpsClient(connection)
    private val installId = InstallId(Pkg.Id("com.example.app"), UserHandle2(10))

    @Test
    fun `raw grant forwards exact package user and permission id`() {
        val permissionId = "android.permission.CAMERA"
        every { connection.grantPermission("com.example.app", 10, permissionId) } returns true

        client.grantPermission(installId, permissionId) shouldBe true

        verify(exactly = 1) {
            connection.grantPermission("com.example.app", 10, permissionId)
        }
    }

    @Test
    fun `raw revoke forwards exact package user and permission id`() {
        val permissionId = "android.permission.RECORD_AUDIO"
        every { connection.revokePermission("com.example.app", 10, permissionId) } returns true

        client.revokePermission(installId, permissionId) shouldBe true

        verify(exactly = 1) {
            connection.revokePermission("com.example.app", 10, permissionId)
        }
    }

    @Test
    fun `typed permission overload remains compatible`() {
        every {
            connection.grantPermission(
                "com.example.app",
                10,
                Permission.POST_NOTIFICATIONS.permissionId,
            )
        } returns true

        client.grantPermission(installId, Permission.POST_NOTIFICATIONS) shouldBe true
    }
}
