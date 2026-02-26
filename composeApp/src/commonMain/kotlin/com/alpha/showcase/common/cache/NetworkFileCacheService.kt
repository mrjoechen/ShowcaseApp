@file:OptIn(ExperimentalTime::class)

package com.alpha.showcase.common.cache

import com.alpha.showcase.common.cache.entity.CacheMetadata
import com.alpha.showcase.common.cache.entity.CachedItem
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.RemoteStorage
import com.alpha.showcase.common.networkfile.storage.remote.Smb
import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import com.alpha.showcase.common.networkfile.util.StorageSourceSerializer
import com.alpha.showcase.common.repo.BatchSourceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private const val DEFAULT_SYNC_BATCH_SIZE = 200
private const val REFRESH_RETRY_DELAY_MS = 5 * 60 * 1000L

class NetworkFileCacheService(
    private val database: SourceCacheDatabase = SourceCacheDatabaseProvider.database
) {

    private val itemDao = database.cachedItemDao()
    private val metadataDao = database.cacheMetadataDao()

    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshingKeys = mutableSetOf<String>()
    private val refreshLock = Mutex()

    private val metadataJson = Json {
        ignoreUnknownKeys = true
    }

    suspend fun <T : RemoteStorage> getOrLoad(
        remoteApi: T,
        recursive: Boolean,
        filter: ((NetworkFile) -> Boolean)?,
        repository: BatchSourceRepository<T, NetworkFile>,
        forceRefresh: Boolean = false,
    ): Result<List<NetworkFile>> {
        val serializedSource = StorageSourceSerializer.sourceJson.encodeToString(
            RemoteStorage.serializer(),
            remoteApi
        )
        val sourceType = remoteApi.schema.lowercase()
        val sourceKey = buildSourceKey(serializedSource, recursive)
        val configHash = buildConfigHash(serializedSource)
        val policy = resolvePolicy(remoteApi, recursive)

        val metadata = metadataDao.getBySource(sourceType, sourceKey)
        val cachedFiles = loadCachedFiles(remoteApi, sourceType, sourceKey)
        val now = currentTimeMillis()

        if (!forceRefresh) {
            val cacheFresh = isCacheFresh(metadata, configHash, now)
            if (cacheFresh) {
                itemDao.updateLastAccessed(sourceType, sourceKey, now)
                return Result.success(applyFilter(cachedFiles, filter))
            }

            val knownEmptyCache = metadata != null && metadata.isValid() && metadata.totalItems == 0
            if (cachedFiles.isNotEmpty() || knownEmptyCache) {
                itemDao.updateLastAccessed(sourceType, sourceKey, now)
                launchBackgroundRefresh(
                    remoteApi = remoteApi,
                    recursive = recursive,
                    sourceType = sourceType,
                    sourceKey = sourceKey,
                    configHash = configHash,
                    policy = policy,
                    repository = repository,
                )
                return Result.success(applyFilter(cachedFiles, filter))
            }
        }

        val syncResult = refreshNow(
            remoteApi = remoteApi,
            recursive = recursive,
            sourceType = sourceType,
            sourceKey = sourceKey,
            configHash = configHash,
            policy = policy,
            repository = repository,
            collectResult = true,
            filter = filter
        )

        if (syncResult.isSuccess) {
            return syncResult
        }

        if (cachedFiles.isNotEmpty()) {
            return Result.success(applyFilter(cachedFiles, filter))
        }

        val knownEmptyCache = metadata != null && metadata.isValid() && metadata.totalItems == 0
        if (knownEmptyCache) {
            return Result.success(emptyList())
        }

        return Result.failure(syncResult.exceptionOrNull() ?: Exception("Cache sync failed"))
    }

    private suspend fun <T : RemoteStorage> refreshNow(
        remoteApi: T,
        recursive: Boolean,
        sourceType: String,
        sourceKey: String,
        configHash: String,
        policy: CachePolicy,
        repository: BatchSourceRepository<T, NetworkFile>,
        collectResult: Boolean,
        filter: ((NetworkFile) -> Boolean)?,
    ): Result<List<NetworkFile>> {
        val acquired = acquireRefresh(sourceKey)
        if (!acquired) {
            val fallback = loadCachedFiles(remoteApi, sourceType, sourceKey)
            return Result.success(applyFilter(fallback, filter))
        }

        return try {
            syncCache(
                remoteApi = remoteApi,
                recursive = recursive,
                sourceType = sourceType,
                sourceKey = sourceKey,
                configHash = configHash,
                policy = policy,
                repository = repository,
                collectResult = collectResult,
                filter = filter,
            )
        } finally {
            releaseRefresh(sourceKey)
        }
    }

    private fun <T : RemoteStorage> launchBackgroundRefresh(
        remoteApi: T,
        recursive: Boolean,
        sourceType: String,
        sourceKey: String,
        configHash: String,
        policy: CachePolicy,
        repository: BatchSourceRepository<T, NetworkFile>,
    ) {
        refreshScope.launch {
            val acquired = acquireRefresh(sourceKey)
            if (!acquired) {
                return@launch
            }

            try {
                syncCache(
                    remoteApi = remoteApi,
                    recursive = recursive,
                    sourceType = sourceType,
                    sourceKey = sourceKey,
                    configHash = configHash,
                    policy = policy,
                    repository = repository,
                    collectResult = false,
                    filter = null,
                )
            } finally {
                releaseRefresh(sourceKey)
            }
        }
    }

    private suspend fun <T : RemoteStorage> syncCache(
        remoteApi: T,
        recursive: Boolean,
        sourceType: String,
        sourceKey: String,
        configHash: String,
        policy: CachePolicy,
        repository: BatchSourceRepository<T, NetworkFile>,
        collectResult: Boolean,
        filter: ((NetworkFile) -> Boolean)?,
    ): Result<List<NetworkFile>> {
        val existingMetadata = metadataDao.getBySource(sourceType, sourceKey)
        val syncVersion = currentTimeMillis()
        val startedAt = syncVersion

        metadataDao.insertOrReplace(
            buildMetadata(
                existing = existingMetadata,
                sourceType = sourceType,
                sourceKey = sourceKey,
                lastUpdated = startedAt,
                nextUpdateTime = startedAt,
                totalItems = existingMetadata?.totalItems ?: 0,
                policy = policy,
                recursive = recursive,
                status = CacheMetadata.STATUS_UPDATING,
                configHash = configHash,
                version = existingMetadata?.version,
            )
        )

        val collected = mutableListOf<NetworkFile>()
        var totalItems = 0

        return try {
            val streamResult = repository.streamItems(
                remoteApi = remoteApi,
                recursive = recursive,
                filter = null,
                batchSize = DEFAULT_SYNC_BATCH_SIZE,
            ) { batch ->
                if (batch.isEmpty()) return@streamItems

                totalItems += batch.size
                val now = currentTimeMillis()
                val cacheBatch = batch.map {
                    it.toCachedItem(
                        sourceType = sourceType,
                        sourceKey = sourceKey,
                        isRecursive = recursive,
                        syncVersion = syncVersion,
                        now = now,
                        metadataJson = metadataJson
                    )
                }
                itemDao.insertOrReplace(cacheBatch)

                if (collectResult) {
                    if (filter == null) {
                        collected.addAll(batch)
                    } else {
                        collected.addAll(batch.filter(filter))
                    }
                }
            }

            if (streamResult.isFailure) {
                throw streamResult.exceptionOrNull() ?: Exception("Stream items failed")
            }

            itemDao.deleteBySourceAndOldSyncVersion(sourceType, sourceKey, syncVersion)

            val finishedAt = currentTimeMillis()
            metadataDao.insertOrReplace(
                buildMetadata(
                    existing = metadataDao.getBySource(sourceType, sourceKey),
                    sourceType = sourceType,
                    sourceKey = sourceKey,
                    lastUpdated = finishedAt,
                    nextUpdateTime = finishedAt + policy.ttlMillis,
                    totalItems = totalItems,
                    policy = policy,
                    recursive = recursive,
                    status = CacheMetadata.STATUS_VALID,
                    configHash = configHash,
                    version = existingMetadata?.version,
                )
            )
            itemDao.updateLastAccessed(sourceType, sourceKey, finishedAt)

            Result.success(if (collectResult) collected else emptyList())
        } catch (e: Exception) {
            e.printStackTrace()
            itemDao.deleteBySourceAndSyncVersion(sourceType, sourceKey, syncVersion)

            val failedAt = currentTimeMillis()
            val failedMetadata = metadataDao.getBySource(sourceType, sourceKey)
            metadataDao.insertOrReplace(
                buildMetadata(
                    existing = failedMetadata,
                    sourceType = sourceType,
                    sourceKey = sourceKey,
                    lastUpdated = failedAt,
                    nextUpdateTime = failedAt + REFRESH_RETRY_DELAY_MS,
                    totalItems = failedMetadata?.totalItems ?: 0,
                    policy = policy,
                    recursive = recursive,
                    status = if ((failedMetadata?.totalItems ?: 0) > 0) {
                        CacheMetadata.STATUS_VALID
                    } else {
                        CacheMetadata.STATUS_INVALID
                    },
                    configHash = configHash,
                    version = failedMetadata?.version,
                )
            )

            Result.failure(e)
        }
    }

    private fun isCacheFresh(metadata: CacheMetadata?, configHash: String, now: Long): Boolean {
        if (metadata == null) return false
        if (!metadata.isValid()) return false
        if (metadata.isSourceConfigChanged(configHash)) return false
        return metadata.nextUpdateTime > now
    }

    private suspend fun loadCachedFiles(
        remoteApi: RemoteStorage,
        sourceType: String,
        sourceKey: String,
    ): List<NetworkFile> {
        return itemDao.getBySource(sourceType, sourceKey).map {
            it.toNetworkFile(remoteApi, metadataJson)
        }
    }

    private fun applyFilter(
        files: List<NetworkFile>,
        filter: ((NetworkFile) -> Boolean)?
    ): List<NetworkFile> {
        return if (filter == null) files else files.filter(filter)
    }

    private fun resolvePolicy(remoteApi: RemoteStorage, recursive: Boolean): CachePolicy {
        val ttl = when (remoteApi) {
            is Smb -> if (recursive) 60 * 60 * 1000L else 20 * 60 * 1000L
            is WebDav -> if (recursive) 45 * 60 * 1000L else 15 * 60 * 1000L
            else -> if (recursive) 30 * 60 * 1000L else 10 * 60 * 1000L
        }
        return CachePolicy(CacheMetadata.STRATEGY_STALE_WHILE_REVALIDATE, ttl)
    }

    private fun buildSourceKey(serializedSource: String, recursive: Boolean): String {
        return "$serializedSource|recursive=$recursive".encodeUtf8().sha256().hex()
    }

    private fun buildConfigHash(serializedSource: String): String {
        return serializedSource.encodeUtf8().sha256().hex()
    }

    private suspend fun acquireRefresh(sourceKey: String): Boolean {
        return refreshLock.withLock {
            if (refreshingKeys.contains(sourceKey)) {
                false
            } else {
                refreshingKeys.add(sourceKey)
                true
            }
        }
    }

    private suspend fun releaseRefresh(sourceKey: String) {
        refreshLock.withLock {
            refreshingKeys.remove(sourceKey)
        }
    }

    private fun buildMetadata(
        existing: CacheMetadata?,
        sourceType: String,
        sourceKey: String,
        lastUpdated: Long,
        nextUpdateTime: Long,
        totalItems: Int,
        policy: CachePolicy,
        recursive: Boolean,
        status: String,
        configHash: String,
        version: String?,
    ): CacheMetadata {
        return CacheMetadata(
            id = existing?.id ?: 0,
            sourceType = sourceType,
            sourceKey = sourceKey,
            lastUpdated = lastUpdated,
            nextUpdateTime = nextUpdateTime,
            totalItems = totalItems,
            updateStrategy = policy.strategy,
            isRecursive = recursive,
            version = version,
            status = status,
            sourceConfigHash = configHash,
        )
    }

    @Serializable
    private data class CachedFileMetadata(
        val modTime: String,
        val isBucket: Boolean,
    )

    private data class CachePolicy(
        val strategy: String,
        val ttlMillis: Long,
    )

    private fun NetworkFile.toCachedItem(
        sourceType: String,
        sourceKey: String,
        isRecursive: Boolean,
        syncVersion: Long,
        now: Long,
        metadataJson: Json,
    ): CachedItem {
        return CachedItem(
            sourceType = sourceType,
            sourceKey = sourceKey,
            parentPath = extractParentPath(path),
            name = fileName,
            path = path,
            isDirectory = isDirectory,
            size = size,
            mimeType = mimeType,
            modifiedTime = modTime.toLongOrNull() ?: 0L,
            createdAt = now,
            lastAccessed = now,
            isRecursive = isRecursive,
            metadata = metadataJson.encodeToString(CachedFileMetadata(modTime, isBucket)),
            syncVersion = syncVersion,
        )
    }

    private fun CachedItem.toNetworkFile(remoteApi: RemoteStorage, metadataJson: Json): NetworkFile {
        val extra = metadata?.let {
            runCatching { metadataJson.decodeFromString<CachedFileMetadata>(it) }.getOrNull()
        }

        val modTimeValue = extra?.modTime ?: if (modifiedTime > 0) modifiedTime.toString() else ""
        return NetworkFile(
            remote = remoteApi,
            path = path,
            fileName = name,
            isDirectory = isDirectory,
            size = size,
            mimeType = mimeType,
            modTime = modTimeValue,
            isBucket = extra?.isBucket ?: false,
        )
    }

    private fun extractParentPath(path: String): String {
        val normalized = if (path.length > 1) path.trimEnd('/') else path
        val lastSlash = normalized.lastIndexOf('/')
        return when {
            lastSlash <= 0 -> "/"
            else -> normalized.substring(0, lastSlash)
        }
    }

    private fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()
}
