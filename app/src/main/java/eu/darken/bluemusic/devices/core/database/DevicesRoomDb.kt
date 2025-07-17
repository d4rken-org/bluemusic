package eu.darken.bluemusic.devices.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        DeviceConfigEntity::class,
    ],
    version = 2,
    autoMigrations = [
        AutoMigration(1, 2)
    ],
    exportSchema = true,
)
abstract class DevicesRoomDb : RoomDatabase() {
    abstract fun devices(): DeviceConfigDao
}