package eu.darken.bluemusic.monitor.core.modules.connection

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.hasApiLevel
import eu.darken.bluemusic.common.permissions.PermissionHelper
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.VolumeObserver
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.modules.ConnectionModule
import eu.darken.bluemusic.monitor.core.modules.volume.VolumeObservationGate
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.monitor.core.ownership.AudioStreamOwnerRegistry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationVolumeModule @Inject constructor(
    volumeTool: VolumeTool,
    volumeObserver: VolumeObserver,
    observationGate: VolumeObservationGate,
    ownerRegistry: AudioStreamOwnerRegistry,
    deviceRepo: DeviceRepo,
    private val permissionHelper: PermissionHelper,
) : BaseVolumeModule(volumeTool, volumeObserver, observationGate, ownerRegistry, deviceRepo) {

    override val type: AudioStream.Type = AudioStream.Type.NOTIFICATION

    @Suppress("NewApi")
    override suspend fun unmetRequirement(): String? = when {
        !hasApiLevel(23) -> null
        permissionHelper.hasNotificationPolicyAccess() -> null
        else -> "ACCESS_NOTIFICATION_POLICY (DND access) is not granted"
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: NotificationVolumeModule): ConnectionModule
    }
}