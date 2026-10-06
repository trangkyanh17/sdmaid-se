package eu.darken.sdmse.analyzer.ui.storage.device

import eu.darken.sdmse.analyzer.core.Analyzer
import eu.darken.sdmse.analyzer.core.AnalyzerSettings
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.datastore.DataStoreValue
import eu.darken.sdmse.common.navigation.NavEvent
import eu.darken.sdmse.common.navigation.routes.UpgradeRoute
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.common.upgrade.UpgradeRepo
import eu.darken.sdmse.stats.core.SpaceHistoryRepo
import eu.darken.sdmse.stats.core.db.SpaceSnapshotEntity
import eu.darken.sdmse.stats.core.forecast.StorageForecast
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import testhelpers.coroutine.runTest2

class DeviceStorageViewModelTest : BaseTest() {

    private fun <T> rwDataStoreValue(initial: T, flow: Flow<T> = flowOf(initial)): DataStoreValue<T> =
        mockk<DataStoreValue<T>>().apply {
            every { this@apply.flow } returns flow
            coEvery { update(any()) } returns DataStoreValue.Updated(old = initial, new = initial)
        }

    private class Harness(
        val vm: DeviceStorageViewModel,
        val hintDismissed: DataStoreValue<Boolean>,
    )

    private fun TestScope.harness(
        isPro: Boolean = false,
        hintDismissed: Boolean = false,
    ): Harness {
        val dismissed = rwDataStoreValue(hintDismissed)
        val analyzer = mockk<Analyzer>(relaxed = true).apply {
            every { data } returns MutableStateFlow(Analyzer.Data())
            every { progress } returns MutableStateFlow(null)
        }
        val settings = mockk<AnalyzerSettings>().apply {
            every { hintLowSpaceDismissed } returns dismissed
            every { lowStorageThresholdBytes } returns rwDataStoreValue(null)
        }
        val info = mockk<UpgradeRepo.Info>().apply {
            every { this@apply.isPro } returns isPro
        }
        val vm = DeviceStorageViewModel(
            dispatcherProvider = TestDispatcherProvider(),
            analyzer = analyzer,
            analyzerSettings = settings,
            spaceHistoryRepo = mockk<SpaceHistoryRepo>().apply {
                every { getAllHistory(any()) } returns flowOf(emptyList())
            },
            upgradeRepo = mockk<UpgradeRepo>().apply { every { upgradeInfo } returns flowOf(info) },
        )
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { vm.state.collect { } }
        return Harness(vm, dismissed)
    }

    @Test
    fun `the hint shows for a non-Pro user who has not dismissed it`() = runTest2 {
        val h = harness(isPro = false, hintDismissed = false)
        advanceUntilIdle()

        h.vm.state.first().showLowSpaceHint shouldBe true
    }

    @Test
    fun `the hint stays hidden for a Pro user`() = runTest2 {
        val h = harness(isPro = true, hintDismissed = false)
        advanceUntilIdle()

        h.vm.state.first().showLowSpaceHint shouldBe false
    }

    @Test
    fun `the hint stays hidden once dismissed`() = runTest2 {
        val h = harness(isPro = false, hintDismissed = true)
        advanceUntilIdle()

        h.vm.state.first().showLowSpaceHint shouldBe false
    }

    @Test
    fun `dismissing writes the flag through`() = runTest2 {
        val h = harness()
        advanceUntilIdle()

        h.vm.dismissLowSpaceHint()
        advanceUntilIdle()

        val captured = slot<(Boolean) -> Boolean?>()
        coVerify(exactly = 1) { h.hintDismissed.update(capture(captured)) }
        captured.captured(false) shouldBe true
    }

    @Test
    fun `the hint's upgrade button navigates to the upgrade screen`() = runTest2 {
        val h = harness()
        val events = mutableListOf<NavEvent>()
        // Foreground scope on purpose: advanceUntilIdle() stops as soon as no FOREGROUND event is
        // queued, so a collector in backgroundScope would never be resumed to receive the emission.
        val job = launch(start = CoroutineStart.UNDISPATCHED) { h.vm.navEvents.collect { events += it } }

        h.vm.openUpgrade()
        advanceUntilIdle()

        (events.single() as NavEvent.GoTo).destination shouldBe UpgradeRoute()
        job.cancel()
    }
    private fun forecastStorage(
        id: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111"),
        free: Long = 20_000L,
        capacity: Long = 100_000L,
    ) = DeviceStorage(
        id = StorageId(internalId = null, externalId = id),
        label = "Test storage".toCaString(),
        type = DeviceStorage.Type.PRIMARY,
        hardware = DeviceStorage.Hardware.BUILT_IN,
        spaceCapacity = capacity,
        spaceFree = free,
        setupIncomplete = false,
    )

    private fun fillingHistory(
        storageId: String,
        capacity: Long = 100_000L,
    ): List<SpaceSnapshotEntity> {
        val base = Instant.parse("2026-10-01T12:00:00Z")
        return (0L..4L).map { day ->
            SpaceSnapshotEntity(
                id = day,
                storageId = storageId,
                recordedAt = base.plus(Duration.ofDays(day)),
                spaceFree = 40_000L - day * 1_000L,
                spaceCapacity = capacity,
            )
        }
    }

    @Test
    fun `per-storage forecast ignores history from other storage ids`() {
        val storage = forecastStorage()
        val ownId = storage.id.externalId.toString()
        val foreignId = "22222222-2222-2222-2222-222222222222"
        val foreign = fillingHistory(foreignId).mapIndexed { index, row ->
            row.copy(
                id = 100L + index,
                spaceFree = 10_000L + index * 5_000L,
            )
        }

        DeviceStorageViewModel.forecastFor(
            storage = storage,
            snapshots = fillingHistory(ownId) + foreign,
            customThresholdBytes = null,
        ) shouldBe StorageForecast.Filling(
            daysUntilFloor = 15,
            bytesPerDay = 1_000L,
            isUrgent = false,
        )
    }

    @Test
    fun `custom low-space threshold recomputes the per-storage forecast`() {
        val storage = forecastStorage()
        val history = fillingHistory(storage.id.externalId.toString())

        DeviceStorageViewModel.forecastFor(
            storage = storage,
            snapshots = history,
            customThresholdBytes = 10_000L,
        ) shouldBe StorageForecast.Filling(
            daysUntilFloor = 10,
            bytesPerDay = 1_000L,
            isUrgent = true,
        )
    }

}
