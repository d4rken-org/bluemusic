package eu.darken.bluemusic.monitor.core.modules.connection

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.common.upgrade.isProSettled
import eu.darken.bluemusic.monitor.core.alert.AlertTool
import eu.darken.bluemusic.monitor.core.alert.AlertType
import eu.darken.bluemusic.monitor.core.modules.ConnectionModule
import eu.darken.bluemusic.monitor.core.modules.DeviceEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

@Singleton
class ConnectionAlertModule @Inject constructor(
    private val alertTool: AlertTool,
    private val upgradeRepo: UpgradeRepo,
) : ConnectionModule {

    private val activeAlertJobs = ConcurrentHashMap<String, Job>()

    override val tag: String
        get() = TAG

    override val priority: Int = 25

    private fun isApplicable(event: DeviceEvent): Boolean =
        event is DeviceEvent.Connected && event.device.connectionAlertType != AlertType.NONE

    override fun appliesTo(event: DeviceEvent): Boolean = isApplicable(event)

    override suspend fun handle(event: DeviceEvent) {
        if (!isApplicable(event)) return
        val device = event.device

        // Background gate on a device-connect event, i.e. exactly when a cold start may still be
        // waiting on Play. A raw isPro() read would silently suppress a paying user's alert, so this
        // reconciles first — bounded, so the alert is delayed at most this long in the unsettled window.
        if (!upgradeRepo.isProSettled(timeout = 3.seconds)) {
            log(TAG) { "Skipping connection alert - requires Pro version" }
            return
        }

        log(TAG) { "Connection alert enabled for device ${device.label}" }

        activeAlertJobs[device.address]?.cancel()

        coroutineScope {
            activeAlertJobs[device.address] = launch {
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
