package eu.darken.bluemusic.monitor.core.modules.connection

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
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLaunchModule @Inject constructor(
    private val appRepo: AppRepo
) : EventModule {

    override val priority: Int = 1

    override suspend fun handle(event: DeviceEvent) {
        if (event !is DeviceEvent.Connected) return
        val device = event.device
        val appsToLaunch = device.launchPkgs
        if (appsToLaunch.isEmpty()) return

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
        @Binds @IntoSet abstract fun bind(entry: AppLaunchModule): EventModule
    }

    companion object {
        private val TAG = logTag("Monitor", "AppLaunch", "Module")
    }
}