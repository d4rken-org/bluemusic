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
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.upgrade.UpgradeRepo
import eu.darken.bluemusic.devices.core.DeviceRepo
import eu.darken.bluemusic.devices.core.currentDevices
import eu.darken.bluemusic.devices.core.toggleVolumeLock

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
        val address = parameters[PARAM_DEVICE_ADDRESS]
        log(TAG) { "onAction(address=$address)" }

        val ep = EntryPointAccessors.fromApplication(context, ActionEntryPoint::class.java)

        if (address == null) {
            log(TAG) { "No device address in parameters, refreshing widgets" }
            ep.widgetManager().refreshWidgets()
            return
        }

        val devices = ep.deviceRepo().currentDevices()
        val device = devices.firstOrNull { it.address == address && it.isActive }

        if (device == null) {
            log(TAG) { "Device $address is no longer active, refreshing widgets" }
            ep.widgetManager().refreshWidgets()
            return
        }

        ep.deviceRepo().toggleVolumeLock(address, ep.upgradeRepo())
        ep.widgetManager().refreshWidgets()
    }

    companion object {
        private val TAG = logTag("Widget", "Action", "ToggleLock")
        val PARAM_DEVICE_ADDRESS = ActionParameters.Key<String>("device_address")
    }
}
