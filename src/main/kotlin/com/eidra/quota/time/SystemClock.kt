package com.eidra.quota.time

import java.time.Instant

class SystemClock : Clock {
    override fun now(): Instant = Instant.now()
}
