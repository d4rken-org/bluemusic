package eu.darken.bluemusic.monitor.core.modules

import eu.darken.bluemusic.common.debug.logging.log
import kotlinx.coroutines.delay

suspend fun ConnectionModule.delayForReactionDelay(event: DeviceEvent) {
    val delay = event.device.actionDelay
    log(tag) { "Delaying reaction by $delay for $this." }
    delay(delay.toMillis())
}