package com.eidra.quota.time

import java.time.Instant

interface Clock {
    fun now(): Instant
}
