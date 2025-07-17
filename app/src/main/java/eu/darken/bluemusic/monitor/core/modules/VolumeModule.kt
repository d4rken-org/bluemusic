package eu.darken.bluemusic.monitor.core.modules

import eu.darken.bluemusic.monitor.core.audio.VolumeEvent

interface VolumeModule {

    /**
     * When should this module run, lower = earlier, higher = later.
     * Modules with the same priority run in parallel
     */

    val priority: Int
        get() = 10  // Default

    suspend fun handle(event: VolumeEvent)
}
