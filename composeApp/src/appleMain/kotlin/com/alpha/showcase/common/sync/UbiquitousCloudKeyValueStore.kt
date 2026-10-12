package com.alpha.showcase.common.sync

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSUbiquitousKeyValueStore
import platform.Foundation.NSUbiquitousKeyValueStoreAccountChange
import platform.Foundation.NSUbiquitousKeyValueStoreChangeReasonKey
import platform.Foundation.NSUbiquitousKeyValueStoreChangedKeysKey
import platform.Foundation.NSUbiquitousKeyValueStoreDidChangeExternallyNotification
import platform.Foundation.NSUbiquitousKeyValueStoreInitialSyncChange
import platform.Foundation.NSUbiquitousKeyValueStoreQuotaViolationChange
import platform.Foundation.NSUbiquityIdentityDidChangeNotification

/**
 * iCloud key-value storage. Requires the `com.apple.developer.ubiquity-kvstore-identifier`
 * entitlement; without it `synchronize()` fails and sync reports itself unavailable.
 *
 * `NSFileManager.ubiquityIdentityToken` stays nil for apps without an iCloud container, so the
 * signed-out state cannot be detected here; values then stay on the device until the user signs in.
 */
internal class UbiquitousCloudKeyValueStore : CloudKeyValueStore {
    private val store = NSUbiquitousKeyValueStore.defaultStore
    private val mutableAvailable = MutableStateFlow(store.synchronize())
    override val isAvailable: StateFlow<Boolean> = mutableAvailable.asStateFlow()

    init {
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = NSUbiquityIdentityDidChangeNotification,
            `object` = null,
            queue = null,
        ) { _ -> mutableAvailable.value = store.synchronize() }
    }

    override val changes: Flow<CloudChange> = callbackFlow {
        val center = NSNotificationCenter.defaultCenter
        val observer = center.addObserverForName(
            name = NSUbiquitousKeyValueStoreDidChangeExternallyNotification,
            `object` = store,
            queue = null,
        ) { notification ->
            val info = notification?.userInfo
            val reason = (info?.get(NSUbiquitousKeyValueStoreChangeReasonKey) as? NSNumber)?.longValue
            val keys = (info?.get(NSUbiquitousKeyValueStoreChangedKeysKey) as? List<*>)
                ?.filterIsInstance<String>()
                ?.toSet()
                .orEmpty()
            trySend(
                when (reason) {
                    NSUbiquitousKeyValueStoreQuotaViolationChange -> CloudChange.QuotaExceeded
                    NSUbiquitousKeyValueStoreAccountChange -> CloudChange.AccountChanged
                    else -> CloudChange.Updated(keys, initialSync = reason == NSUbiquitousKeyValueStoreInitialSyncChange)
                },
            )
        }
        // Apple asks apps to synchronize once the observer is in place, so launch-time changes are delivered.
        mutableAvailable.value = store.synchronize()
        awaitClose { center.removeObserver(observer) }
    }

    override fun get(key: String): String? = store.stringForKey(key)

    override fun set(key: String, value: String) {
        store.setString(value, forKey = key)
    }

    override fun remove(key: String) {
        store.removeObjectForKey(key)
    }

    override fun synchronize() {
        mutableAvailable.value = store.synchronize()
    }
}
