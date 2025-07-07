package eu.darken.bluemusic.common

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.*
import eu.darken.bluemusic.main.core.service.ServiceHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private val TAG = logTag("EventGenerator")
        const val EXTRA_DEVICE_EVENT: String = "eu.darken.bluemusic.core.bluetooth.event"
    }

    fun generate(device: SourceDevice, type: SourceDevice.Event.Type): SourceDevice.Event {
        return SourceDevice.Event(device, type)
    }

    fun send(device: SourceDevice, type: SourceDevice.Event.Type) {
        log(TAG) { "Generating and sending $type for $device" }
        val event = generate(device, type)

        val service: Intent = ServiceHelper.getIntent(context)
        service.putExtra(EXTRA_DEVICE_EVENT, event)
        val componentName: ComponentName? = ServiceHelper.startService(context, service)
        if (componentName != null) log(TAG, VERBOSE) { "Service is already running." }
    }
}
