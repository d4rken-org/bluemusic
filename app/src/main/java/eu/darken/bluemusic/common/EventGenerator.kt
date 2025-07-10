package eu.darken.bluemusic.common

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
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
        generate(device, type)
        // TODO
//        val service: Intent = ServiceController.getIntent(context)
//        service.putExtra(EXTRA_DEVICE_EVENT, event)
//        val componentName: ComponentName? = ServiceController.startService(context, service)
//        if (componentName != null) log(TAG, VERBOSE) { "Service is already running." }
    }
}
