package eu.darken.bluemusic.monitor.core.modules.connection

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.common.upgrade.isPro
import eu.darken.bluemusic.monitor.core.alert.AlertTool
import eu.darken.bluemusic.monitor.core.alert.AlertType
import eu.darken.bluemusic.monitor.core.modules.ConnectionModule
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import eu.darken.bluemusic.monitor.core.modules.delayForReactionDelay
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectionAlertModule @Inject constructor(
    private val alertTool: AlertTool,
    private val upgradeRepo: UpgradeRepo,
) : ConnectionModule {

    private val activeAlertJobs = mutableMapOf<String, Job>()

    override val tag: String
        get() = TAG

    override val priority: Int = 25

    override suspend fun handle(event: DeviceEvent) {
        if (event !is DeviceEvent.Connected) return

        val device = event.device
        if (device.connectionAlertType == AlertType.NONE) return

        if (!upgradeRepo.isPro()) {
            log(TAG) { "Skipping connection alert - requires Pro version" }
            return
        }

        log(TAG) { "Connection alert enabled for device ${device.label}" }

        activeAlertJobs[device.address]?.cancel()

        coroutineScope {
            activeAlertJobs[device.address] = launch {
                delayForReactionDelay(event)

                alertTool.playAlert(device.connectionAlertType, device.connectionAlertSoundUri)
                log(TAG) { "Played connection alert (type=${device.connectionAlertType}) for device ${device.label}" }
            }
        }
    }

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds @IntoSet abstract fun bind(entry: ConnectionAlertModule): ConnectionModule
    }

    companion object {
        private val TAG = logTag("Monitor", "ConnectionAlert", "Module")
    }
}
