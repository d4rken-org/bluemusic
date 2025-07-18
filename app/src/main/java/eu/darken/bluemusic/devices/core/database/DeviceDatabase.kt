package eu.darken.bluemusic.devices.core.database

import android.content.Context
import androidx.room.Room
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceDatabase @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val database by lazy {
        Room.databaseBuilder(
            context,
            DevicesRoomDb::class.java, "managed_devices"
        )
            .build()
    }

    val devices: DeviceConfigDao
        get() = database.devices()

}