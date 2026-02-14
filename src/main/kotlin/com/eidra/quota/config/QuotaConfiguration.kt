package com.eidra.quota.config

import com.eidra.quota.domain.Quota
import java.time.Duration

object QuotaConfiguration {
    val quotas: Map<String, Quota> = mapOf(
        "client-a" to Quota(limit = 100L, window = Duration.ofHours(1)),
        "client-b" to Quota(limit = 10L, window = Duration.ofMinutes(1))
    )
}
