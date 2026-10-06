package eu.darken.sdmse.appcontrol.core.access.history

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

class AppAccessOperationDaoTest : BaseTest() {

    private lateinit var db: AppAccessHistoryRoomDb
    private lateinit var dao: AppAccessOperationDao

    @BeforeEach
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppAccessHistoryRoomDb::class.java,
        ).allowMainThreadQueries().build()
        dao = db.operations()
    }

    @AfterEach
    fun teardown() {
        db.close()
    }

    private fun row(
        id: String,
        packageName: String = "pkg.a",
        userId: Int = 0,
        createdAt: Long,
        revertOf: String? = null,
    ) = AppAccessOperationEntity(
        id = id,
        packageName = packageName,
        userId = userId,
        kind = AppAccessOperation.Kind.RUNTIME_PERMISSION.name,
        subjectId = "android.permission.CAMERA",
        beforeValue = "denied",
        afterValue = "granted",
        createdAtEpochMs = createdAt,
        revertOf = revertOf,
    )

    @Test
    fun `history partitions exact package and user and sorts newest first`() = runTest {
        dao.insert(row(id = "a-old", createdAt = 1))
        dao.insert(row(id = "a-new", createdAt = 3))
        dao.insert(row(id = "other-user", userId = 10, createdAt = 4))
        dao.insert(row(id = "other-pkg", packageName = "pkg.b", createdAt = 5))

        dao.observeForTarget("pkg.a", 0, 20).first().map { it.id } shouldContainExactly
            listOf("a-new", "a-old")
    }

    @Test
    fun `history limit is enforced by query`() = runTest {
        dao.insert(row(id = "one", createdAt = 1))
        dao.insert(row(id = "two", createdAt = 2))
        dao.insert(row(id = "three", createdAt = 3))

        dao.observeForTarget("pkg.a", 0, 2).first().map { it.id } shouldContainExactly
            listOf("three", "two")
    }

    @Test
    fun `only one undo row can reference an original operation`() = runTest {
        dao.insert(row(id = "original", createdAt = 1))
        dao.insert(row(id = "undo-1", createdAt = 2, revertOf = "original"))

        dao.hasRevert("original") shouldBe true

        shouldThrow<SQLiteConstraintException> {
            dao.insert(row(id = "undo-2", createdAt = 3, revertOf = "original"))
        }
    }

    @Test
    fun `retention removes strictly older rows`() = runTest {
        dao.insert(row(id = "expired", createdAt = 99))
        dao.insert(row(id = "boundary", createdAt = 100))
        dao.insert(row(id = "new", createdAt = 101))

        dao.deleteOlderThan(100) shouldBe 1
        dao.observeForTarget("pkg.a", 0, 20).first().map { it.id } shouldContainExactly
            listOf("new", "boundary")
    }

    @Test
    fun `global trim keeps newest rows`() = runTest {
        (1L..5L).forEach { n ->
            dao.insert(row(id = "row-$n", createdAt = n))
        }

        dao.trimToMax(3) shouldBe 2
        dao.count() shouldBe 3
        dao.observeForTarget("pkg.a", 0, 20).first().map { it.id } shouldContainExactly
            listOf("row-5", "row-4", "row-3")
    }

    @Test
    fun `orphaned undo rows are pruned after original disappears`() = runTest {
        dao.insert(row(id = "original", createdAt = 1))
        dao.insert(row(id = "undo", createdAt = 2, revertOf = "original"))

        dao.deleteOlderThan(2) shouldBe 1
        dao.deleteOrphanedReverts() shouldBe 1
        dao.count() shouldBe 0
    }
}
