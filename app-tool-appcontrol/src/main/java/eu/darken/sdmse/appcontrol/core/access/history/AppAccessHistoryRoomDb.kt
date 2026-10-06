package eu.darken.sdmse.appcontrol.core.access.history

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AppAccessOperationEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppAccessHistoryRoomDb : RoomDatabase() {
    abstract fun operations(): AppAccessOperationDao
}
