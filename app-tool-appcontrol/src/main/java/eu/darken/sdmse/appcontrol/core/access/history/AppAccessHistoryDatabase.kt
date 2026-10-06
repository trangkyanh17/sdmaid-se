package eu.darken.sdmse.appcontrol.core.access.history

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.sdmse.common.pkgs.features.InstallId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppAccessHistoryDatabase @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val database by lazy {
        Room.databaseBuilder(context, AppAccessHistoryRoomDb::class.java, DB_NAME).build()
    }

    private val dao: AppAccessOperationDao
        get() = database.operations()

    fun history(
        installId: InstallId,
        limit: Int = MAX_ROWS_PER_TARGET,
    ): Flow<List<AppAccessOperation>> {
        require(limit in 1..MAX_ROWS_PER_TARGET) {
            "History limit must be in 1..$MAX_ROWS_PER_TARGET"
        }

        return dao.observeForTarget(
            packageName = installId.pkgId.name,
            userId = installId.userHandle.handleId,
            limit = limit,
        ).map { rows -> rows.map(AppAccessOperation::fromEntity) }
    }

    suspend fun get(id: String): AppAccessOperation? =
        dao.get(id)?.let(AppAccessOperation::fromEntity)

    suspend fun hasRevert(operationId: String): Boolean =
        dao.hasRevert(operationId)

    suspend fun record(
        installId: InstallId,
        kind: AppAccessOperation.Kind,
        subjectId: String,
        before: AppAccessOperation.Value,
        after: AppAccessOperation.Value,
        revertOf: String? = null,
        createdAt: Instant = Instant.now(),
    ): AppAccessOperation {
        require(subjectId.isNotBlank()) { "subjectId must not be blank" }

        val entity = AppAccessOperationEntity(
            id = UUID.randomUUID().toString(),
            packageName = installId.pkgId.name,
            userId = installId.userHandle.handleId,
            kind = kind.name,
            subjectId = subjectId,
            beforeValue = AppAccessOperation.encodeValue(kind, before),
            afterValue = AppAccessOperation.encodeValue(kind, after),
            createdAtEpochMs = createdAt.toEpochMilli(),
            revertOf = revertOf,
        )

        database.withTransaction {
            dao.insert(entity)
            pruneLocked(createdAt)
        }

        return AppAccessOperation.fromEntity(entity)
    }

    internal suspend fun prune(now: Instant = Instant.now()) {
        database.withTransaction {
            pruneLocked(now)
        }
    }

    private suspend fun pruneLocked(now: Instant) {
        val cutoff = (now - RETENTION).toEpochMilli()
        dao.deleteOlderThan(cutoff)
        dao.trimToMax(MAX_ROWS_GLOBAL)
        dao.deleteOrphanedReverts()
    }

    companion object {
        private const val DB_NAME = "appcontrol_access_history"
        internal const val MAX_ROWS_GLOBAL = 1_000
        internal const val MAX_ROWS_PER_TARGET = 20
        internal val RETENTION: Duration = Duration.ofDays(30)
    }
}
