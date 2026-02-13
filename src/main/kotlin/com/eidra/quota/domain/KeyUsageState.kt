package com.eidra.quota.domain

import java.time.Instant

/**
 * Per-key usage state for the current fixed window.
 */
data class KeyUsageState(
    val windowStart: Instant,
    val used: Long
)
