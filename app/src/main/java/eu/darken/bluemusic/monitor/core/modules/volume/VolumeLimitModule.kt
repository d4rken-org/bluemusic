package eu.darken.bluemusic.monitor.core.modules.volume

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.currentDevices
import eu.darken.bluemusic.monitor.core.audio.VolumeEvent
import eu.darken.bluemusic.monitor.core.audio.VolumeLimitEnforcer
import eu.darken.bluemusic.monitor.core.modules.VolumeModule
import eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class VolumeLimitModule @Inject constructor(
    private val limitEnforcer: VolumeLimitEnforcer,
    private val deviceRepo: DeviceRepo,
    private val ownerRegistry: AudioStreamOwnerRegistry,
) : VolumeModule {

    override val tag: String
        get() = TAG

    // Run before VolumeUpdateModule (priority 10) so the corrected level is what gets persisted,
    // and before VolumeLockModule (also 10) so the two can't race: the dispatcher runs modules of
    // equal priority concurrently.
    override val priority: Int = 7

    override suspend fun handle(event: VolumeEvent) {
        val id = event.streamId

        if (event.self) {
            log(TAG, VERBOSE) { "Volume change was triggered by us, ignoring it." }
            return
        }

        val ownerAddresses = ownerRegistry.ownerAddressesFor(id).toSet()
        if (ownerAddresses.isEmpty()) return

        limitEnforcer.enforce(
            streamId = id,
            devices = deviceRepo.currentDevices(),
            ownerAddresses = ownerAddresses,
        )
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: VolumeLimitModule): VolumeModule
    }

    companion object {
        private val TAG = logTag("Monitor", "Volume", "Limit", "Module")
    }
}
