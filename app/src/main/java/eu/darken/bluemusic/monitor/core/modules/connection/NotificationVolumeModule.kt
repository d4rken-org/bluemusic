package eu.darken.bluemusic.monitor.core.modules.connection

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.hasApiLevel
import eu.darken.bluemusic.common.permissions.PermissionHelper
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.modules.ConnectionModule
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationVolumeModule @Inject constructor(
    volumeTool: VolumeTool,
    private val permissionHelper: PermissionHelper
) : BaseVolumeModule(volumeTool) {

    override val type: AudioStream.Type = AudioStream.Type.NOTIFICATION

    @Suppress("NewApi")
    override suspend fun areRequirementsMet(): Boolean = !hasApiLevel(23) || permissionHelper.hasNotificationPolicyAccess()

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: NotificationVolumeModule): ConnectionModule
    }
}