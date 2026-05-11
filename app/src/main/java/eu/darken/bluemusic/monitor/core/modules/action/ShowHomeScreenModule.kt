package eu.darken.bluemusic.monitor.core.modules.action

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.apps.AppRepo
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.monitor.core.modules.ConnectionModule
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShowHomeScreenModule @Inject internal constructor(
    private val appRepo: AppRepo,
) : ConnectionModule {

    override val tag: String
        get() = TAG

    override val priority: Int = 100

    private fun isApplicable(event: DeviceEvent): Boolean =
        event is DeviceEvent.Connected && event.device.showHomeScreen

    override fun appliesTo(event: DeviceEvent): Boolean = isApplicable(event)

    override suspend fun handle(event: DeviceEvent) {
        if (!isApplicable(event)) return
        val device = event.device

        log(TAG) { "Showing home screen for device: ${device.label}" }
        appRepo.goToHomeScreen()
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: ShowHomeScreenModule): ConnectionModule
    }

    companion object {
        private val TAG = logTag("Monitor", "ShowHomeScreen", "Module")
    }
}
