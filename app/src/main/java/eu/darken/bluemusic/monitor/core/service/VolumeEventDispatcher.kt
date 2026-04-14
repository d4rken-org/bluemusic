package eu.darken.bluemusic.monitor.core.service

import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.monitor.core.audio.VolumeEvent
import eu.darken.bluemusic.monitor.core.modules.VolumeModule
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VolumeEventDispatcher @Inject constructor(
    private val volumeModuleMap: Set<@JvmSuppressWildcards VolumeModule>,
) {

    suspend fun dispatch(event: VolumeEvent) {
        val modulesByPriority = volumeModuleMap
            .groupBy { it.priority }
            .toSortedMap()

        for ((priority, modules) in modulesByPriority) {
            log(TAG, VERBOSE) { "dispatch: ${modules.size} modules at priority $priority" }

            coroutineScope {
                modules.map { module ->
                    async {
                        try {
                            log(TAG, VERBOSE) { "dispatch: ${module.tag} HANDLE-START" }
                            module.handle(event)
                            log(TAG, VERBOSE) { "dispatch: ${module.tag} HANDLE-STOP" }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            log(TAG, ERROR) { "dispatch: error: ${module.tag}: ${e.asLog()}" }
                        }
                    }
                }.awaitAll()
            }
        }
    }

    companion object {
        private val TAG = logTag("Monitor", "Volume", "Dispatcher")
    }
}
