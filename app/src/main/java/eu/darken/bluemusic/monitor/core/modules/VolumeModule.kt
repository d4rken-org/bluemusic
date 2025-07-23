package eu.darken.bluemusic.monitor.core.modules

import eu.darken.bluemusic.monitor.core.audio.VolumeEvent

interface VolumeModule : EventModule {
    suspend fun handle(event: VolumeEvent)
}
