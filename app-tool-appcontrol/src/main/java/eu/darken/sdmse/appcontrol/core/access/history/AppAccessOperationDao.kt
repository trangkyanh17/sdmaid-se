package eu.darken.sdmse.appcontrol.core.access.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AppAccessOperationDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: AppAccessOperationEntity)

    @Query("SELECT * FROM app_access_operations WHERE id = :id LIMIT 1")
    suspend fun get(id: String): AppAccessOperationEntity?

    @Query(
        """
        SELECT * FROM app_access_operations
        WHERE package_name = :packageName AND user_id = :userId
        ORDER BY created_at_epoch_ms DESC, id DESC
        LIMIT :limit
        """
    )
    fun observeForTarget(
        packageName: String,
        userId: Int,
        limit: Int,
    ): Flow<List<AppAccessOperationEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM app_access_operations WHERE revert_of = :operationId)")
    suspend fun hasRevert(operationId: String): Boolean

    @Query("DELETE FROM app_access_operations WHERE created_at_epoch_ms < :cutoffEpochMs")
    suspend fun deleteOlderThan(cutoffEpochMs: Long): Int

    @Query(
        """
        DELETE FROM app_access_operations
        WHERE id IN (
            SELECT id FROM app_access_operations
            ORDER BY created_at_epoch_ms DESC, id DESC
            LIMIT -1 OFFSET :maxEntries
        )
        """
    )
    suspend fun trimToMax(maxEntries: Int): Int

    @Query(
        """
        DELETE FROM app_access_operations
        WHERE revert_of IS NOT NULL
          AND revert_of NOT IN (SELECT id FROM app_access_operations)
        """
    )
    suspend fun deleteOrphanedReverts(): Int

    @Query("SELECT COUNT(*) FROM app_access_operations")
    suspend fun count(): Int
}
