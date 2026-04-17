package eu.darken.bluemusic.devices.core.database

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Room
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceDatabase @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    private var databaseOverride: DevicesRoomDb? = null
    private var daoOverride: DeviceConfigDao? = null

    private val defaultDatabase by lazy {
        Room.databaseBuilder(context, DevicesRoomDb::class.java, "managed_devices")
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    private val database: DevicesRoomDb
        get() = databaseOverride ?: defaultDatabase

    @VisibleForTesting
    fun setDatabaseForTest(roomDb: DevicesRoomDb?) {
        databaseOverride = roomDb
    }

    @VisibleForTesting
    fun setDaoForTest(dao: DeviceConfigDao?) {
        daoOverride = dao
    }

    val devices: DeviceConfigDao
        get() = daoOverride ?: database.devices()

    suspend fun <R> withTransaction(block: suspend () -> R): R = database.withTransaction(block)
}
