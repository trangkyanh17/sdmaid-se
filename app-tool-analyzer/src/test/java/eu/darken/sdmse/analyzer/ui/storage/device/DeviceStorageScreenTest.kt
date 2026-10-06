package eu.darken.sdmse.analyzer.ui.storage.device

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import eu.darken.sdmse.analyzer.core.device.DeviceStorage
import eu.darken.sdmse.common.ca.toCaString
import eu.darken.sdmse.common.compose.preview.PreviewWrapper
import eu.darken.sdmse.common.compose.tour.GuidedTourController
import eu.darken.sdmse.common.compose.tour.LocalGuidedTourController
import eu.darken.sdmse.common.storage.StorageId
import eu.darken.sdmse.stats.core.db.SpaceSnapshotEntity
import eu.darken.sdmse.stats.core.forecast.StorageForecast
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test
import testhelpers.compose.BaseComposeRobolectricTest
import java.time.Instant
import java.util.UUID

class DeviceStorageScreenTest : BaseComposeRobolectricTest() {

    // Relaxed mock: shouldStart() defaults to false, so no guided tour starts.
    private val mockTourController: GuidedTourController = mockk(relaxed = true)

    private fun ComposeContentTestRule.setStorageScreen(
        state: DeviceStorageViewModel.State,
        onLowSpaceHintDismiss: () -> Unit = {},
        onLowSpaceHintUpgrade: () -> Unit = {},
    ) {
        setContent {
            CompositionLocalProvider(LocalGuidedTourController provides mockTourController) {
                PreviewWrapper {
                    DeviceStorageScreen(
                        stateSource = MutableStateFlow(state),
                        onLowSpaceHintDismiss = onLowSpaceHintDismiss,
                        onLowSpaceHintUpgrade = onLowSpaceHintUpgrade,
                    )
                }
            }
        }
    }

    @Test
    fun `the low space hint renders when not Pro and not dismissed`() {
        composeRule.setStorageScreen(DeviceStorageViewModel.State(showLowSpaceHint = true))

        composeRule.onNodeWithText("Never get caught out of space").assertExists()
    }

    @Test
    fun `the low space hint stays away when hidden`() {
        composeRule.setStorageScreen(DeviceStorageViewModel.State(showLowSpaceHint = false))

        composeRule.onNodeWithText("Never get caught out of space").assertDoesNotExist()
    }

    @Test
    fun `the hint's buttons are wired`() {
        var dismissals = 0
        var upgrades = 0
        composeRule.setStorageScreen(
            state = DeviceStorageViewModel.State(showLowSpaceHint = true),
            onLowSpaceHintDismiss = { dismissals++ },
            onLowSpaceHintUpgrade = { upgrades++ },
        )

        composeRule.onNodeWithText("Dismiss").performClick()
        composeRule.onNodeWithText("Upgrade").performClick()

        composeRule.runOnIdle {
            assertEquals(1, dismissals)
            assertEquals(1, upgrades)
        }
    }
    @Test
    fun `storage card renders forward forecast when filling`() {
        val storageId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val storage = DeviceStorage(
            id = StorageId(internalId = null, externalId = storageId),
            label = "Primary storage".toCaString(),
            type = DeviceStorage.Type.PRIMARY,
            hardware = DeviceStorage.Hardware.BUILT_IN,
            spaceCapacity = 100_000L,
            spaceFree = 20_000L,
            setupIncomplete = false,
        )
        val snapshots = listOf(
            SpaceSnapshotEntity(1, storageId.toString(), Instant.parse("2026-10-01T12:00:00Z"), 40_000L, 100_000L),
            SpaceSnapshotEntity(2, storageId.toString(), Instant.parse("2026-10-02T12:00:00Z"), 39_000L, 100_000L),
        )
        composeRule.setStorageScreen(
            DeviceStorageViewModel.State(
                storages = listOf(
                    DeviceStorageViewModel.Row(
                        storage = storage,
                        snapshots = snapshots,
                        forecast = StorageForecast.Filling(
                            daysUntilFloor = 6,
                            bytesPerDay = 1_000L,
                            isUrgent = true,
                        ),
                    )
                )
            )
        )

        composeRule.onNodeWithText("About 6 days of space left at this rate").assertExists()
    }

}
