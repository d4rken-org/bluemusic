package eu.darken.bluemusic.monitor.core.modules

import eu.darken.bluemusic.bluetooth.core.SourceDevice
import eu.darken.bluemusic.devices.core.ManagedDevice

interface EventModule {
    suspend fun areRequirementsMet(): Boolean {
        return true
    }

    suspend fun handle(device: ManagedDevice, event: SourceDevice.Event)

    /**
     * When should this module run, lower = earlier, higher = later.
     * Modules with the same priority run in parallel
     */
    val priority: Int
        get() = 10
}
