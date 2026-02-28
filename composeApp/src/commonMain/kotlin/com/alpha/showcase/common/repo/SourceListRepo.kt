@file:OptIn(ExperimentalTime::class)
package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.storage.StorageSources
import com.alpha.showcase.common.networkfile.storage.drive.DropBox
import com.alpha.showcase.common.networkfile.storage.drive.GoogleDrive
import com.alpha.showcase.common.networkfile.storage.drive.GooglePhotos
import com.alpha.showcase.common.networkfile.storage.drive.OneDrive
import com.alpha.showcase.common.networkfile.storage.remote.Ftp
import com.alpha.showcase.common.networkfile.storage.remote.GitHubSource
import com.alpha.showcase.common.networkfile.storage.remote.GiteeSource
import com.alpha.showcase.common.networkfile.storage.remote.ImmichSource
import com.alpha.showcase.common.networkfile.storage.remote.RcloneRemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteStorageImpl
import com.alpha.showcase.common.networkfile.storage.remote.Sftp
import com.alpha.showcase.common.networkfile.storage.remote.Smb
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import com.alpha.showcase.common.networkfile.util.StorageSourceSerializer
import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.storage.objectStoreOf
import com.alpha.showcase.common.versionCode
import com.alpha.showcase.common.versionName
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.Clock
import randomUUID
import kotlin.time.ExperimentalTime

class SourceListRepo {
    private val store = objectStoreOf<String>("sources")

    private val repoManager by lazy {
        RepoManager()
    }


    private val defaultValue by lazy {
        StorageSources(
            versionCode.toInt(),
            versionName,
            randomUUID(),
            "default",
            Clock.System.now().toEpochMilliseconds(),
            mutableListOf(UnSplashSource("Sample", UnSplashSourceType.UsersPhotos.type, "chenqiao"))
        )
    }

    suspend fun getSources(): StorageSources {
        val encrypted = runCatching { store.get() }.getOrNull()
        encrypted?.let {
            runCatching {
                val rawJson = RConfig.decrypt(it)
                StorageSourceSerializer.sourceJson.decodeFromString(StorageSources.serializer(), rawJson)
            }.getOrNull()
        }?.let {
            val (normalized, changed) = normalizeSensitiveFields(it)
            if (changed) {
                setSources(normalized)
            }
            return normalized
        }
        return defaultValue
    }


    suspend fun setSources(sources: StorageSources) {
        val rawJson = StorageSourceSerializer.sourceJson.encodeToString(
            StorageSources.serializer(),
            sources
        )
        store.set(
            RConfig.encrypt(rawJson)
        )
    }

    suspend fun addSource(source: RemoteApi) {
        val sources = getSources()
        sources.sources.add(source)
        setSources(sources)
    }

    suspend fun saveSource(remoteApi: RemoteApi): Boolean {
        val storageSources = getSources()
        storageSources.sources.add(remoteApi)
        setSources(storageSources)
        return true
    }

    suspend fun deleteSource(remoteApi: RemoteApi): Boolean {
        val oldSources = getSources()

        val sources = mutableListOf<RemoteApi>()
        sources.addAll(oldSources.sources)
        val remoteStorages = oldSources.sources.filter { ele ->
            ele.name == remoteApi.name
        }
        remoteStorages.forEach { ele ->
            sources.remove(ele)
        }
        val storageSources = StorageSources(
            oldSources.version,
            oldSources.versionName,
            oldSources.id,
            oldSources.sourceName,
            oldSources.timeStamp,
            sources
        )
        setSources(storageSources)
        return true
    }

    suspend fun getSourceFileDirItems(
        remoteApi: RcloneRemoteApi,
        path: String,
    ) = repoManager.getFileDirItems(remoteApi, path)

    suspend fun checkConnection(remoteApi: RemoteApi, timeout: Long = 10000): Result<Any> {
        return try {
            withTimeout(timeout) {
                repoManager.checkConnection(remoteApi)
            }
        } catch (e: TimeoutCancellationException) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun normalizeSensitiveFields(storageSources: StorageSources): Pair<StorageSources, Boolean> {
        var changed = false
        val normalized = storageSources.sources.map { source ->
            when (source) {
                is Smb -> {
                    val encryptedPass = RConfig.encrypt(source.passwd)
                    if (encryptedPass != source.passwd) changed = true
                    source.copy(passwd = encryptedPass)
                }

                is Ftp -> {
                    val encryptedPass = RConfig.encrypt(source.passwd)
                    if (encryptedPass != source.passwd) changed = true
                    source.copy(passwd = encryptedPass)
                }

                is Sftp -> {
                    val encryptedPass = RConfig.encrypt(source.passwd)
                    if (encryptedPass != source.passwd) changed = true
                    source.copy(passwd = encryptedPass)
                }

                is WebDav -> {
                    val encryptedPass = RConfig.encrypt(source.passwd)
                    if (encryptedPass != source.passwd) changed = true
                    source.copy(passwd = encryptedPass)
                }

                is RemoteStorageImpl -> {
                    val encryptedPass = RConfig.encrypt(source.passwd)
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
                    val encryptedToken = RConfig.encrypt(source.token)
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
                    val encryptedToken = RConfig.encrypt(source.token)
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
                    val encryptedApiKey = source.apiKey?.let { RConfig.encrypt(it) }
                    val encryptedPass = source.pass?.let { RConfig.encrypt(it) }
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

                is GoogleDrive -> {
                    val encryptedToken = RConfig.encrypt(source.token)
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
                    val encryptedToken = RConfig.encrypt(source.token)
                    if (encryptedToken != source.token) changed = true
                    GooglePhotos(
                        name = source.name,
                        token = encryptedToken,
                        path = source.path
                    )
                }

                is OneDrive -> {
                    val encryptedToken = RConfig.encrypt(source.token)
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
                    val encryptedToken = RConfig.encrypt(source.token)
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

}
