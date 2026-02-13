package com.eidra.quota.service

/**
 * Result of a consume decision; includes quota metadata for response headers/body.
 */
sealed class QuotaDecision {
    abstract val limit: Long
    abstract val remaining: Long
    abstract val resetAtEpochSeconds: Long

    data class Accepted(
        override val limit: Long,
        override val remaining: Long,
        override val resetAtEpochSeconds: Long
    ) : QuotaDecision()

    data class Rejected(
        override val limit: Long,
        override val remaining: Long,
        override val resetAtEpochSeconds: Long
    ) : QuotaDecision()
}
