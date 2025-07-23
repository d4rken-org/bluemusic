package eu.darken.bluemusic.monitor.core.modules.connection

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.INFO
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.currentDevices
import eu.darken.bluemusic.monitor.core.WakeLockManager
import eu.darken.bluemusic.monitor.core.modules.ConnectionModule
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import eu.darken.bluemusic.monitor.core.modules.delayForReactionDelay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeepAwakeModule @Inject internal constructor(
    private val deviceRepo: DeviceRepo,
    private val wakeLockManager: WakeLockManager,
) : ConnectionModule {

    override val tag: String
        get() = TAG

    override val priority: Int = 3

    override suspend fun handle(event: DeviceEvent) {
        val device = event.device
        if (!device.keepAwake) return

        delayForReactionDelay(event)

        val deviceMap = deviceRepo.currentDevices().associateBy { it.address }
        val hasAnyKeepAwakeDevice = deviceMap.values.any { d -> d.keepAwake && d.isActive }

        when (event) {
            is DeviceEvent.Connected -> {
                log(TAG) { "Device connected with keep awake: $device" }
                wakeLockManager.setWakeLock(true)
            }

            is DeviceEvent.Disconnected -> {
                log(TAG) { "Device disconnected with keep awake: $device" }
                if (!hasAnyKeepAwakeDevice) {
                    log(TAG) { "No more devices need keep awake, releasing wakelock" }
                    wakeLockManager.setWakeLock(false)
                } else {
                    log(TAG, INFO) { "Other devices still need keep awake, maintaining wakelock" }
                }
            }
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: KeepAwakeModule): ConnectionModule
    }

    companion object {
        private val TAG = logTag("Monitor", "KeepAwake", "Module")
    }
}
