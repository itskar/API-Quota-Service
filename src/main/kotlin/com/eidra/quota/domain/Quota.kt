package com.eidra.quota.domain

import java.time.Duration

data class Quota(
    val limit: Long,
    val window: Duration
)
