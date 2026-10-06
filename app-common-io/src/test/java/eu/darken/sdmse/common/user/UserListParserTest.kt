package eu.darken.sdmse.common.user

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class UserListParserTest : BaseTest() {

    @Test
    fun `modern full owner keeps typed status and punctuation in name`() {
        val parsed = UserListParser.parse(
            listOf(
                "0: id=0, name=Owner, Main: phone, type=android.os.usertype.full.SYSTEM, " +
                    "flags=FULL|SYSTEM|ADMIN|MAIN (running) (current) (visible)"
            )
        ).single()

        parsed.id shouldBe 0
        parsed.name shouldBe "Owner, Main: phone"
        parsed.rawType shouldBe "android.os.usertype.full.SYSTEM"
        parsed.type shouldBe UserProfile2.Type.SYSTEM
        parsed.isRunning shouldBe true
        parsed.isCurrent shouldBe true
        parsed.isVisible shouldBe true
        parsed.isQuietMode shouldBe false
        parsed.flags shouldContainExactly setOf("FULL", "SYSTEM", "ADMIN", "MAIN")
    }

    @Test
    fun `modern managed profile is a work profile`() {
        val parsed = UserListParser.parse(
            listOf(
                "1: id=10, name=Work, type=android.os.usertype.profile.MANAGED, " +
                    "flags=PROFILE|MANAGED_PROFILE|INITIALIZED (running) (visible)"
            )
        ).single()

        parsed.id shouldBe 10
        parsed.type shouldBe UserProfile2.Type.WORK_PROFILE
        parsed.isRunning shouldBe true
        parsed.isVisible shouldBe true
    }

    @Test
    fun `modern private space stays distinct and can be quiet`() {
        val parsed = UserListParser.parse(
            listOf(
                "2: id=11, name=Private, type=android.os.usertype.profile.PRIVATE, " +
                    "flags=PROFILE|QUIET_MODE|INITIALIZED"
            )
        ).single()

        parsed.type shouldBe UserProfile2.Type.PRIVATE_PROFILE
        parsed.isQuietMode shouldBe true
        parsed.isRunning shouldBe false
    }

    @Test
    fun `unknown future profile type remains a profile instead of a full user`() {
        val parsed = UserListParser.parse(
            listOf(
                "3: id=12, name=Clone, type=android.os.usertype.profile.CLONE, flags=PROFILE|INITIALIZED"
            )
        ).single()

        parsed.type shouldBe UserProfile2.Type.OTHER_PROFILE
    }

    @Test
    fun `legacy managed-profile flag classifies work profile`() {
        val parsed = UserListParser.parse(
            listOf("\tUserInfo{10:Work profile:1020} running")
        ).single()

        parsed.id shouldBe 10
        parsed.name shouldBe "Work profile"
        parsed.code shouldBe "1020"
        parsed.type shouldBe UserProfile2.Type.WORK_PROFILE
        parsed.isRunning shouldBe true
    }

    @Test
    fun `legacy full user with colon in name parses from the outer delimiters`() {
        val parsed = UserListParser.parse(
            listOf("\tUserInfo{12:Guest: temporary:400}")
        ).single()

        parsed.id shouldBe 12
        parsed.name shouldBe "Guest: temporary"
        parsed.type shouldBe UserProfile2.Type.FULL_USER
        parsed.isRunning shouldBe false
    }

    @Test
    fun `one malformed line does not discard valid users`() {
        val parsed = UserListParser.parse(
            listOf(
                "Users:",
                "this is not a user",
                "0: id=0, name=Owner, type=android.os.usertype.full.SYSTEM, flags=FULL|SYSTEM (running)",
                "\tUserInfo{10:Work:1020} running",
            )
        )

        parsed.map { it.id } shouldContainExactly listOf(0, 10)
    }

    @Test
    fun `unknown future flag is preserved and ignored by classification`() {
        val parsed = UserListParser.parse(
            listOf(
                "4: id=20, name=Mystery, type=android.os.usertype.full.SECONDARY, " +
                    "flags=FULL|SOMETHING_FUTURE (running)"
            )
        ).single()

        parsed.type shouldBe UserProfile2.Type.FULL_USER
        parsed.flags shouldContainExactly setOf("FULL", "SOMETHING_FUTURE")
    }
}
