package eu.darken.bluemusic.monitor.core.modules.action

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.apps.AppRepo
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import eu.darken.bluemusic.monitor.core.modules.EventModule
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShowHomeScreenModule @Inject internal constructor(
    private val appRepo: AppRepo,
) : EventModule {

    override val priority: Int = 100

    override suspend fun handle(event: DeviceEvent) {
        if (event !is DeviceEvent.Connected) return

        val device = event.device
        if (!device.showHomeScreen) return

        log(TAG) { "Showing home screen for device: ${device.label}" }
        appRepo.goToHomeScreen()
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: ShowHomeScreenModule): EventModule
    }

    companion object {
        private val TAG = logTag("Monitor", "ShowHomeScreen", "Module")
    }
}