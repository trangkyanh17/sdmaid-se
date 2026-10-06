package eu.darken.sdmse.appcontrol.core.access

import eu.darken.sdmse.appcontrol.core.access.history.AppAccessHistoryDatabase
import eu.darken.sdmse.appcontrol.core.access.history.AppAccessOperation
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AppAccessControllerTest : BaseTest() {

    private val inspector = mockk<AppPermissionInspector>()
    private val pkgOps = mockk<PkgOps>()
    private val history = mockk<AppAccessHistoryDatabase>()
    private val controller = AppAccessController(inspector, pkgOps, history)
    private val installId = InstallId(Pkg.Id("com.example.app"), UserHandle2(10))

    @Test
    fun `grant records verified before and after state for exact target`() = runTest {
        val permissionId = "android.permission.CAMERA"
        val before = AppPermissionSnapshot(
            installId,
            listOf(AppPermissionSnapshot.Entry(permissionId, granted = false, runtimeMutable = true)),
        )
        val after = AppPermissionSnapshot(
            installId,
            listOf(AppPermissionSnapshot.Entry(permissionId, granted = true, runtimeMutable = true)),
        )
        coEvery { inspector.inspect(installId) } returnsMany listOf(before, after)
        coEvery { pkgOps.grantPermission(installId, permissionId) } returns true
        coEvery {
            history.record(
                installId = installId,
                kind = AppAccessOperation.Kind.RUNTIME_PERMISSION,
                subjectId = permissionId,
                before = AppAccessOperation.Value.Permission(false),
                after = AppAccessOperation.Value.Permission(true),
            )
        } returns mockk()

        controller.grantRuntimePermission(installId, permissionId) shouldBe
            AppAccessController.PermissionMutationResult(after)

        coVerify(exactly = 1) { pkgOps.grantPermission(installId, permissionId) }
        coVerify(exactly = 1) {
            history.record(
                installId = installId,
                kind = AppAccessOperation.Kind.RUNTIME_PERMISSION,
                subjectId = permissionId,
                before = AppAccessOperation.Value.Permission(false),
                after = AppAccessOperation.Value.Permission(true),
            )
        }
    }

    @Test
    fun `revoke records verified before and after state for exact target`() = runTest {
        val permissionId = "android.permission.RECORD_AUDIO"
        val before = AppPermissionSnapshot(
            installId,
            listOf(AppPermissionSnapshot.Entry(permissionId, granted = true, runtimeMutable = true)),
        )
        val after = AppPermissionSnapshot(
            installId,
            listOf(AppPermissionSnapshot.Entry(permissionId, granted = false, runtimeMutable = true)),
        )
        coEvery { inspector.inspect(installId) } returnsMany listOf(before, after)
        coEvery { pkgOps.revokePermission(installId, permissionId) } returns true
        coEvery { history.record(any(), any(), any(), any(), any()) } returns mockk()

        controller.revokeRuntimePermission(installId, permissionId) shouldBe
            AppAccessController.PermissionMutationResult(after)

        coVerify(exactly = 1) { pkgOps.revokePermission(installId, permissionId) }
        coVerify(exactly = 1) {
            history.record(
                installId = installId,
                kind = AppAccessOperation.Kind.RUNTIME_PERMISSION,
                subjectId = permissionId,
                before = AppAccessOperation.Value.Permission(true),
                after = AppAccessOperation.Value.Permission(false),
            )
        }
    }

    @Test
    fun `non runtime permission is rejected without backend mutation or history`() = runTest {
        val permissionId = "android.permission.INTERNET"
        coEvery { inspector.inspect(installId) } returns AppPermissionSnapshot(
            installId,
            listOf(AppPermissionSnapshot.Entry(permissionId, granted = true, runtimeMutable = false)),
        )

        controller.revokeRuntimePermission(installId, permissionId) shouldBe null

        coVerify(exactly = 0) { pkgOps.revokePermission(any(), any<String>()) }
        coVerify(exactly = 0) { pkgOps.grantPermission(any(), any<String>()) }
        coVerify(exactly = 0) { history.record(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `permission absent from current snapshot is rejected`() = runTest {
        coEvery { inspector.inspect(installId) } returns AppPermissionSnapshot(
            installId,
            emptyList(),
        )

        controller.grantRuntimePermission(installId, "android.permission.CAMERA") shouldBe null

        coVerify(exactly = 0) { pkgOps.grantPermission(any(), any<String>()) }
        coVerify(exactly = 0) { history.record(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `permission verification mismatch never fabricates a history row`() = runTest {
        val permissionId = "android.permission.CAMERA"
        val before = AppPermissionSnapshot(
            installId,
            listOf(AppPermissionSnapshot.Entry(permissionId, granted = false, runtimeMutable = true)),
        )
        val stillDenied = before.copy()
        coEvery { inspector.inspect(installId) } returnsMany listOf(before, stillDenied)
        coEvery { pkgOps.grantPermission(installId, permissionId) } returns true

        shouldThrow<IllegalStateException> {
            controller.grantRuntimePermission(installId, permissionId)
        }

        coVerify(exactly = 0) { history.record(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `history persistence failure is surfaced with verified permission state`() = runTest {
        val permissionId = "android.permission.CAMERA"
        val failure = IllegalStateException("history unavailable")
        val before = AppPermissionSnapshot(
            installId,
            listOf(AppPermissionSnapshot.Entry(permissionId, granted = false, runtimeMutable = true)),
        )
        val after = AppPermissionSnapshot(
            installId,
            listOf(AppPermissionSnapshot.Entry(permissionId, granted = true, runtimeMutable = true)),
        )
        coEvery { inspector.inspect(installId) } returnsMany listOf(before, after)
        coEvery { pkgOps.grantPermission(installId, permissionId) } returns true
        coEvery { history.record(any(), any(), any(), any(), any()) } throws failure

        controller.grantRuntimePermission(installId, permissionId) shouldBe
            AppAccessController.PermissionMutationResult(after, historyError = failure)
    }

    @Test
    fun `history cancellation is preserved`() = runTest {
        val permissionId = "android.permission.CAMERA"
        val before = AppPermissionSnapshot(
            installId,
            listOf(AppPermissionSnapshot.Entry(permissionId, granted = false, runtimeMutable = true)),
        )
        val after = AppPermissionSnapshot(
            installId,
            listOf(AppPermissionSnapshot.Entry(permissionId, granted = true, runtimeMutable = true)),
        )
        coEvery { inspector.inspect(installId) } returnsMany listOf(before, after)
        coEvery { pkgOps.grantPermission(installId, permissionId) } returns true
        coEvery { history.record(any(), any(), any(), any(), any()) } throws CancellationException("cancel")

        shouldThrow<CancellationException> {
            controller.grantRuntimePermission(installId, permissionId)
        }
    }

    @Test
    fun `set appop records actual before and after states`() = runTest {
        val key = PkgOps.AppOpsKey.GET_USAGE_STATS
        val value = PkgOps.AppOpsValue.IGNORE
        var targetQueries = 0
        coEvery { pkgOps.queryAppOps(installId, any()) } answers {
            when (secondArg<PkgOps.AppOpsKey>()) {
                key -> if (targetQueries++ == 0) PkgOps.AppOpsValue.DEFAULT else value
                else -> PkgOps.AppOpsValue.ALLOW
            }
        }
        coEvery { pkgOps.setAppOps(installId, key, value) } returns true
        coEvery { history.record(any(), any(), any(), any(), any()) } returns mockk()

        val result = controller.setAppOp(installId, key, value)

        result shouldBe result?.copy(historyError = null)
        result!!.appOps.single { it.key == key }.value shouldBe value
        coVerify(exactly = 1) { pkgOps.setAppOps(installId, key, value) }
        coVerify(exactly = 1) {
            history.record(
                installId = installId,
                kind = AppAccessOperation.Kind.APP_OP,
                subjectId = key.name,
                before = AppAccessOperation.Value.AppOp(PkgOps.AppOpsValue.DEFAULT),
                after = AppAccessOperation.Value.AppOp(value),
            )
        }
    }

    @Test
    fun `setting appop to current value is rejected without mutation or history`() = runTest {
        val key = PkgOps.AppOpsKey.GET_USAGE_STATS
        coEvery { pkgOps.queryAppOps(installId, key) } returns PkgOps.AppOpsValue.DEFAULT

        controller.setAppOp(installId, key, PkgOps.AppOpsValue.DEFAULT) shouldBe null

        coVerify(exactly = 0) { pkgOps.setAppOps(any(), any(), any()) }
        coVerify(exactly = 0) { history.record(any(), any(), any(), any(), any()) }
    }
}
