package com.alpha.showcase.common.repo

import com.alpha.showcase.common.networkfile.RService
import com.alpha.showcase.common.networkfile.storage.StorageSources
import com.alpha.showcase.common.networkfile.storage.remote.RcloneRemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.networkfile.util.StorageSourceSerializer
import com.alpha.showcase.common.storage.objectStoreOf
import com.alpha.showcase.common.versionCode
import com.alpha.showcase.common.versionName
import kotlinx.datetime.Clock
import randomUUID


class SourceListRepo {
    private val store = objectStoreOf<String>("sources")

    private val rclone by lazy {
        RService.rcx
    }

    private val defaultValue by lazy {
        StorageSources(
            versionCode.toInt(),
            versionName,
            randomUUID(),
            "default",
            Clock.System.now().toEpochMilliseconds(),
            mutableListOf()
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

    suspend fun addSource(source: RemoteApi<Any>) {
        val sources = getSources()
        sources.sources.add(source)
        setSources(sources)
    }


    fun setUpSourcesAndConfig(remoteApi: RemoteApi<Any>) {
        TODO("Not yet implemented")
    }

    suspend fun saveSource(remoteApi: RemoteApi<Any>): Boolean {
        val storageSources = getSources()
        storageSources.sources.add(remoteApi)
        setSources(storageSources)
        return true
    }

    suspend fun deleteSource(remoteApi: RemoteApi<Any>): Boolean {
        val oldSources = getSources()

        val sources = mutableListOf<RemoteApi<Any>>()
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

    fun getSourceFileDirItems(remoteApi: RcloneRemoteApi, path: String): Result<Any> {
        TODO("Not yet implemented")
    }

    fun <T> linkConnection(
        oAuthRcloneApi: T, onRetrieveOauthUrl: (String?) -> Unit
    ): T? {
        TODO("Not yet implemented")
    }

    fun checkConnection(remoteApi: RemoteApi<Any>): Result<Any> {
        TODO("Not yet implemented")
    }

}
