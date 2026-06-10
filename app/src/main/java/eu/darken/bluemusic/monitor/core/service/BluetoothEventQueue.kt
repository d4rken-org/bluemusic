package eu.darken.bluemusic.monitor.core.service

import android.os.SystemClock
import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.monitor.core.audio.AudioStream
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothEventQueue @Inject constructor() {

    private val _events = Channel<Event>(Channel.UNLIMITED)
    private val sequenceCounter = AtomicLong(0)

    internal var clock: () -> Long = { SystemClock.elapsedRealtime() }

    val events = _events.receiveAsFlow()

    suspend fun submit(event: Event) {
        log(TAG) { "submit($event)" }
        _events.send(event)
    }

    /**
     * Drops all queued events. Called when monitoring shuts down due to the app being
     * disabled, so stale pre-disable events don't fire after a later re-enable.
     */
    fun clear() {
        var dropped = 0
        while (_events.tryReceive().isSuccess) dropped++
        if (dropped > 0) log(TAG) { "clear(): dropped $dropped stale events" }
    }

    fun stampEvent(
        type: Event.Type,
        sourceDevice: SourceDevice,
        volumeSnapshot: VolumeSnapshot? = null,
    ): Event = Event(
        type = type,
        sourceDevice = sourceDevice,
        volumeSnapshot = volumeSnapshot,
        receivedAtElapsedMs = clock(),
        sequence = sequenceCounter.getAndIncrement(),
    )

    data class Event(
        val type: Type,
        val sourceDevice: SourceDevice,
        val volumeSnapshot: VolumeSnapshot? = null,
        val receivedAtElapsedMs: Long = 0L,
        val sequence: Long = 0L,
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