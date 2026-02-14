package com.eidra.quota.api

import kotlinx.serialization.Serializable

@Serializable
data class QuotaStatusResponse(
    val limit: Long,
    val remaining: Long,
    val resetAtEpochSeconds: Long
)
