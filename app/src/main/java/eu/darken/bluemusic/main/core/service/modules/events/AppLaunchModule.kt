package eu.darken.bluemusic.main.core.service.modules.events

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.common.AppTool
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.ManagedDevice
import eu.darken.bluemusic.main.core.service.modules.EventModule
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLaunchModule @Inject constructor(
    private val appTool: AppTool
) : EventModule {

    override val priority: Int = 1

    override suspend fun handle(device: ManagedDevice, event: SourceDevice.Event) {
        if (event.type != SourceDevice.Event.Type.CONNECTED) return
        if (device.launchPkg == null) return

        log(TAG) { "Launching app ${device.launchPkg}" }
        appTool.launch(device.launchPkg!!)

        try {
            Thread.sleep(1000)
        } catch (e: InterruptedException) {
            log(TAG, WARN) { "Thread interrupted: ${e.asLog()}" }
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: AppLaunchModule): EventModule
    }

    companion object {
        private val TAG = logTag("AppLaunchModule")
    }
}