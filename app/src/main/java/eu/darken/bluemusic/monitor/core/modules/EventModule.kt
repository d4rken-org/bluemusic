package eu.darken.bluemusic.monitor.core.modules

interface EventModule {

    /**
     * When should this module run, lower = earlier, higher = later.
     * Modules with the same priority run in parallel
     */
    val priority: Int
        get() = 10

    val tag: String
}
