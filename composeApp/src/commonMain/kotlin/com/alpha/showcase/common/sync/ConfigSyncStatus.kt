package com.alpha.showcase.common.sync

/** User-visible state of configuration sync. */
sealed interface ConfigSyncStatus {
    /** This platform has no cloud key-value store. */
    data object Unsupported : ConfigSyncStatus
    data object Disabled : ConfigSyncStatus
    /** Sync is on, but the user is not signed in to the cloud account. */
    data object Unavailable : ConfigSyncStatus
    data object Syncing : ConfigSyncStatus
    data class Synced(val at: Long) : ConfigSyncStatus
    /** Part of the cloud data has not reached this device yet. */
    data object Waiting : ConfigSyncStatus
    /** Another device wrote data in a format this version cannot merge safely. */
    data object NeedsUpgrade : ConfigSyncStatus
    data object QuotaExceeded : ConfigSyncStatus
    /** The cloud account changed; sync paused until the user turns it on again. */
    data object AccountChanged : ConfigSyncStatus
    data class Failed(val message: String) : ConfigSyncStatus
}
