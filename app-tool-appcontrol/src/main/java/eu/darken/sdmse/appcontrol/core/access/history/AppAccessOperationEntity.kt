package eu.darken.sdmse.appcontrol.core.access.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "app_access_operations",
    indices = [
        Index(value = ["package_name", "user_id", "created_at_epoch_ms"]),
        Index(value = ["revert_of"], unique = true),
    ],
)
data class AppAccessOperationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "package_name")
    val packageName: String,
    @ColumnInfo(name = "user_id")
    val userId: Int,
    @ColumnInfo(name = "kind")
    val kind: String,
    @ColumnInfo(name = "subject_id")
    val subjectId: String,
    @ColumnInfo(name = "before_value")
    val beforeValue: String,
    @ColumnInfo(name = "after_value")
    val afterValue: String,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "revert_of")
    val revertOf: String? = null,
)
