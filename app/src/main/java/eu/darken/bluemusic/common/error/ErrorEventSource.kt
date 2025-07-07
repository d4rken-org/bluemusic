package eu.darken.butler.common.error

import eu.darken.butler.common.flow.SingleEventFlow

interface ErrorEventSource {
    val errorEvents: SingleEventFlow<Throwable>
}