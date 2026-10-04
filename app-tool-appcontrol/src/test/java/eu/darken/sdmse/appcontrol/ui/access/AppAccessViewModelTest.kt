package eu.darken.sdmse.appcontrol.ui.access

import eu.darken.sdmse.appcontrol.core.access.AppPermissionInspector
import eu.darken.sdmse.appcontrol.core.access.AppPermissionSnapshot
import eu.darken.sdmse.appcontrol.ui.AppAccessRoute
import eu.darken.sdmse.common.pkgs.Pkg
import eu.darken.sdmse.common.pkgs.features.InstallId
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
        val vm = AppAccessViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            inspector = inspector,
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
        val vm = AppAccessViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            inspector = inspector,
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
        val vm = AppAccessViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            inspector = inspector,
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
        val vm = AppAccessViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            inspector = inspector,
        )

        vm.bindRoute(AppAccessRoute(first))
        vm.bindRoute(AppAccessRoute(second))
        advanceUntilIdle()

        vm.state.value shouldBe AppAccessViewModel.State.Ready(firstSnapshot)
        coVerify(exactly = 1) { inspector.inspect(first) }
        coVerify(exactly = 0) { inspector.inspect(second) }
    }
}
