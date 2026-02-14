package com.eidra.quota.time

import java.time.Duration
import java.time.Instant

/**
 * Clock for tests: controllable current time
 */
class FakeClock(initialNow: Instant = Instant.EPOCH) : Clock {
    private var _now: Instant = initialNow

    override fun now(): Instant = _now

    fun advanceBy(duration: Duration) {
        _now = _now.plus(duration)
    }

    fun setNow(instant: Instant) {
        _now = instant
    }

}
