package eu.darken.bluemusic.monitor.core.service

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
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

    data class Event(
        val type: Type,
        val sourceDevice: SourceDevice,
        val volumeSnapshot: VolumeSnapshot? = null,
    ) {
        enum class Type {
            CONNECTED,
            DISCONNECTED,
            ;
        }
    }

    data class VolumeSnapshot(val levels: Map<AudioStream.Id, Level>) {
        data class Level(val current: Int, val min: Int, val max: Int)
    }

    companion object {
        private val TAG = logTag("Monitor", "Event", "Queue")
    }
}