package eu.darken.bluemusic.main.core.service.modules.events

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.core.speaker.FakeSpeakerDevice
import eu.darken.bluemusic.bluetooth.core.speaker.SpeakerDeviceProvider
import eu.darken.bluemusic.common.EventGenerator
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.devices.core.currentDevices
import eu.darken.bluemusic.main.core.service.modules.EventModule
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FakeDeviceConnectModule @Inject constructor(
    private val eventGenerator: EventGenerator,
    private val deviceRepo: DeviceRepo,
    private val speakerDeviceProvider: SpeakerDeviceProvider,
) : EventModule {

    override val priority: Int
        get() = 0

    override suspend fun handle(device: ManagedDevice, event: SourceDevice.Event) {
        if (event.type != SourceDevice.Event.Type.DISCONNECTED) return

        val managed = runBlocking { deviceRepo.currentDevices() }
        val fakeSpeaker = managed.singleOrNull { it.address == FakeSpeakerDevice.ADDRESS }
        log(TAG) { "FakeSpeaker: $fakeSpeaker" }
        if (fakeSpeaker == null) return

        val active = managed.filter { it.isActive }
        log(TAG) { "Active devices: $active" }
        if (active.size > 1 || active.size == 1 && !active.any { it.address == fakeSpeaker.address }) return

        log(TAG) { "Sending fake device connect event." }
        // Create a fake source device for the event
        val fakeSourceDevice = speakerDeviceProvider.getSpeaker()
        eventGenerator.send(fakeSourceDevice, SourceDevice.Event.Type.CONNECTED)
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: FakeDeviceConnectModule): EventModule
    }

    companion object {
        private val TAG = logTag("FakeDeviceConnectModule")
    }
}