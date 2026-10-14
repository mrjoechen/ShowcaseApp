package com.alpha.showcase.common.ai

import com.alpha.showcase.common.storage.ObjectStore
import io.ktor.http.Url
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/** Hosts the user explicitly agreed to send photos to for AI summaries. */
@Serializable
internal data class AiUploadConsentRecord(val hosts: Set<String> = emptySet())

/** The host named in the consent prompt. Consent covers every path and port on that host. */
internal fun AiProfile.consentHost(): String = runCatching { Url(baseUrl).host.lowercase() }.getOrDefault("")

/**
 * Explicit permission to upload photos to a configured AI service, recorded per host on this device
 * only, so each device asks before its first upload. Where it is not required, uploads are allowed.
 */
internal class AiUploadConsent(
    private val required: () -> Boolean,
    private val store: ObjectStore<AiUploadConsentRecord>,
) {
    private val mutex = Mutex()
    private val mutableHosts = MutableStateFlow<Set<String>?>(null)

    /** Hosts the user agreed to, or null until [load] has read them. */
    val hosts: StateFlow<Set<String>?> = mutableHosts.asStateFlow()

    val isRequired: Boolean get() = required()

    suspend fun load(): Set<String> = mutex.withLock { loadLocked() }

    /** Unloaded [hosts] count as not consented, so callers fail closed. */
    fun needsConsent(profile: AiProfile, hosts: Set<String>?): Boolean =
        required() && (hosts == null || profile.consentHost() !in hosts)

    suspend fun isGranted(profile: AiProfile): Boolean = !required() || !needsConsent(profile, load())

    suspend fun grant(profile: AiProfile) {
        val host = profile.consentHost()
        require(host.isNotEmpty()) { "AI service URL has no host" }
        mutex.withLock {
            val updated = loadLocked() + host
            store.set(AiUploadConsentRecord(updated))
            mutableHosts.value = updated
        }
    }

    /** Must be called while [mutex] is held. */
    private suspend fun loadLocked(): Set<String> =
        mutableHosts.value ?: store.get()?.hosts.orEmpty().also { mutableHosts.value = it }

    companion object {
        /** For platforms and tests where uploads need no prompt; the store is never read. */
        val NotRequired = AiUploadConsent({ false }, object : ObjectStore<AiUploadConsentRecord> {
            override suspend fun set(value: AiUploadConsentRecord) = error("Consent is not required")
            override suspend fun delete() = Unit
            override suspend fun get(): AiUploadConsentRecord? = null
        })
    }
}
