package testhelpers.time

import eu.darken.bluemusic.common.time.MonotonicClock

class FakeMonotonicClock(var now: Long = 0L) : MonotonicClock {
    override fun nowMs(): Long = now
}
