package com.eidra.quota.storage

import com.eidra.quota.domain.KeyUsageState
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock


internal class KeyStateHolder {
    var state: KeyUsageState? = null
    val lock = ReentrantLock()
}

class InMemoryUsageRepository : UsageRepository {
    private val keyHolders = ConcurrentHashMap<String, KeyStateHolder>()

    override fun <T> withKeyState(apiKey: String, block: (KeyUsageState?) -> Pair<KeyUsageState, T>): T {
        val holder = keyHolders.computeIfAbsent(apiKey) { KeyStateHolder() }
        holder.lock.lock()
        try {
            val (newState, result) = block(holder.state)
            holder.state = newState
            return result
        } finally {
            holder.lock.unlock()
        }
    }
}
