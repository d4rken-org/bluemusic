package eu.darken.bluemusic.monitor.core.modules.connection

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
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLaunchModule @Inject constructor(
    private val appRepo: AppRepo
) : ConnectionModule {

    override val tag: String
        get() = TAG

    override val priority: Int = 5

    private fun isApplicable(event: DeviceEvent): Boolean =
        event is DeviceEvent.Connected && event.device.launchPkgs.isNotEmpty()

    override fun appliesTo(event: DeviceEvent): Boolean = isApplicable(event)

    override suspend fun handle(event: DeviceEvent) {
        if (!isApplicable(event)) return
        val device = event.device
        val appsToLaunch = device.launchPkgs

        log(TAG) { "Launching ${appsToLaunch.size} apps: $appsToLaunch" }

        appsToLaunch.forEach { pkg ->
            try {
                log(TAG) { "Launching app: $pkg" }
                appRepo.launch(pkg)
                delay(500) // Small delay between launches
            } catch (e: Exception) {
                log(TAG) { "Failed to launch app $pkg: ${e.message}" }
            }
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: AppLaunchModule): ConnectionModule
    }

    companion object {
        private val TAG = logTag("Monitor", "AppLaunch", "Module")
    }
}
