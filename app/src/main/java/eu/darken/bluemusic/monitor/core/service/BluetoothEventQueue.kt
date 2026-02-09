package eu.darken.bluemusic.monitor.core.service

import android.os.Parcelable
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.parcelize.Parcelize
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothEventQueue @Inject constructor() {

    private val _events = Channel<Event>(Channel.UNLIMITED)

    val events = _events.receiveAsFlow()

    suspend fun submit(event: Event) {
        log(TAG) { "submit($event)" }
        _events.send(event)
    }

    @Parcelize
    data class Event(
        val type: Type,
        val sourceDevice: SourceDevice,
    ) : Parcelable {
        enum class Type {
            CONNECTED,
            DISCONNECTED,
            ;
        }
    }

    companion object {
        private val TAG = logTag("Monitor", "Event", "Queue")
    }
}