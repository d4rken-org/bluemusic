package eu.darken.bluemusic.monitor.core.modules

interface ConnectionModule : EventModule {
    suspend fun handle(event: DeviceEvent)
}
