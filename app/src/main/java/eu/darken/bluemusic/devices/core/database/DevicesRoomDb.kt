package eu.darken.bluemusic.devices.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        DeviceConfigEntity::class,
    ],
    version = 6,
    autoMigrations = [
        AutoMigration(from = 2, to = 3, spec = Migration2To3::class),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6)
    ],
    exportSchema = true,
)
@TypeConverters(StringListTypeConverter::class, IntListTypeConverter::class, DndModeTypeConverter::class, AlertTypeConverter::class)
abstract class DevicesRoomDb : RoomDatabase() {
    abstract fun devices(): DeviceConfigDao
}