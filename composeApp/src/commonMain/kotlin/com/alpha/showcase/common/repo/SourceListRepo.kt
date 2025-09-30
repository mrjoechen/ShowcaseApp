@file:OptIn(ExperimentalTime::class)
package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.storage.StorageSources
import com.alpha.showcase.common.networkfile.storage.remote.RcloneRemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import com.alpha.showcase.common.networkfile.util.StorageSourceSerializer
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
        return store.get()?.let {
            StorageSourceSerializer.sourceJson.decodeFromString(StorageSources.serializer(), it)
        } ?: defaultValue
    }


    suspend fun setSources(sources: StorageSources) {
        store.set(
            StorageSourceSerializer.sourceJson.encodeToString(
                StorageSources.serializer(),
                sources
            )
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
                val result = repoManager.checkConnection(remoteApi)
                if (result.isSuccess) {
                    Result.success(result.getOrNull())
                } else {
                    Result.failure("Error")
                }
            }
        } catch (e: TimeoutCancellationException) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

}
