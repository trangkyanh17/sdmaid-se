package eu.darken.sdmse.appcontrol.ui.access

import eu.darken.sdmse.appcontrol.core.access.AppAccessController
import eu.darken.sdmse.appcontrol.core.access.AppOpEntry
import eu.darken.sdmse.appcontrol.core.access.AppPermissionInspector
import eu.darken.sdmse.appcontrol.core.access.AppPermissionSnapshot
import eu.darken.sdmse.appcontrol.ui.AppAccessRoute
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import eu.darken.sdmse.common.user.UserHandle2
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class AppAccessViewModelTest : BaseTest() {

    private fun installId(name: String, userId: Int = 0) =
        InstallId(Pkg.Id(name), UserHandle2(userId))

    @Test
    fun `bindRoute loads exact target and reaches Ready`() = runTest2 {
        val id = installId("com.example.app", userId = 10)
        val snapshot = AppPermissionSnapshot(
            installId = id,
            permissions = listOf(
                AppPermissionSnapshot.Entry("android.permission.CAMERA", granted = true),
            ),
        )
        val inspector = mockk<AppPermissionInspector>()
        coEvery { inspector.inspect(id) } returns snapshot
        val controller = mockk<AppAccessController>(relaxed = true)
        val vm = AppAccessViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            inspector = inspector,
            controller = controller,
        )

        vm.bindRoute(AppAccessRoute(id))
        advanceUntilIdle()

        vm.state.value shouldBe AppAccessViewModel.State.Ready(snapshot)
        coVerify(exactly = 1) { inspector.inspect(id) }
    }

    @Test
    fun `missing package reaches NotFound`() = runTest2 {
        val id = installId("com.example.missing")
        val inspector = mockk<AppPermissionInspector>()
        coEvery { inspector.inspect(id) } returns null
        val controller = mockk<AppAccessController>(relaxed = true)
        val vm = AppAccessViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            inspector = inspector,
            controller = controller,
        )

        vm.bindRoute(AppAccessRoute(id))
        advanceUntilIdle()

        vm.state.value shouldBe AppAccessViewModel.State.NotFound
        coVerify(exactly = 1) { inspector.inspect(id) }
    }

    @Test
    fun `inspection failure leaves explicit Error state`() = runTest2 {
        val id = installId("com.example.failure", userId = 10)
        val failure = IllegalStateException("cross-user access unavailable")
        val inspector = mockk<AppPermissionInspector>()
        coEvery { inspector.inspect(id) } throws failure
        val controller = mockk<AppAccessController>(relaxed = true)
        val vm = AppAccessViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            inspector = inspector,
            controller = controller,
        )

        val errors = mutableListOf<Throwable>()
        val errorJob = launch(start = CoroutineStart.UNDISPATCHED) {
            vm.errorEvents.collect { errors.add(it) }
        }

        vm.bindRoute(AppAccessRoute(id))
        advanceUntilIdle()

        vm.state.value shouldBe AppAccessViewModel.State.Error(failure)
        errors shouldBe listOf(failure)
        errorJob.cancel()
    }

    @Test
    fun `first route binding wins`() = runTest2 {
        val first = installId("com.example.first", userId = 10)
        val second = installId("com.example.second", userId = 11)
        val firstSnapshot = AppPermissionSnapshot(first, emptyList())
        val inspector = mockk<AppPermissionInspector>()
        coEvery { inspector.inspect(first) } returns firstSnapshot
        coEvery { inspector.inspect(second) } returns AppPermissionSnapshot(second, emptyList())
        val controller = mockk<AppAccessController>(relaxed = true)
        val vm = AppAccessViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            inspector = inspector,
            controller = controller,
        )

        vm.bindRoute(AppAccessRoute(first))
        vm.bindRoute(AppAccessRoute(second))
        advanceUntilIdle()

        vm.state.value shouldBe AppAccessViewModel.State.Ready(firstSnapshot)
        coVerify(exactly = 1) { inspector.inspect(first) }
        coVerify(exactly = 0) { inspector.inspect(second) }
    }
    @Test
    fun `request on denied runtime permission creates grant confirmation`() = runTest2 {
        val id = installId("com.example.app", userId = 10)
        val permissionId = "android.permission.CAMERA"
        val snapshot = AppPermissionSnapshot(
            id,
            listOf(AppPermissionSnapshot.Entry(permissionId, granted = false, runtimeMutable = true)),
        )
        val inspector = mockk<AppPermissionInspector>()
        val controller = mockk<AppAccessController>(relaxed = true)
        coEvery { inspector.inspect(id) } returns snapshot
        val vm = AppAccessViewModel(TestDispatcherProvider(), inspector, controller)

        vm.bindRoute(AppAccessRoute(id))
        advanceUntilIdle()
        vm.requestPermissionMutation(permissionId)

        vm.state.value shouldBe AppAccessViewModel.State.Ready(
            snapshot = snapshot,
            pendingMutation = AppAccessViewModel.PermissionMutation(
                permissionId,
                AppAccessViewModel.PermissionAction.GRANT,
            ),
        )
    }

    @Test
    fun `request on granted runtime permission creates revoke confirmation`() = runTest2 {
        val id = installId("com.example.app", userId = 10)
        val permissionId = "android.permission.RECORD_AUDIO"
        val snapshot = AppPermissionSnapshot(
            id,
            listOf(AppPermissionSnapshot.Entry(permissionId, granted = true, runtimeMutable = true)),
        )
        val inspector = mockk<AppPermissionInspector>()
        val controller = mockk<AppAccessController>(relaxed = true)
        coEvery { inspector.inspect(id) } returns snapshot
        val vm = AppAccessViewModel(TestDispatcherProvider(), inspector, controller)

        vm.bindRoute(AppAccessRoute(id))
        advanceUntilIdle()
        vm.requestPermissionMutation(permissionId)

        vm.state.value shouldBe AppAccessViewModel.State.Ready(
            snapshot = snapshot,
            pendingMutation = AppAccessViewModel.PermissionMutation(
                permissionId,
                AppAccessViewModel.PermissionAction.REVOKE,
            ),
        )
    }

    @Test
    fun `non runtime permission cannot create mutation confirmation`() = runTest2 {
        val id = installId("com.example.app", userId = 10)
        val permissionId = "android.permission.INTERNET"
        val snapshot = AppPermissionSnapshot(
            id,
            listOf(AppPermissionSnapshot.Entry(permissionId, granted = true, runtimeMutable = false)),
        )
        val inspector = mockk<AppPermissionInspector>()
        val controller = mockk<AppAccessController>(relaxed = true)
        coEvery { inspector.inspect(id) } returns snapshot
        val vm = AppAccessViewModel(TestDispatcherProvider(), inspector, controller)

        vm.bindRoute(AppAccessRoute(id))
        advanceUntilIdle()
        vm.requestPermissionMutation(permissionId)

        vm.state.value shouldBe AppAccessViewModel.State.Ready(snapshot)
    }

    @Test
    fun `confirm grant mutates exact permission then reloads actual state`() = runTest2 {
        val id = installId("com.example.app", userId = 10)
        val permissionId = "android.permission.CAMERA"
        val before = AppPermissionSnapshot(
            id,
            listOf(AppPermissionSnapshot.Entry(permissionId, granted = false, runtimeMutable = true)),
        )
        val after = AppPermissionSnapshot(
            id,
            listOf(AppPermissionSnapshot.Entry(permissionId, granted = true, runtimeMutable = true)),
        )
        val inspector = mockk<AppPermissionInspector>()
        val controller = mockk<AppAccessController>()
        coEvery { inspector.inspect(id) } returnsMany listOf(before, after)
        coEvery { controller.grantRuntimePermission(id, permissionId) } returns true
        val vm = AppAccessViewModel(TestDispatcherProvider(), inspector, controller)

        vm.bindRoute(AppAccessRoute(id))
        advanceUntilIdle()
        vm.requestPermissionMutation(permissionId)
        vm.confirmPermissionMutation()
        advanceUntilIdle()

        vm.state.value shouldBe AppAccessViewModel.State.Ready(after)
        coVerify(exactly = 1) { controller.grantRuntimePermission(id, permissionId) }
        coVerify(exactly = 2) { inspector.inspect(id) }
    }

    @Test
    fun `confirm revoke mutates exact permission then reloads actual state`() = runTest2 {
        val id = installId("com.example.app", userId = 10)
        val permissionId = "android.permission.RECORD_AUDIO"
        val before = AppPermissionSnapshot(
            id,
            listOf(AppPermissionSnapshot.Entry(permissionId, granted = true, runtimeMutable = true)),
        )
        val after = AppPermissionSnapshot(
            id,
            listOf(AppPermissionSnapshot.Entry(permissionId, granted = false, runtimeMutable = true)),
        )
        val inspector = mockk<AppPermissionInspector>()
        val controller = mockk<AppAccessController>()
        coEvery { inspector.inspect(id) } returnsMany listOf(before, after)
        coEvery { controller.revokeRuntimePermission(id, permissionId) } returns true
        val vm = AppAccessViewModel(TestDispatcherProvider(), inspector, controller)

        vm.bindRoute(AppAccessRoute(id))
        advanceUntilIdle()
        vm.requestPermissionMutation(permissionId)
        vm.confirmPermissionMutation()
        advanceUntilIdle()

        vm.state.value shouldBe AppAccessViewModel.State.Ready(after)
        coVerify(exactly = 1) { controller.revokeRuntimePermission(id, permissionId) }
        coVerify(exactly = 2) { inspector.inspect(id) }
    }
    @Test
    fun `binding also loads appops for exact target`() = runTest2 {
        val id = installId("com.example.app", userId = 10)
        val permissionSnapshot = AppPermissionSnapshot(id, emptyList())
        val appOps = listOf(
            AppOpEntry(PkgOps.AppOpsKey.GET_USAGE_STATS, PkgOps.AppOpsValue.ALLOW),
        )
        val inspector = mockk<AppPermissionInspector>()
        val controller = mockk<AppAccessController>()
        coEvery { inspector.inspect(id) } returns permissionSnapshot
        coEvery { controller.queryAppOps(id) } returns appOps
        val vm = AppAccessViewModel(TestDispatcherProvider(), inspector, controller)

        vm.bindRoute(AppAccessRoute(id))
        advanceUntilIdle()

        vm.state.value shouldBe AppAccessViewModel.State.Ready(
            snapshot = permissionSnapshot,
            appOps = appOps,
        )
        coVerify(exactly = 1) { controller.queryAppOps(id) }
    }

    @Test
    fun `appops query failure preserves permission screen and marks appops unavailable`() = runTest2 {
        val id = installId("com.example.app", userId = 10)
        val permissionSnapshot = AppPermissionSnapshot(id, emptyList())
        val failure = IllegalStateException("appops unavailable")
        val inspector = mockk<AppPermissionInspector>()
        val controller = mockk<AppAccessController>()
        coEvery { inspector.inspect(id) } returns permissionSnapshot
        coEvery { controller.queryAppOps(id) } throws failure
        val vm = AppAccessViewModel(TestDispatcherProvider(), inspector, controller)

        vm.bindRoute(AppAccessRoute(id))
        advanceUntilIdle()

        vm.state.value shouldBe AppAccessViewModel.State.Ready(
            snapshot = permissionSnapshot,
            appOpsError = failure,
        )
    }

    @Test
    fun `selecting appop value sets exact key then requeries actual modes`() = runTest2 {
        val id = installId("com.example.app", userId = 10)
        val permissionSnapshot = AppPermissionSnapshot(id, emptyList())
        val before = listOf(
            AppOpEntry(PkgOps.AppOpsKey.GET_USAGE_STATS, PkgOps.AppOpsValue.DEFAULT),
        )
        val after = listOf(
            AppOpEntry(PkgOps.AppOpsKey.GET_USAGE_STATS, PkgOps.AppOpsValue.IGNORE),
        )
        val inspector = mockk<AppPermissionInspector>()
        val controller = mockk<AppAccessController>()
        coEvery { inspector.inspect(id) } returns permissionSnapshot
        coEvery { controller.queryAppOps(id) } returnsMany listOf(before, after)
        coEvery {
            controller.setAppOp(
                id,
                PkgOps.AppOpsKey.GET_USAGE_STATS,
                PkgOps.AppOpsValue.IGNORE,
            )
        } returns true
        val vm = AppAccessViewModel(TestDispatcherProvider(), inspector, controller)

        vm.bindRoute(AppAccessRoute(id))
        advanceUntilIdle()
        vm.requestAppOpMutation(PkgOps.AppOpsKey.GET_USAGE_STATS)
        vm.selectAppOpValue(PkgOps.AppOpsValue.IGNORE)
        advanceUntilIdle()

        vm.state.value shouldBe AppAccessViewModel.State.Ready(
            snapshot = permissionSnapshot,
            appOps = after,
        )
        coVerify(exactly = 1) {
            controller.setAppOp(
                id,
                PkgOps.AppOpsKey.GET_USAGE_STATS,
                PkgOps.AppOpsValue.IGNORE,
            )
        }
        coVerify(exactly = 2) { controller.queryAppOps(id) }
    }
}
