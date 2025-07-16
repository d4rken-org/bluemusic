package eu.darken.bluemusic.monitor.core.modules.connection

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.currentDevices
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import eu.darken.bluemusic.monitor.core.modules.EventModule
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeepAwakeModule @Inject internal constructor(
    private val deviceRepo: DeviceRepo,
) : EventModule {

    override val priority: Int
        get() = 1

    override suspend fun handle(event: DeviceEvent) {
        val device = event.device
        if (!device.keepAwake) return
        if (device.type == SourceDevice.Type.PHONE_SPEAKER) {
            log(TAG, ERROR) { "Keep awake should not be enabled for the fake speaker device: $device" }
            return
        }

        val deviceMap = deviceRepo.currentDevices().associateBy { it.address }
        val otherWokeDevice = deviceMap.values.find { d -> d.keepAwake && d.address != event.address }

        when (event) {
            is DeviceEvent.Connected -> {
                log(TAG) { "Acquiring wakelock for $device" }
                // TODO
//                wakelockMan.tryAquire()
            }

            is DeviceEvent.Connected -> {
                if (otherWokeDevice == null) {
                    log(TAG) { "Releasing wakelock for $device" }
//                    wakelockMan.tryRelease()
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

    companion object {
        private val TAG = logTag("Monitor", "KeepAwake", "Module")
    }
}
