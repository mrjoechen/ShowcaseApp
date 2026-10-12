package com.alpha.showcase.common.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * A small replicated key-value store owned by the user's cloud account (iCloud KVS on Apple).
 * Writes are local first and replicated by the system; there is no delivery guarantee or ordering.
 */
interface CloudKeyValueStore {
    /** The user is signed in to the cloud account, so values can replicate. */
    val isAvailable: StateFlow<Boolean>

    /** Changes made by other devices or by the system (quota, account switch). */
    val changes: Flow<CloudChange>

    fun get(key: String): String?
    fun set(key: String, value: String)
    fun remove(key: String)

    /** Hints the system to exchange pending changes soon; it does not block until they arrive. */
    fun synchronize()
}

sealed interface CloudChange {
    data class Updated(val keys: Set<String>, val initialSync: Boolean) : CloudChange
    data object AccountChanged : CloudChange
    data object QuotaExceeded : CloudChange
}
