package eu.darken.bluemusic.eq.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Listens for the audio effect control session broadcasts that cooperating player apps send, and
 * keeps a snapshot of the sessions that are currently open.
 *
 * Knows nothing about effects: attaching is [EqEffectController]'s job, deciding when to listen is
 * [EqCoordinator]'s.
 */
@Singleton
class EqSessionTracker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) {

    private val reducer = EqSessionReducer()
    private val lock = Any()
    private var receiver: SessionReceiver? = null
    private var generationCounter = 0L

    private val _state = MutableStateFlow(EqSessionState())
    val state: StateFlow<EqSessionState> = _state.asStateFlow()

    private inner class SessionReceiver(val generation: Long) : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = onBroadcast(generation, intent)
    }

    suspend fun startListening(): Unit = withContext(dispatcherProvider.Default) {
        synchronized(lock) {
            if (receiver != null) {
                log(TAG, VERBOSE) { "startListening(): Already listening" }
                return@synchronized
            }

            val generation = ++generationCounter
            val newReceiver = SessionReceiver(generation)
            val filter = IntentFilter().apply {
                addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            }

            try {
                // These come from other apps and are not protected broadcasts, so on targetSdk 33+
                // the export flag is mandatory and NOT_EXPORTED would never receive them.
                ContextCompat.registerReceiver(context, newReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
                receiver = newReceiver
                reduce { onListeningStarted(it, Instant.now(), generation, "Registered receiver (gen=$generation)") }
            } catch (e: Exception) {
                log(TAG, ERROR) { "startListening(): Registration failed: ${e.asLog()}" }
                reduce { onListeningStopped(it, Instant.now(), "Registration failed: ${e.message}") }
            }
        }
    }

    suspend fun stopListening(): Unit = withContext(dispatcherProvider.Default) {
        synchronized(lock) {
            val current = receiver
            if (current == null) {
                log(TAG, VERBOSE) { "stopListening(): Not listening" }
            } else {
                try {
                    context.unregisterReceiver(current)
                } catch (e: Exception) {
                    log(TAG, WARN) { "stopListening(): Unregister failed: ${e.asLog()}" }
                }
                receiver = null
            }

            val detail = if (current != null) "Unregistered receiver" else "Not listening"
            reduce { onListeningStopped(it, Instant.now(), detail) }
        }
    }

    fun onAttached(sessionId: Int, detail: String) = reduce { onAttached(it, Instant.now(), sessionId, detail) }

    fun onAttachFailed(sessionId: Int, detail: String) = reduce { onAttachFailed(it, Instant.now(), sessionId, detail) }

    fun onDetached(sessionId: Int, detail: String) = reduce { onDetached(it, Instant.now(), sessionId, detail) }

    fun onControlChanged(sessionId: Int, hasControl: Boolean) = reduce {
        onControlChanged(it, Instant.now(), sessionId, hasControl)
    }

    suspend fun clear(): Unit = withContext(dispatcherProvider.Default) {
        reduce { clear(it, Instant.now()) }
    }

    private fun onBroadcast(generation: Long, intent: Intent) = synchronized(lock) {
        // Diagnostic only, session identity is the session id: not every app sends the package.
        val packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
        val sessionId = when {
            intent.hasExtra(AudioEffect.EXTRA_AUDIO_SESSION) -> intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)
            else -> null
        }
        log(TAG, VERBOSE) { "onBroadcast(${intent.action}): package=$packageName session=$sessionId gen=$generation" }

        when (intent.action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> reduce {
                onOpenBroadcast(it, Instant.now(), generation, packageName, sessionId)
            }

            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> reduce {
                onCloseBroadcast(it, Instant.now(), generation, packageName, sessionId)
            }
        }
    }

    private fun reduce(block: EqSessionReducer.(EqSessionState) -> EqSessionState): Unit = synchronized(lock) {
        val old = _state.value
        val new = reducer.block(old)
        _state.value = new
        val newest = new.events.lastOrNull()
        if (newest != null && newest !== old.events.lastOrNull()) log(TAG, VERBOSE) { "Event: $newest" }
    }

    companion object {
        internal val TAG = logTag("Eq", "SessionTracker")
    }
}
