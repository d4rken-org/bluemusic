package eu.darken.bluemusic.eq.ui

import android.content.pm.PackageManager
import eu.darken.bluemusic.common.coroutine.DispatcherProvider
import eu.darken.bluemusic.common.debug.logging.Logging.Priority.WARN
import eu.darken.bluemusic.common.debug.logging.asLog
import eu.darken.bluemusic.common.debug.logging.log
import eu.darken.bluemusic.common.debug.logging.logTag
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puts a face to the package name an effect session came with.
 *
 * A package we can't resolve stays unresolved: the name arrives on an unverified broadcast and is
 * never ours to show. The manifest `<queries>` covers launcher apps, which is every realistic player.
 */
@Singleton
class EqAppResolver @Inject constructor(
    private val packageManager: PackageManager,
    private val dispatcherProvider: DispatcherProvider,
) {

    private val cacheLock = Mutex()

    /**
     * Bounded, and the least recently used entry goes first: the package names come from an
     * unverified broadcast, so a misbehaving app must not be able to grow this without end.
     */
    private val cache = object : LinkedHashMap<String, EqStatusApp>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EqStatusApp>): Boolean =
            size > CACHE_SIZE
    }

    /** The same status, with the app it names resolved for display. */
    suspend fun resolved(status: EqStatus): EqStatus = when (status) {
        is EqStatus.Active -> status.copy(app = status.app?.let { resolve(it) })
        is EqStatus.NoControl -> status.copy(app = status.app?.let { resolve(it) })
        else -> status
    }

    suspend fun resolve(app: EqStatusApp): EqStatusApp = cacheLock.withLock {
        cache.getOrPut(app.packageName) {
            withContext(dispatcherProvider.IO) { load(app.packageName) }
        }
    }

    private fun load(packageName: String): EqStatusApp = try {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        EqStatusApp(
            packageName = packageName,
            label = appInfo.loadLabel(packageManager).toString(),
            icon = appInfo.loadIcon(packageManager),
        )
    } catch (e: Exception) {
        log(TAG, WARN) { "Failed to resolve $packageName: ${e.asLog()}" }
        EqStatusApp(packageName)
    }

    companion object {
        private val TAG = logTag("Eq", "AppResolver")

        /** Upper bound on remembered app resolutions, successful or not. */
        private const val CACHE_SIZE = 32
    }
}
