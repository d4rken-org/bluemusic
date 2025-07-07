package eu.darken.bluemusic.main.core.service.modules.events

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.bluetooth.core.speaker.FakeSpeakerDevice
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.bluetooth.core.SourceDevice.Event.Type.CONNECTED
import eu.darken.bluemusic.bluetooth.core.SourceDevice.Event.Type.DISCONNECTED
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.main.core.service.modules.EventModule
import eu.darken.bluemusic.common.WakelockMan
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.*
import eu.darken.bluemusic.devices.core.DeviceManagerFlowAdapter
import javax.inject.Inject

import javax.inject.Singleton

@Singleton
class KeepAwakeModule @Inject internal constructor(
    private val wakelockMan: WakelockMan,
    private val deviceManager: DeviceManagerFlowAdapter
) : EventModule {

    companion object {
        private val TAG = logTag("KeepAwakeModule")
    }

    override val priority: Int
        get() = 1

    override fun handle(device: ManagedDevice, event: SourceDevice.Event) {
        if (!device.keepAwake) return
        if (device.address == FakeSpeakerDevice.address) {
            log(TAG, ERROR) { "Keep awake should not be enabled for the fake speaker device: $device" }
            return
        }

        val deviceMap = runBlocking { deviceManager.devices().first() }
        val otherWokeDevice = deviceMap.values.find { d -> d.keepAwake && d.address != event.address }

        when (event.type) {
            CONNECTED -> {
                log(TAG) { "Acquiring wakelock for $device" }
                wakelockMan.tryAquire()
            }
            DISCONNECTED -> {
                if (otherWokeDevice == null) {
                    log(TAG) { "Releasing wakelock for $device" }
                    wakelockMan.tryRelease()
                } else {
                    log(
                        TAG,
                        INFO
                    ) { "Not releasing wakelock, another device also wants 'keep awake': $otherWokeDevice" }
                }
            }
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: KeepAwakeModule): EventModule
    }
}
