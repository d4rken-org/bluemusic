package eu.darken.bluemusic.main.core.service.modules

import eu.darken.bluemusic.main.core.audio.AudioStream

abstract class VolumeModule {

    /**
     * When should this module run, lower = earlier, higher = later.
     * Modules with the same priority run in parallel
     */
    // Default
    val priority: Int
        get() = 10

    abstract fun handle(id: AudioStream.Id, volume: Int)
}
