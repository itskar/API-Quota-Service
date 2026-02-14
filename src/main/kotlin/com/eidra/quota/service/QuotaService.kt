package com.eidra.quota.service

import com.eidra.quota.domain.KeyUsageState
import com.eidra.quota.domain.Quota
import com.eidra.quota.storage.UsageRepository
import com.eidra.quota.time.Clock
import java.time.Instant

class QuotaService(
    private val quotas: Map<String, Quota>,
    private val repository: UsageRepository,
    private val clock: Clock
) {

    fun consume(apiKey: String, unitsRequested: Long): QuotaDecision {
        require(unitsRequested > 0) { "units must be positive" }
        val quota = quotas[apiKey] ?: throw IllegalArgumentException("Unknown API key")

        return repository.withKeyState(apiKey) { current ->
            val now = clock.now()
            val (windowStart, used) = if (current == null || now.isAfterOrEqualTo(current.windowStart + quota.window)) {
                Pair(now, 0L)
            } else {
                Pair(current.windowStart, current.used)
            }

            val newUsed = used + unitsRequested
            val limit = quota.limit

            // Overflow or over limit: reject, do not update state
            if (newUsed < used || newUsed > limit) {
                val remaining = (limit - used).coerceAtLeast(0L)
                val resetAt = windowStart + quota.window
                val decision = QuotaDecision.Rejected(
                    limit = limit,
                    remaining = remaining,
                    resetAtEpochSeconds = resetAt.epochSecond
                )
                Pair(KeyUsageState(windowStart, used), decision)
            } else {
                val remaining = limit - newUsed
                val resetAt = windowStart + quota.window
                val decision = QuotaDecision.Accepted(
                    limit = limit,
                    remaining = remaining,
                    resetAtEpochSeconds = resetAt.epochSecond
                )
                Pair(KeyUsageState(windowStart, newUsed), decision)
            }
        }
    }

    fun hasQuota(apiKey: String): Boolean = apiKey in quotas
}

private fun Instant.isAfterOrEqualTo(other: Instant): Boolean = !this.isBefore(other)
