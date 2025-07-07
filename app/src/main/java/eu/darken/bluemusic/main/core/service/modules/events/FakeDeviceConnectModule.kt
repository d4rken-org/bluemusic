package eu.darken.bluemusic.main.core.service.modules.events

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.bluetooth.core.speaker.FakeSpeakerDevice
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.main.core.service.modules.EventModule
import eu.darken.bluemusic.common.EventGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DeviceManagerFlowAdapter
import javax.inject.Inject

import javax.inject.Singleton

@Singleton
class FakeDeviceConnectModule @Inject constructor(
    private val eventGenerator: EventGenerator,
    private val deviceManager: DeviceManagerFlowAdapter
) : EventModule {

    companion object {
        private val TAG = logTag("FakeDeviceConnectModule")
    }

    override val priority: Int
        get() = 0

    override fun handle(eventDevice: ManagedDevice, event: SourceDevice.Event) {
        if (event.type != SourceDevice.Event.Type.DISCONNECTED) return

        val managed = runBlocking { deviceManager.devices().first() }
        val fakeSpeaker = managed[FakeSpeakerDevice.address]
        log(TAG) { "FakeSpeaker: $fakeSpeaker" }
        if (fakeSpeaker == null) return

        val active = managed.filter { it.value.isActive }
        log(TAG) { "Active devices: $active" }
        if (active.size > 1 || active.size == 1 && !active.containsKey(fakeSpeaker.address)) return

        log(TAG) { "Sending fake device connect event." }
        // Create a fake source device for the event
        val fakeSourceDevice = object : SourceDevice {
            override val bluetoothClass = null
            override val address = fakeSpeaker.address
            override val name = fakeSpeaker.name
            override val alias = fakeSpeaker.alias
            override val label = fakeSpeaker.label
            override fun getStreamId(type: eu.darken.bluemusic.main.core.audio.AudioStream.Type) =
                fakeSpeaker.getStreamId(type)

            override fun describeContents() = 0
            override fun writeToParcel(dest: android.os.Parcel, flags: Int) {}
        }
        eventGenerator.send(fakeSourceDevice, SourceDevice.Event.Type.CONNECTED)
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: FakeDeviceConnectModule): EventModule
    }
}