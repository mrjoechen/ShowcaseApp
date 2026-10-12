@file:OptIn(ExperimentalTime::class)
package com.alpha.showcase.common.repo

import com.alpha.showcase.common.cache.GallerySourceMediaStore
import com.alpha.showcase.common.networkfile.storage.StorageSources
import com.alpha.showcase.common.networkfile.storage.drive.DropBox
import com.alpha.showcase.common.networkfile.storage.drive.GoogleDrive
import com.alpha.showcase.common.networkfile.storage.drive.GooglePhotos
import com.alpha.showcase.common.networkfile.storage.drive.OneDrive
import com.alpha.showcase.common.networkfile.storage.remote.Ftp
import com.alpha.showcase.common.networkfile.storage.remote.GitHubSource
import com.alpha.showcase.common.networkfile.storage.remote.GallerySource
import com.alpha.showcase.common.networkfile.storage.remote.GiteeSource
import com.alpha.showcase.common.networkfile.storage.remote.ImmichSource
import com.alpha.showcase.common.networkfile.storage.remote.MTPHOTO_AUTH_TYPE_API_KEY
import com.alpha.showcase.common.networkfile.storage.remote.MTPHOTO_AUTH_TYPE_PASSWORD
import com.alpha.showcase.common.networkfile.storage.remote.Local
import com.alpha.showcase.common.networkfile.storage.remote.MTPhotoSource
import com.alpha.showcase.common.networkfile.storage.remote.PexelsSource
import com.alpha.showcase.common.networkfile.storage.remote.RcloneRemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteStorageImpl
import com.alpha.showcase.common.networkfile.storage.remote.S3Source
import com.alpha.showcase.common.networkfile.storage.remote.Sftp
import com.alpha.showcase.common.networkfile.storage.remote.Smb
import com.alpha.showcase.common.networkfile.storage.remote.TMDBSource
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import com.alpha.showcase.common.networkfile.util.StorageSourceSerializer
import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.storage.objectStoreOf
import com.alpha.showcase.common.utils.isCurrentConfigCiphertext
import com.alpha.showcase.common.utils.runConnectionProbe
import com.alpha.showcase.common.versionCode
import com.alpha.showcase.common.versionName
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import randomUUID
import kotlin.time.ExperimentalTime

internal expect fun defaultRemoteSources(): MutableList<RemoteApi>

class SourceListRepo {
    private val store = objectStoreOf<String>("sources")
    private val galleryMediaStore by lazy {
        GallerySourceMediaStore()
    }

    private val repoManager by lazy {
        RepoManager()
    }


    private fun defaultValue() =
        StorageSources(
            versionCode.toInt(),
            versionName,
            randomUUID(),
            "default",
            Clock.System.now().toEpochMilliseconds(),
            defaultRemoteSources(),
        )

    suspend fun getSources(): StorageSources = sourceMutationMutex.withLock {
        getSourcesUnlocked()
    }

    private suspend fun getSourcesUnlocked(): StorageSources {
        val storedValue = store.get() ?: return defaultValue()
        val rawJson = RConfig.decryptAsync(storedValue)
        val decoded = StorageSourceSerializer.sourceJson.decodeFromString(
            StorageSources.serializer(),
            rawJson,
        )
        val (normalized, sensitiveFieldsChanged) = normalizeSensitiveFields(decoded)
        if (sensitiveFieldsChanged || !storedValue.isCurrentConfigCiphertext()) {
            writeSources(normalized)
        }
        return normalized
    }


    suspend fun setSources(sources: StorageSources) = sourceMutationMutex.withLock {
        setSourcesUnlocked(sources)
    }

    private suspend fun setSourcesUnlocked(sources: StorageSources) {
        val (normalized, _) = normalizeSensitiveFields(sources)
        writeSources(normalized)
        mutableLocalEdits.tryEmit(Unit)
    }

    /** Sources shared with the user's other devices, in display order and with plaintext credentials. */
    internal suspend fun readSyncableSources(): List<RemoteApi> = sourceMutationMutex.withLock {
        val syncable = getSourcesUnlocked().sources.filter { it.isSyncable() }
        transformSensitiveFields(syncable) { RConfig.decryptAsync(it) }.first
    }

    /**
     * Replaces the syncable sources with [synced] while keeping device-only sources at their positions.
     * Credentials in [synced] are plaintext and get encrypted with this device's key.
     * Not reported through [localEdits].
     */
    internal suspend fun applySyncedSources(synced: List<RemoteApi>) = sourceMutationMutex.withLock {
        val current = getSourcesUnlocked()
        val merged = mergeSyncedSources(current.sources, synced)
        val incoming = merged.filter { it.isSyncable() }

        val previousNames = current.sources.filter { it.isSyncable() }.associateBy { it.name }
        val incomingNames = incoming.mapTo(mutableSetOf()) { it.name }
        val cacheService = com.alpha.showcase.common.cache.NetworkFileCacheService.shared
        val removed = previousNames.values.filter { it.name !in incomingNames }
        removed.forEach { source ->
            runCatching {
                cacheService.deleteSource(source)
                galleryMediaStore.deleteSource(source.name)
            }
        }
        if (removed.isNotEmpty()) runCatching { com.alpha.showcase.common.cache.SourceDerivedCache.clear() }
        writeSources(normalizeSensitiveFields(current.copy(sources = merged.toMutableList())).first)
        incoming.filter { it.name !in previousNames }.forEach { cacheService.allowSource(it) }
    }

    private suspend fun writeSources(sources: StorageSources) {
        val rawJson = StorageSourceSerializer.sourceJson.encodeToString(
            StorageSources.serializer(),
            sources
        )
        store.set(RConfig.encryptAsync(rawJson))
    }

    suspend fun addSource(source: RemoteApi) {
        saveSource(source)
    }

    suspend fun saveSource(remoteApi: RemoteApi): Boolean = sourceMutationMutex.withLock {
        val storageSources = getSourcesUnlocked()
        if (storageSources.sources.any { it.name == remoteApi.name }) return@withLock false
        val updatedSources = storageSources.copy(
            sources = (storageSources.sources + remoteApi).toMutableList(),
        )
        setSourcesUnlocked(updatedSources)
        com.alpha.showcase.common.cache.NetworkFileCacheService.shared.allowSource(remoteApi)
        true
    }

    suspend fun replaceSource(previous: RemoteApi, replacement: RemoteApi): Boolean =
        sourceMutationMutex.withLock {
            val storageSources = getSourcesUnlocked()
            val previousIndex = storageSources.sources.indexOfFirst { it.name == previous.name }
            if (previousIndex < 0) return@withLock false
            if (storageSources.sources.withIndex().any { (index, source) ->
                    index != previousIndex && source.name == replacement.name
                }
            ) {
                return@withLock false
            }

            val updatedList = storageSources.sources.toMutableList().apply {
                this[previousIndex] = replacement
            }
            setSourcesUnlocked(storageSources.copy(sources = updatedList))
            true
        }

    suspend fun deleteSource(remoteApi: RemoteApi): Boolean = sourceMutationMutex.withLock {
        val oldSources = getSourcesUnlocked()
        if (oldSources.sources.none { it.name == remoteApi.name }) return@withLock false
        try {
            com.alpha.showcase.common.cache.NetworkFileCacheService.shared.deleteSource(remoteApi)
            galleryMediaStore.deleteSource(remoteApi.name)
            com.alpha.showcase.common.cache.SourceDerivedCache.clear()
            setSourcesUnlocked(oldSources.copy(
                sources = oldSources.sources.filterNot { it.name == remoteApi.name }.toMutableList(),
            ))
        } catch (error: Throwable) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                com.alpha.showcase.common.cache.NetworkFileCacheService.shared.allowSource(remoteApi)
            }
            throw error
        }
        true
    }

    suspend fun getSourceFileDirItems(
        remoteApi: RcloneRemoteApi,
        path: String,
        timeout: Long = 10000,
    ): Result<List<Any>> = runConnectionProbe(timeout) {
        repoManager.getFileDirItems(remoteApi, path)
    }

    suspend fun checkConnection(remoteApi: RemoteApi, timeout: Long = 10000): Result<Any> =
        runConnectionProbe(timeout) { repoManager.checkConnection(remoteApi) }

    private suspend fun normalizeSensitiveFields(storageSources: StorageSources): Pair<StorageSources, Boolean> {
        val (normalized, changed) = transformSensitiveFields(storageSources.sources) { RConfig.encryptAsync(it) }
        if (!changed) {
            return storageSources to false
        }
        return storageSources.copy(sources = normalized.toMutableList()) to true
    }

    /** Applies [transform] to every stored credential; reports whether any value changed. */
    private suspend fun transformSensitiveFields(
        sources: List<RemoteApi>,
        transform: suspend (String) -> String,
    ): Pair<List<RemoteApi>, Boolean> {
        var changed = false
        val transformed = sources.map { source ->
            when (source) {
                is Smb -> {
                    val encryptedPass = transform(source.passwd)
                    if (encryptedPass != source.passwd) changed = true
                    source.copy(passwd = encryptedPass)
                }

                is Ftp -> {
                    val encryptedPass = transform(source.passwd)
                    if (encryptedPass != source.passwd) changed = true
                    source.copy(passwd = encryptedPass)
                }

                is Sftp -> {
                    val encryptedPass = transform(source.passwd)
                    if (encryptedPass != source.passwd) changed = true
                    source.copy(passwd = encryptedPass)
                }

                is WebDav -> {
                    val encryptedPass = transform(source.passwd)
                    if (encryptedPass != source.passwd) changed = true
                    source.copy(passwd = encryptedPass)
                }

                is RemoteStorageImpl -> {
                    val encryptedPass = transform(source.passwd)
                    if (encryptedPass != source.passwd) changed = true
                    RemoteStorageImpl(
                        id = source.id,
                        host = source.host,
                        port = source.port,
                        user = source.user,
                        passwd = encryptedPass,
                        name = source.name,
                        path = source.path,
                        isCrypt = source.isCrypt,
                        description = source.description,
                        addTime = source.addTime,
                        lock = source.lock,
                        schema = source.schema,
                    )
                }

                is GitHubSource -> {
                    val encryptedToken = transform(source.token)
                    if (encryptedToken != source.token) changed = true
                    GitHubSource(
                        name = source.name,
                        repoUrl = source.repoUrl,
                        token = encryptedToken,
                        path = source.path,
                        branchName = source.branchName
                    )
                }

                is GiteeSource -> {
                    val encryptedToken = transform(source.token)
                    if (encryptedToken != source.token) changed = true
                    GiteeSource(
                        name = source.name,
                        repoUrl = source.repoUrl,
                        token = encryptedToken,
                        path = source.path,
                        branchName = source.branchName
                    )
                }

                is ImmichSource -> {
                    val encryptedApiKey = source.apiKey?.let { transform(it) }
                    val encryptedPass = source.pass?.let { transform(it) }
                    if (encryptedApiKey != source.apiKey || encryptedPass != source.pass) changed = true
                    ImmichSource(
                        name = source.name,
                        url = source.url,
                        port = source.port,
                        authType = source.authType,
                        apiKey = encryptedApiKey,
                        user = source.user,
                        pass = encryptedPass,
                        album = source.album
                    )
                }

                is MTPhotoSource -> {
                    val normalized = source.withTransformedCredentials(transform)
                    if (normalized != source) changed = true
                    normalized
                }

                is S3Source -> {
                    val encryptedSecretKey = transform(source.secretKey)
                    val normalized = if (encryptedSecretKey == source.secretKey) {
                        source
                    } else {
                        source.copy(secretKey = encryptedSecretKey)
                    }
                    if (normalized.secretKey != source.secretKey) changed = true
                    normalized
                }

                is PexelsSource -> {
                    val storedApiKey = source.extra[PEXELS_API_KEY_KEY]
                    val encryptedApiKey = storedApiKey?.let { transform(it) }
                    val normalized = if (storedApiKey == null || encryptedApiKey == storedApiKey) {
                        source
                    } else {
                        PexelsSource(
                            name = source.name,
                            photoType = source.photoType,
                            extra = source.extra + (PEXELS_API_KEY_KEY to encryptedApiKey.orEmpty()),
                        )
                    }
                    if (normalized.extra != source.extra) changed = true
                    normalized
                }

                is UnSplashSource -> {
                    val encryptedApiKey = source.apiKey?.let { transform(it) }
                    val normalized = if (encryptedApiKey == source.apiKey) {
                        source
                    } else {
                        UnSplashSource(
                            name = source.name,
                            photoType = source.photoType,
                            user = source.user,
                            collectionId = source.collectionId,
                            topic = source.topic,
                            orientation = source.orientation,
                            apiKey = encryptedApiKey,
                        )
                    }
                    if (normalized.apiKey != source.apiKey) changed = true
                    normalized
                }

                is TMDBSource -> {
                    val encryptedApiToken = source.apiToken?.let { transform(it) }
                    val normalized = if (encryptedApiToken == source.apiToken) {
                        source
                    } else {
                        TMDBSource(
                            name = source.name,
                            contentType = source.contentType,
                            language = source.language,
                            region = source.region,
                            imageType = source.imageType,
                            apiToken = encryptedApiToken,
                        )
                    }
                    if (normalized.apiToken != source.apiToken) changed = true
                    normalized
                }

                is GoogleDrive -> {
                    val encryptedToken = transform(source.token)
                    if (encryptedToken != source.token) changed = true
                    GoogleDrive(
                        name = source.name,
                        token = encryptedToken,
                        scope = source.scope,
                        folderId = source.folderId,
                        path = source.path
                    )
                }

                is GooglePhotos -> {
                    val encryptedToken = transform(source.token)
                    if (encryptedToken != source.token) changed = true
                    GooglePhotos(
                        name = source.name,
                        token = encryptedToken,
                        path = source.path
                    )
                }

                is OneDrive -> {
                    val encryptedToken = transform(source.token)
                    if (encryptedToken != source.token) changed = true
                    OneDrive(
                        name = source.name,
                        token = encryptedToken,
                        driveId = source.driveId,
                        driveType = source.driveType,
                        path = source.path
                    )
                }

                is DropBox -> {
                    val encryptedToken = transform(source.token)
                    if (encryptedToken != source.token) changed = true
                    DropBox(
                        name = source.name,
                        token = encryptedToken,
                        path = source.path
                    )
                }

                else -> source
            }
        }
        return (if (changed) transformed else sources) to changed
    }

    internal companion object {
        private val sourceMutationMutex = Mutex()
        private val mutableLocalEdits = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

        /** Emits after sources are changed on this device. */
        val localEdits: SharedFlow<Unit> = mutableLocalEdits.asSharedFlow()
    }

}

/**
 * Places the [synced] sources in their synced order and re-inserts device-only sources at their
 * previous positions. Synced sources whose names collide with a device-only source are dropped.
 */
internal fun mergeSyncedSources(current: List<RemoteApi>, synced: List<RemoteApi>): List<RemoteApi> {
    val deviceOnly = current.withIndex().filterNot { it.value.isSyncable() }
    val deviceOnlyNames = deviceOnly.mapTo(mutableSetOf()) { it.value.name }
    val merged = synced.filter { it.isSyncable() && it.name !in deviceOnlyNames }.distinctBy { it.name }.toMutableList()
    deviceOnly.forEach { (index, source) -> merged.add(index.coerceAtMost(merged.size), source) }
    return merged
}

/** Folders and photo libraries only exist on the device that added them. */
internal fun RemoteApi.isSyncable(): Boolean = this !is Local && this !is GallerySource

internal suspend fun MTPhotoSource.withEncryptedCredentials(): MTPhotoSource =
    withTransformedCredentials { RConfig.encryptAsync(it) }

private suspend fun MTPhotoSource.withTransformedCredentials(
    transform: suspend (String) -> String,
): MTPhotoSource = when (authType) {
    MTPHOTO_AUTH_TYPE_API_KEY -> copy(
        apiKey = apiKey?.let { transform(it) },
    )

    MTPHOTO_AUTH_TYPE_PASSWORD -> copy(
        pass = pass?.let { transform(it) },
    )

    else -> this
}

internal fun S3Source.withEncryptedSecretKey(): S3Source {
    val encryptedSecretKey = RConfig.encryptBlocking(secretKey)
    return if (encryptedSecretKey == secretKey) this else copy(secretKey = encryptedSecretKey)
}
