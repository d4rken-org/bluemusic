package eu.darken.bluemusic.main.ui.widget

import android.content.Context
import androidx.annotation.Keep
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.currentDevices
import eu.darken.bluemusic.devices.core.toggleVolumeLock
import kotlinx.coroutines.CancellationException

class VolumeLockToggleAction : ActionCallback {

    @Keep
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ActionEntryPoint {
        fun deviceRepo(): DeviceRepo
        fun upgradeRepo(): UpgradeRepo
        fun widgetManager(): WidgetManager
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val ep = EntryPointAccessors.fromApplication(context, ActionEntryPoint::class.java)
        try {
            val address = parameters[PARAM_DEVICE_ADDRESS]
            log(TAG) { "onAction(address=$address)" }
            if (address == null) return

            val device = ep.deviceRepo().currentDevices()
                .firstOrNull { it.address == address && it.isActive }
            if (device == null) {
                log(TAG) { "Device $address is no longer active" }
                return
            }

            ep.deviceRepo().toggleVolumeLock(address, ep.upgradeRepo())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log(TAG, ERROR) { "onAction failed: ${e.asLog()}" }
        } finally {
            runCatching { ep.widgetManager().refreshWidgets() }
        }
    }

    companion object {
        private val TAG = logTag("Widget", "Action", "ToggleLock")
        val PARAM_DEVICE_ADDRESS = ActionParameters.Key<String>("device_address")
    }
}
