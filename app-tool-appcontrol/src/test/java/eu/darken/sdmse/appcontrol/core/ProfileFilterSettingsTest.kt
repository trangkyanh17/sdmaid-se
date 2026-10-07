package eu.darken.sdmse.appcontrol.core

import eu.darken.sdmse.common.serialization.SerializationIOModule
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.json.toComparableJson

class ProfileFilterSettingsTest : BaseTest() {

    private val json: Json = SerializationIOModule().json()

    @Test
    fun `serialize default matches golden JSON`() {
        val rawJson = json.encodeToString(ProfileFilterSettings.serializer(), ProfileFilterSettings())

        rawJson.toComparableJson() shouldBe """
            {
                "scope": "ALL"
            }
        """.toComparableJson()
    }

    @Test
    fun `round trip preserves every scope`() {
        ProfileFilterSettings.Scope.entries.forEach { scope ->
            val original = ProfileFilterSettings(scope)
            val rawJson = json.encodeToString(ProfileFilterSettings.serializer(), original)
            json.decodeFromString(ProfileFilterSettings.serializer(), rawJson) shouldBe original
        }
    }
}
