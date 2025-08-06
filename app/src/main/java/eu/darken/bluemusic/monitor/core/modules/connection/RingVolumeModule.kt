package eu.darken.bluemusic.monitor.core.modules.connection

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.hasApiLevel
import eu.darken.bluemusic.common.permissions.PermissionHelper
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import eu.darken.bluemusic.monitor.core.audio.RingerTool
import eu.darken.bluemusic.monitor.core.audio.VolumeTool
import eu.darken.bluemusic.monitor.core.modules.ConnectionModule
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RingVolumeModule @Inject constructor(
    volumeTool: VolumeTool,
    ringerTool: RingerTool,
    private val permissionHelper: PermissionHelper,
) : BaseVolumeWithModesModule(volumeTool, ringerTool) {

    override val type: AudioStream.Type = AudioStream.Type.RINGTONE

    @Suppress("NewApi")
    override suspend fun areRequirementsMet(): Boolean = !hasApiLevel(23) || permissionHelper.hasNotificationPolicyAccess()

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: RingVolumeModule): ConnectionModule
    }
}