package eu.darken.bluemusic.eqspike.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.ERROR
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
 * Debug-only spike: listens for audio effect control session broadcasts and attaches an
 * unmistakable equalizer profile to another app's audio session.
 */
@Singleton
class EqSpikeRepo @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) {

    private data class EffectKey(val packageName: String, val sessionId: Int)

    private val reducer = EqSpikeReducer()
    private val lock = Any()
    private val effects = mutableMapOf<EffectKey, Equalizer>()
    private var receiver: BroadcastReceiver? = null

    private val _state = MutableStateFlow(EqSpikeState())
    val state: StateFlow<EqSpikeState> = _state.asStateFlow()

    suspend fun startListening(): Unit = withContext(dispatcherProvider.Default) {
        synchronized(lock) {
            if (receiver != null) {
                log(TAG) { "startListening(): Already listening" }
                return@synchronized
            }

            val newReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = onBroadcast(intent)
            }
            val filter = IntentFilter().apply {
                addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            }

            try {
                // These come from other apps and are not protected broadcasts, so on targetSdk 33+
                // the export flag is mandatory and NOT_EXPORTED would never receive them.
                ContextCompat.registerReceiver(context, newReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
                receiver = newReceiver
                reduce { onListeningChanged(it, Instant.now(), listening = true, detail = "Registered receiver") }
            } catch (e: Exception) {
                log(TAG, ERROR) { "startListening(): Registration failed: ${e.asLog()}" }
                reduce {
                    onListeningChanged(it, Instant.now(), listening = false, detail = "Registration failed: ${e.message}")
                }
            }
        }
    }

    suspend fun stopListening(): Unit = withContext(dispatcherProvider.Default) {
        synchronized(lock) {
            val current = receiver
            if (current == null) {
                log(TAG) { "stopListening(): Not listening" }
                return@synchronized
            }

            try {
                context.unregisterReceiver(current)
            } catch (e: Exception) {
                log(TAG, WARN) { "stopListening(): Unregister failed: ${e.asLog()}" }
            }
            receiver = null

            releaseAll()
            reduce { onListeningChanged(it, Instant.now(), listening = false, detail = "Unregistered receiver") }
        }
    }

    suspend fun attach(packageName: String, sessionId: Int): Unit = withContext(dispatcherProvider.Default) {
        synchronized(lock) {
            if (sessionId <= 0) {
                reduce { onAttachFailed(it, Instant.now(), packageName, sessionId, "Invalid session id") }
                return@synchronized
            }

            val key = EffectKey(packageName, sessionId)
            effects.remove(key)?.let {
                log(TAG) { "attach($key): Replacing existing effect ${it.id}" }
                it.releaseQuietly()
            }

            var equalizer: Equalizer? = null
            try {
                equalizer = Equalizer(EFFECT_PRIORITY, sessionId)
                val detail = equalizer.applyMuffleProfile(key)
                equalizer.setEnabled(true)
                equalizer.setControlStatusListener { _, controlGranted ->
                    log(TAG) { "Control status for $key: granted=$controlGranted" }
                    reduce { onControlChanged(it, Instant.now(), packageName, sessionId, controlGranted) }
                }
                effects[key] = equalizer
                reduce { onAttached(it, Instant.now(), packageName, sessionId, detail) }
            } catch (e: Throwable) {
                log(TAG, ERROR) { "attach($key): Failed: ${e.asLog()}" }
                equalizer?.releaseQuietly()
                reduce {
                    onAttachFailed(it, Instant.now(), packageName, sessionId, e.message ?: e.toString())
                }
            }
        }
    }

    suspend fun detach(packageName: String, sessionId: Int): Unit = withContext(dispatcherProvider.Default) {
        synchronized(lock) {
            val key = EffectKey(packageName, sessionId)
            val effect = effects.remove(key)
            log(TAG) { "detach($key): effect=$effect" }
            effect?.releaseQuietly()
            reduce {
                onDetached(
                    it,
                    Instant.now(),
                    packageName,
                    sessionId,
                    detail = if (effect != null) "Released effect" else "Nothing attached",
                )
            }
        }
    }

    suspend fun clear(): Unit = withContext(dispatcherProvider.Default) {
        synchronized(lock) {
            releaseAll()
            reduce { this.clear(it, Instant.now()) }
        }
    }

    private fun onBroadcast(intent: Intent): Unit = synchronized(lock) {
        val packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
        val sessionId = when {
            intent.hasExtra(AudioEffect.EXTRA_AUDIO_SESSION) -> intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)
            else -> null
        }
        log(TAG) { "onBroadcast(${intent.action}): package=$packageName session=$sessionId" }

        when (intent.action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                reduce { onOpenBroadcast(it, Instant.now(), packageName, sessionId) }
            }

            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                if (packageName != null && sessionId != null) {
                    val key = EffectKey(packageName, sessionId)
                    effects.remove(key)?.let {
                        log(TAG) { "onBroadcast(): Releasing effect for closed session $key" }
                        it.releaseQuietly()
                    }
                }
                reduce { onCloseBroadcast(it, Instant.now(), packageName, sessionId) }
            }
        }
    }

    private fun Equalizer.applyMuffleProfile(key: EffectKey): String {
        val range = bandLevelRange
        val min = range[0]
        val max = range[1]
        val bandCount = numberOfBands

        for (band in 0 until bandCount) {
            // getCenterFreq() is in milliHertz, so 2000Hz is 2_000_000
            if (getCenterFreq(band.toShort()) >= MUFFLE_CUTOFF_MHZ) setBandLevel(band.toShort(), min)
        }
        if (bandCount > 0) setBandLevel(0.toShort(), max)

        val levels = (0 until bandCount).map { getBandLevel(it.toShort()) }
        val descriptorInfo = try {
            descriptor.let { "${it.name}/${it.implementor}/${it.uuid}" }
        } catch (e: Exception) {
            "unavailable (${e.message})"
        }
        log(TAG) { "applyMuffleProfile($key): id=$id descriptor=$descriptorInfo bands=$bandCount levels=$levels range=$min..$max" }

        return "id=$id bands=$bandCount levels=$levels"
    }

    private fun releaseAll() {
        effects.forEach { (key, effect) ->
            log(TAG) { "releaseAll(): Releasing effect for $key" }
            effect.releaseQuietly()
        }
        effects.clear()
    }

    private fun Equalizer.releaseQuietly() = try {
        release()
    } catch (e: Exception) {
        log(TAG, WARN) { "release() failed: ${e.asLog()}" }
    }

    private fun reduce(block: EqSpikeReducer.(EqSpikeState) -> EqSpikeState): Unit = synchronized(lock) {
        val old = _state.value
        val new = reducer.block(old)
        _state.value = new
        val newest = new.events.lastOrNull()
        if (newest != null && newest !== old.events.lastOrNull()) log(TAG) { "Event: $newest" }
    }

    companion object {
        internal val TAG = logTag("EqSpike", "Repo")
        private const val EFFECT_PRIORITY = 1000
        private const val MUFFLE_CUTOFF_MHZ = 2_000_000
    }
}
