package eu.darken.sdmse.appcontrol.core.access.history

import eu.darken.sdmse.common.pkgs.pkgops.PkgOps
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AppAccessOperationTest : BaseTest() {

    @Test
    fun `runtime permission values round trip strictly`() {
        AppAccessOperation.decodeValue(
            AppAccessOperation.Kind.RUNTIME_PERMISSION,
            AppAccessOperation.encodeValue(
                AppAccessOperation.Kind.RUNTIME_PERMISSION,
                AppAccessOperation.Value.Permission(granted = true),
            ),
        ) shouldBe AppAccessOperation.Value.Permission(granted = true)
    }

    @Test
    fun `appops values round trip strictly`() {
        PkgOps.AppOpsValue.entries.forEach { value ->
            AppAccessOperation.decodeValue(
                AppAccessOperation.Kind.APP_OP,
                AppAccessOperation.encodeValue(
                    AppAccessOperation.Kind.APP_OP,
                    AppAccessOperation.Value.AppOp(value),
                ),
            ) shouldBe AppAccessOperation.Value.AppOp(value)
        }
    }

    @Test
    fun `mismatched value type is rejected`() {
        shouldThrow<IllegalArgumentException> {
            AppAccessOperation.encodeValue(
                AppAccessOperation.Kind.RUNTIME_PERMISSION,
                AppAccessOperation.Value.AppOp(PkgOps.AppOpsValue.ALLOW),
            )
        }
    }

    @Test
    fun `unknown persisted runtime value fails closed`() {
        shouldThrow<IllegalStateException> {
            AppAccessOperation.decodeValue(
                AppAccessOperation.Kind.RUNTIME_PERMISSION,
                "unknown",
            )
        }
    }

    @Test
    fun `unknown persisted appops value fails closed`() {
        shouldThrow<IllegalStateException> {
            AppAccessOperation.decodeValue(
                AppAccessOperation.Kind.APP_OP,
                "foreground",
            )
        }
    }

    @Test
    fun `unknown persisted operation kind fails closed`() {
        val entity = AppAccessOperationEntity(
            id = "id",
            packageName = "pkg",
            userId = 0,
            kind = "BOGUS",
            subjectId = "subject",
            beforeValue = "granted",
            afterValue = "denied",
            createdAtEpochMs = 1L,
        )

        shouldThrow<IllegalStateException> {
            AppAccessOperation.fromEntity(entity)
        }
    }
}
