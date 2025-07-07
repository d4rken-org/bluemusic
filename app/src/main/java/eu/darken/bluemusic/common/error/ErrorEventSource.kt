package eu.darken.bluemusic.common.error

import eu.darken.bluemusic.common.flow.SingleEventFlow

interface ErrorEventSource {
    val errorEvents: SingleEventFlow<Throwable>
}