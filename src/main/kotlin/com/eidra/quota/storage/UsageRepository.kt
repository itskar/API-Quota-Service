package com.eidra.quota.storage

import com.eidra.quota.domain.KeyUsageState

interface UsageRepository {
    /**
     * Execute [block] under per-key lock; block receives current state (or null) and returns new state to store.
     * Repository guarantees atomic load-compute-store for the key.
     */
    fun <T> withKeyState(apiKey: String, block: (KeyUsageState?) -> Pair<KeyUsageState, T>): T
}
