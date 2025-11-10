package eu.darken.bluemusic.devices.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        DeviceConfigEntity::class,
    ],
    version = 3,
    autoMigrations = [
        AutoMigration(from = 2, to = 3, spec = Migration2To3::class)
    ],
    exportSchema = true,
)
@TypeConverters(StringListTypeConverter::class, IntListTypeConverter::class, DndModeTypeConverter::class)
abstract class DevicesRoomDb : RoomDatabase() {
    abstract fun devices(): DeviceConfigDao
}