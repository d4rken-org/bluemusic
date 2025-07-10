package eu.darken.bluemusic.monitor.core.modules

import eu.darken.bluemusic.main.core.audio.AudioStream

interface VolumeModule {

    /**
     * When should this module run, lower = earlier, higher = later.
     * Modules with the same priority run in parallel
     */

    val priority: Int
        get() = 10  // Default

    suspend fun handle(id: AudioStream.Id, volume: Int)
}
