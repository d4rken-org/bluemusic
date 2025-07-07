package eu.darken.bluemusic.main.core.service.modules.events

import eu.darken.bluemusic.AppComponent
import eu.darken.bluemusic.bluetooth.core.FakeSpeakerDevice
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.data.device.DeviceManagerFlow
import eu.darken.bluemusic.data.device.ManagedDevice
import eu.darken.bluemusic.main.core.service.modules.EventModule
import eu.darken.bluemusic.util.EventGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject

@AppComponent.Scope
class FakeDeviceConnectModule @Inject constructor(
    private val eventGenerator: EventGenerator,
    private val deviceManager: DeviceManagerFlow
) : EventModule() {

    override fun getPriority(): Int = 0

    override fun handle(eventDevice: ManagedDevice, event: SourceDevice.Event) {
        if (event.type != SourceDevice.Event.Type.DISCONNECTED) return

        val managed = runBlocking { deviceManager.devices().first() }
        val fakeSpeaker = managed[FakeSpeakerDevice.ADDR]
        Timber.d("FakeSpeaker: %s", fakeSpeaker)
        if (fakeSpeaker == null) return

        val active = managed.filter { it.value.isActive }
        Timber.d("Active devices: %s", active)
        if (active.size > 1 || active.size == 1 && !active.containsKey(fakeSpeaker.address)) return

        Timber.d("Sending fake device connect event.")
        // Create a fake source device for the event
        val fakeSourceDevice = object : SourceDevice {
            override val bluetoothClass = null
            override val address = fakeSpeaker.address
            override val name = fakeSpeaker.name
            override val alias = fakeSpeaker.alias
            override val label = fakeSpeaker.label
            override fun setAlias(newAlias: String?) = false
            override fun getStreamId(type: eu.darken.bluemusic.main.core.audio.AudioStream.Type) =
                fakeSpeaker.getStreamId(type)

            override fun describeContents() = 0
            override fun writeToParcel(dest: android.os.Parcel, flags: Int) {}
        }
        eventGenerator.send(fakeSourceDevice, SourceDevice.Event.Type.CONNECTED)
    }
}