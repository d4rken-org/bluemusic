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
import eu.darken.bluemusic.monitor.core.modules.SettlePolicy
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

    override fun appliesTo(event: DeviceEvent): Boolean = event.device.keepAwake

    /**
     * On Connected: wake the screen immediately, before the settle barrier — the user
     * expects the screen to wake the moment the device connects, not 6 seconds later.
     * On Disconnected: wait the barrier so a rapid reconnect (cancellable supersede)
     * has the chance to interrupt before we release the wakelock.
     */
    override fun settlePolicy(event: DeviceEvent): SettlePolicy = when (event) {
        is DeviceEvent.Connected -> SettlePolicy.Immediate
        is DeviceEvent.Disconnected -> SettlePolicy.AfterDeviceSettle
        else -> SettlePolicy.AfterDeviceSettle
    }

    override suspend fun handle(event: DeviceEvent) {
        if (!appliesTo(event)) return
        val device = event.device

        when (event) {
            is DeviceEvent.Connected -> {
                log(TAG) { "KeepAwake on connect: ${device.address}/${device.label}" }
                wakeLockManager.setWakeLock(true)
                wakeLockManager.wakeScreenNow()
            }

            is DeviceEvent.Disconnected -> {
                // Dispatcher already applied the settle barrier. The cancellable launch
                // should have been cancelled if a reconnect arrived during the wait, but
                // double-check the device hasn't been reconnected just in case (cheap).
                val devices = deviceRepo.currentDevices()
                val maybeReconnected = devices.firstOrNull { it.address == device.address }
                if (maybeReconnected?.isConnected == true) {
                    log(TAG) { "${device.address} reconnected during disconnect handling; skipping wakelock release." }
                    return
                }

                val hasAnyKeepAwakeDevice = devices.any { d -> d.keepAwake && d.isActive }
                log(TAG) { "Device disconnected with keep awake: ${device.address}/${device.label}" }
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
