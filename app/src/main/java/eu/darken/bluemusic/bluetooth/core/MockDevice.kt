package eu.darken.bluemusic.bluetooth.core

import android.bluetooth.BluetoothClass
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import eu.darken.bluemusic.main.core.audio.AudioStream
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
data class MockDevice(
    override val label: String = "MockDevice ${UUID.randomUUID().toString().take(4)}",
) : SourceDevice {
    @IgnoredOnParcel
    override val address: String = UUID.randomUUID()
        .toString()
        .uppercase()
        .replace("-", "")
        .take(16)
        .chunked(2)
        .joinToString(":")
    override val bluetoothClass: BluetoothClass?
        get() = null
    override val name: String?
        get() = null
    override val alias: String?
        get() = null

    override fun getStreamId(type: AudioStream.Type): AudioStream.Id {
        TODO("Not yet implemented")
    }

    fun toManagedDevice(
        isActive: Boolean = false,
    ) = ManagedDevice(
        device = this,
        config = DeviceConfigEntity(
            address = address,
            musicVolume = 0.7f,
            callVolume = 0.6f,
            ringVolume = 0.5f,
            notificationVolume = 0.4f,
            alarmVolume = 0.3f,
        ),
        isActive = isActive,
    )
}
