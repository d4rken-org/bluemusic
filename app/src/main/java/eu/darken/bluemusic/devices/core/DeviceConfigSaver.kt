package eu.darken.bluemusic.devices.core

import eu.darken.bluemusic.common.coroutine.AppScope
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import eu.darken.bluemusic.devices.core.database.DeviceConfigEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists device config edits on the app scope instead of the screen's.
 *
 * Releasing a slider and navigating back in the same moment would otherwise cancel the write the user
 * just committed: it ran in the ViewModel's scope, which dies with the screen. Every edit is handed to
 * a single actor that outlives any screen and applies the writes in submission order, so a later edit
 * can never be overtaken by an earlier one.
 */
@Singleton
class DeviceConfigSaver @Inject constructor(
    @param:AppScope private val appScope: CoroutineScope,
    private val deviceRepo: DeviceRepo,
) {

    private data class Write(
        val address: DeviceAddr,
        val update: (DeviceConfigEntity) -> DeviceConfigEntity,
        val completion: CompletableDeferred<Unit>,
    )

    private val writes = Channel<Write>(Channel.UNLIMITED)

    init {
        appScope.launch {
            for (write in writes) {
                try {
                    deviceRepo.updateDevice(write.address, write.update)
                    write.completion.complete(Unit)
                } catch (e: CancellationException) {
                    write.completion.cancel(e)
                    throw e
                } catch (e: Exception) {
                    log(TAG, ERROR) { "save(${write.address}) failed: ${e.asLog()}" }
                    write.completion.completeExceptionally(e)
                }
            }
        }
    }

    /**
     * Queues [update] for [address] and returns when the write has landed.
     *
     * The returned handle is only there for callers that want to sequence something after the write,
     * e.g. clearing a live preview. Dropping it does not cancel anything.
     */
    fun save(address: DeviceAddr, update: (DeviceConfigEntity) -> DeviceConfigEntity): Deferred<Unit> {
        log(TAG, VERBOSE) { "save($address)" }
        val write = Write(address = address, update = update, completion = CompletableDeferred())
        val result = writes.trySend(write)
        if (result.isFailure) {
            log(TAG, ERROR) { "save($address): Queue is gone, dropping the write" }
            write.completion.completeExceptionally(
                result.exceptionOrNull() ?: IllegalStateException("Config write queue is closed")
            )
        }
        return write.completion
    }

    companion object {
        private val TAG = logTag("Devices", "ConfigSaver")
    }
}
