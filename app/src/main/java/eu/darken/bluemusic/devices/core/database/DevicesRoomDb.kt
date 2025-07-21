package eu.darken.bluemusic.devices.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        DeviceConfigEntity::class,
    ],
    version = 3,
    autoMigrations = [
        androidx.room.AutoMigration(from = 1, to = 2),
        androidx.room.AutoMigration(from = 2, to = 3)
    ],
    exportSchema = true,
)
@TypeConverters(StringListTypeConverter::class)
abstract class DevicesRoomDb : RoomDatabase() {
    abstract fun devices(): DeviceConfigDao
}