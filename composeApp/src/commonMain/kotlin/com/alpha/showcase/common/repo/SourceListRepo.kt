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
import com.alpha.showcase.common.networkfile.storage.remote.GiteeSource
import com.alpha.showcase.common.networkfile.storage.remote.ImmichSource
import com.alpha.showcase.common.networkfile.storage.remote.MTPHOTO_AUTH_TYPE_API_KEY
import com.alpha.showcase.common.networkfile.storage.remote.MTPHOTO_AUTH_TYPE_PASSWORD
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

    suspend fun deleteSource(remoteApi: RemoteApi): Boolean {
        sourceMutationMutex.withLock {
            val oldSources = getSourcesUnlocked()

            val sources = oldSources.sources.filterNot { it.name == remoteApi.name }.toMutableList()
            val storageSources = oldSources.copy(sources = sources)
            setSourcesUnlocked(storageSources)
        }
        runCatching {
            galleryMediaStore.deleteSource(remoteApi.name)
        }.onFailure {
            it.printStackTrace()
        }
        return true
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
        var changed = false
        val normalized = storageSources.sources.map { source ->
            when (source) {
                is Smb -> {
                    val encryptedPass = RConfig.encryptAsync(source.passwd)
                    if (encryptedPass != source.passwd) changed = true
                    source.copy(passwd = encryptedPass)
                }

                is Ftp -> {
                    val encryptedPass = RConfig.encryptAsync(source.passwd)
                    if (encryptedPass != source.passwd) changed = true
                    source.copy(passwd = encryptedPass)
                }

                is Sftp -> {
                    val encryptedPass = RConfig.encryptAsync(source.passwd)
                    if (encryptedPass != source.passwd) changed = true
                    source.copy(passwd = encryptedPass)
                }

                is WebDav -> {
                    val encryptedPass = RConfig.encryptAsync(source.passwd)
                    if (encryptedPass != source.passwd) changed = true
                    source.copy(passwd = encryptedPass)
                }

                is RemoteStorageImpl -> {
                    val encryptedPass = RConfig.encryptAsync(source.passwd)
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
                    val encryptedToken = RConfig.encryptAsync(source.token)
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
                    val encryptedToken = RConfig.encryptAsync(source.token)
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
                    val encryptedApiKey = source.apiKey?.let { RConfig.encryptAsync(it) }
                    val encryptedPass = source.pass?.let { RConfig.encryptAsync(it) }
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
                    val normalized = source.withEncryptedCredentials()
                    if (normalized != source) changed = true
                    normalized
                }

                is S3Source -> {
                    val encryptedSecretKey = RConfig.encryptAsync(source.secretKey)
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
                    val encryptedApiKey = storedApiKey?.let { RConfig.encryptAsync(it) }
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
                    val encryptedApiKey = source.apiKey?.let { RConfig.encryptAsync(it) }
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
                    val encryptedApiToken = source.apiToken?.let { RConfig.encryptAsync(it) }
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
                    val encryptedToken = RConfig.encryptAsync(source.token)
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
                    val encryptedToken = RConfig.encryptAsync(source.token)
                    if (encryptedToken != source.token) changed = true
                    GooglePhotos(
                        name = source.name,
                        token = encryptedToken,
                        path = source.path
                    )
                }

                is OneDrive -> {
                    val encryptedToken = RConfig.encryptAsync(source.token)
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
                    val encryptedToken = RConfig.encryptAsync(source.token)
                    if (encryptedToken != source.token) changed = true
                    DropBox(
                        name = source.name,
                        token = encryptedToken,
                        path = source.path
                    )
                }

                else -> source
            }
        }.toMutableList()

        if (!changed) {
            return storageSources to false
        }
        return storageSources.copy(sources = normalized) to true
    }

    private companion object {
        val sourceMutationMutex = Mutex()
    }

}

internal suspend fun MTPhotoSource.withEncryptedCredentials(): MTPhotoSource = when (authType) {
    MTPHOTO_AUTH_TYPE_API_KEY -> copy(
        apiKey = apiKey?.let { RConfig.encryptAsync(it) },
    )

    MTPHOTO_AUTH_TYPE_PASSWORD -> copy(
        pass = pass?.let { RConfig.encryptAsync(it) },
    )

    else -> this
}

internal fun S3Source.withEncryptedSecretKey(): S3Source {
    val encryptedSecretKey = RConfig.encryptBlocking(secretKey)
    return if (encryptedSecretKey == secretKey) this else copy(secretKey = encryptedSecretKey)
}
