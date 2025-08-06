package eu.darken.bluemusic.monitor.core.modules.connection

import android.os.Build
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.hasApiLevel
import eu.darken.bluemusic.common.permissions.PermissionHelper
import eu.darken.bluemusic.monitor.core.audio.DndTool
import eu.darken.bluemusic.monitor.core.modules.ConnectionModule
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import eu.darken.bluemusic.monitor.core.modules.delayForReactionDelay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DndModeModule @Inject constructor(
    private val dndTool: DndTool,
    private val permissionHelper: PermissionHelper
) : ConnectionModule {

    override val tag: String
        get() = TAG

    override val priority: Int = 3 // Before volume modules

    override suspend fun handle(event: DeviceEvent) {
        if (event !is DeviceEvent.Connected) return

        if (!hasApiLevel(Build.VERSION_CODES.M)) {
            log(TAG) { "Skipping DND mode handling - requires API 23+" }
            return
        }

        val device = event.device

        device.dndMode?.let { mode ->
            if (!permissionHelper.hasNotificationPolicyAccess()) {
                log(TAG) { "Skipping DND mode handling - no notification policy access" }
                return
            }

            delayForReactionDelay(event)

            log(TAG) { "Setting DND mode on connect to $mode for device ${device.label}" }
            dndTool.setDndMode(mode)
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: DndModeModule): ConnectionModule
    }

    companion object {
        private val TAG = logTag("Monitor", "DndMode", "Module")
    }
}