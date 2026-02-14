package com.eidra.quota.api

import kotlinx.serialization.Serializable

@Serializable
data class ConsumeRequest(
    val units: Long
)
