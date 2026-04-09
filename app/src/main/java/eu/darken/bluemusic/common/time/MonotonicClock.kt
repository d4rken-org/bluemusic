package eu.darken.bluemusic.common.time

import android.os.SystemClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monotonic time source.
 *
 * Returns milliseconds since an arbitrary but consistent epoch (device boot).
 * Unlike [System.currentTimeMillis], it is NOT affected by wall-clock changes
 * (NTP resync, user adjustment, time-zone / DST shifts). Use this for interval
 * measurements (timeouts, TTLs, debouncing) where "how long ago did X happen?"
 * must remain accurate across clock jumps.
 *
 * Backed by [SystemClock.elapsedRealtime] in production. A fake implementation
 * can be supplied in unit tests since [SystemClock] is part of the Android
 * framework stubs and throws in plain JVM tests.
 */
interface MonotonicClock {
    fun nowMs(): Long
}

@Singleton
class AndroidMonotonicClock @Inject constructor() : MonotonicClock {
    override fun nowMs(): Long = SystemClock.elapsedRealtime()

    @Module @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds abstract fun bind(impl: AndroidMonotonicClock): MonotonicClock
    }
}
