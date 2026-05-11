package eu.darken.bluemusic.monitor.core.modules

interface ConnectionModule : EventModule {

    /**
     * Whether this module wants to run for the given event. The dispatcher filters modules
     * by this before deciding whether to pay the actionDelay barrier. Defaults to true so
     * tests with relaxed mocks still see modules scheduled — concrete production modules
     * must override and mirror their own internal early-return logic to avoid wasting the
     * barrier on dispatches where nothing useful happens.
     */
    fun appliesTo(event: DeviceEvent): Boolean = true

    /**
     * When this module runs relative to the dispatcher's actionDelay barrier, for this
     * specific event. Lets a module use Immediate for one event type and AfterDeviceSettle
     * for another — e.g. KeepAwakeModule wakes immediately on Connected but waits the
     * device-settle barrier on Disconnected.
     */
    fun settlePolicy(event: DeviceEvent): SettlePolicy = SettlePolicy.AfterDeviceSettle

    suspend fun handle(event: DeviceEvent)

    val cancellable: Boolean
        get() = true
}
