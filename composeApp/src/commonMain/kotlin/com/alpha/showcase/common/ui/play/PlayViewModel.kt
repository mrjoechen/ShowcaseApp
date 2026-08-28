package com.alpha.showcase.common.ui.play

import com.alpha.showcase.common.cache.CacheSyncResult
import com.alpha.showcase.common.mtphoto.MTPhotoFile
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.Ftp
import com.alpha.showcase.common.networkfile.storage.remote.GitHubSource
import com.alpha.showcase.common.networkfile.storage.remote.Local
import com.alpha.showcase.common.networkfile.storage.remote.RcloneRemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteStorage
import com.alpha.showcase.common.networkfile.storage.remote.RssSource
import com.alpha.showcase.common.networkfile.storage.remote.S3Source
import com.alpha.showcase.common.networkfile.storage.remote.PexelsSource
import com.alpha.showcase.common.networkfile.storage.remote.Sftp
import com.alpha.showcase.common.networkfile.storage.remote.Smb
import com.alpha.showcase.common.networkfile.storage.remote.TMDBSource
import com.alpha.showcase.common.networkfile.storage.remote.UnSplashSource
import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import com.alpha.showcase.common.networkfile.util.getStringRandom
import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.repo.CachedSourceInfo
import com.alpha.showcase.common.repo.RepoManager
import com.alpha.showcase.common.repo.S3_OBJECT_KEY
import com.alpha.showcase.common.repo.S3ObjectUrlSigner
import com.alpha.showcase.common.repo.SourceListRepo
import com.alpha.showcase.common.repo.createS3ObjectUrlSigner
import com.alpha.showcase.common.ui.ext.getSimpleMessage
import com.alpha.showcase.common.ui.settings.SortRule
import com.alpha.showcase.common.ui.vm.UiState
import com.alpha.showcase.common.utils.Log
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.fullPath
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal suspend fun convertNetworkFilesForPlayback(
    api: RemoteApi,
    files: List<NetworkFile>,
    signingDispatcher: CoroutineDispatcher = Dispatchers.Default,
    resolveS3Signer: suspend (S3Source) -> S3ObjectUrlSigner = { createS3ObjectUrlSigner(it) },
): List<Any> = when (api) {
    is S3Source -> if (files.isEmpty()) {
        emptyList()
    } else {
        when (val resolution = withContext(signingDispatcher) {
            try {
                val signer = resolveS3Signer(api)
                val items = ArrayList<Any>(files.size)
                files.forEachIndexed { index, file ->
                    if (index > 0) yield()
                    ensureActive()
                    val objectKey = file.extra?.get(S3_OBJECT_KEY) ?: file.path
                    val cacheKey = s3NetworkFileCacheKey(file)
                    val initialSignedRequest = signer.sign(objectKey)
                    items += DataWithType(
                        data = ResolvedImageModel(
                            initialSignedRequest = initialSignedRequest,
                            stableKey = file.path,
                            cacheKey = cacheKey,
                            refreshSignedRequest = { signer.sign(objectKey) },
                        ),
                        type = file.mimeType,
                    )
                }
                S3PlaybackResolution.Success(items)
            } catch (e: Exception) {
                S3PlaybackResolution.Failure(e)
            }
        }) {
            is S3PlaybackResolution.Success -> resolution.items
            is S3PlaybackResolution.Failure -> throw resolution.exception
        }
    }
    is RssSource -> files.map { it.path }
    else -> files.map { it as Any }
}

private sealed interface S3PlaybackResolution {
    data class Success(val items: List<Any>) : S3PlaybackResolution
    data class Failure(val exception: Exception) : S3PlaybackResolution
}

/**
 * Cache-DB path candidates for a converted playback item. Resolved request data
 * is deliberately ignored: signed URLs expire, while [ResolvedImageModel.stableKey]
 * retains the durable source identity used by paging anchors and cache lookup.
 */
internal fun cachePathCandidates(api: RemoteApi, item: Any): List<String> {
    return when {
        api is WebDav && item is UrlWithAuth -> {
            val base = api.url.replace(Url(api.url).fullPath, "")
            val stripped = item.url.removePrefix(base)
            listOf(stripped, stripped.removePrefix("/")).distinct()
        }
        item is String -> listOf(item)
        item is UrlWithAuth -> listOf(item.url)
        item is ResolvedImageModel -> cachePathCandidates(api, item.stableKey)
        item is DataWithType -> cachePathCandidates(api, item.data)
        item is NetworkFile -> listOf(item.path)
        else -> emptyList()
    }
}

internal suspend fun <T> playbackUiStateBoundary(
    block: suspend () -> UiState<T>,
): UiState<T> = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    UiState.Error(e.getSimpleMessage())
}

private val playbackNameComparator = Comparator<Any> { left, right ->
    val leftMTPhoto = left.mtPhotoFileOrNull()
    val rightMTPhoto = right.mtPhotoFileOrNull()
    if (leftMTPhoto != null && rightMTPhoto != null) {
        val nameComparison = leftMTPhoto.fileName.compareTo(
            rightMTPhoto.fileName,
            ignoreCase = true,
        )
        if (nameComparison != 0) nameComparison else leftMTPhoto.fileId.compareTo(rightMTPhoto.fileId)
    } else {
        playbackNameKey(left).compareTo(playbackNameKey(right))
    }
}

private val playbackDateComparator = Comparator<Any> { left, right ->
    val leftMTPhoto = left.mtPhotoFileOrNull()
    val rightMTPhoto = right.mtPhotoFileOrNull()
    if (leftMTPhoto != null && rightMTPhoto != null) {
        val dateComparison = leftMTPhoto.tokenAt.compareTo(rightMTPhoto.tokenAt)
        if (dateComparison != 0) dateComparison else leftMTPhoto.fileId.compareTo(rightMTPhoto.fileId)
    } else {
        playbackDateKey(left).compareTo(playbackDateKey(right))
    }
}

private fun Any.mtPhotoFileOrNull(): MTPhotoFile? =
    (this as? DataWithType)?.data as? MTPhotoFile

private fun playbackNameKey(item: Any): String = when (item) {
    is NetworkFile -> item.fileName
    is String -> item
    is DataWithType -> item.data.toString()
    else -> ""
}

private fun playbackDateKey(item: Any): String = when (item) {
    is NetworkFile -> item.modTime
    is String -> item
    is DataWithType -> item.data.toString()
    else -> ""
}

open class PlayViewModel {

    companion object : PlayViewModel()

    private val sourceRepo by lazy {
        RepoManager()
    }

    private val sourceListRepo by lazy {
        SourceListRepo()
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun getImageFileInfo(
        api: RemoteApi,
        recursive: Boolean = false,
        supportVideo: Boolean = false,
        sortRule: Int = -1
    ): UiState<List<Any>> = playbackUiStateBoundary {

        var imageFiles = withContext(Dispatchers.Default) {
            sourceRepo.getItems(api, recursive) {
                it.isImage() || (supportVideo && it.isVideo())
            }
        }
        if (imageFiles.isSuccess && imageFiles.getOrDefault(emptyList())
                .isNotEmpty() && sortRule != -1
        ) {

            imageFiles = withContext(Dispatchers.Default) {

                val list = imageFiles.getOrDefault(emptyList())
                val sorted = when (sortRule) {


                    SortRule.Random.value -> {
                        list.shuffled()
                    }

                    SortRule.NameAsc.value -> {
                        list.sortedWith(playbackNameComparator)
                    }

                    SortRule.NameDesc.value -> {
                        list.sortedWith(playbackNameComparator.reversed())
                    }

                    SortRule.DateAsc.value -> {
                        list.sortedWith(playbackDateComparator)
                    }

                    SortRule.DateDesc.value -> {
                        list.sortedWith(playbackDateComparator.reversed())
                    }

                    else -> {
                        list
                    }

                }

                Result.success(sorted)

            }

        }


        if (imageFiles.isSuccess) {
//      UiState.Content(imageFiles.data !!)
            when (api) {
                is Local -> {

                    val imagePathStrings = imageFiles.getOrNull()?.map {
                        (it as NetworkFile).path
                    }
                    UiState.Content(imagePathStrings ?: emptyList())
                }

                is RcloneRemoteApi -> {

                    if (api is WebDav) {
                        val list = mutableListOf<UrlWithAuth>()
                        imageFiles.getOrNull()?.forEach {networkFile ->
                            list.add(
                                    UrlWithAuth(
                                        (networkFile as NetworkFile).let {
                                            StringBuilder().append(api.url.replace(Url(api.url).fullPath, ""))
                                            .append(if (it.path.startsWith("/")) it.path else "/${it.path}")
                                            .toString()
                                    },
                                    HttpHeaders.Authorization,
                                    "Basic ${Base64.encode("${api.user}:${RConfig.decryptAsync(api.passwd)}".toByteArray())}"
                                )
                            )
                        }
                        if (list.isNotEmpty()) {
                            UiState.Content(list)
                        } else {
                            UiState.Error("No content found!")
                        }
                    } else if (api is Smb || api is Ftp || api is Sftp) {
                        if (imageFiles.isSuccess) {
                            UiState.Content(imageFiles.getOrNull() ?: emptyList())
                        } else {
                            UiState.Error(imageFiles.exceptionOrNull()?.message ?: "Error")
                        }
                    }else{
                        UiState.Error("Error !")
                    }

                }

                is GitHubSource -> {
                    // add Auth token
                    val token = RConfig.decryptAsync(api.token)
                    if (token.isBlank()) {
                        UiState.Content(imageFiles.getOrNull()!!)
                    } else {
                        val list = mutableListOf<UrlWithAuth>()
                        imageFiles.getOrNull()?.forEach {
                            list.add(
                                UrlWithAuth(
                                    it as String,
                                    HttpHeaders.Authorization,
                                    "token $token"
                                )
                            )
                        }

                        UiState.Content(list)
                    }
                }

                is S3Source -> {
                    val networkFiles = imageFiles.getOrNull().orEmpty().map { it as NetworkFile }
                    UiState.Content(convertNetworkFilesForPlayback(api, networkFiles))
                }

                is RssSource -> {
                    val networkFiles = imageFiles.getOrNull().orEmpty().map { it as NetworkFile }
                    UiState.Content(convertNetworkFilesForPlayback(api, networkFiles))
                }

                else -> {
                    UiState.Content(imageFiles.getOrNull()!!)
                }
            }

        } else {
            UiState.Error(imageFiles.exceptionOrNull()?.getSimpleMessage()?: "Error")
        }
    }

    /**
     * Paged version of getImageFileInfo for cached sources.
     * Returns a PagingPlayItems that loads data in pages from the database.
     * Falls back to full loading for non-cached sources.
     *
     * For cached sources with displayable data, returns immediately (including a
     * partial first-sync snapshot). When background sync completes, the returned
     * PagingPlayItems is refreshed in-place — no object replacement. If no media
     * is displayable yet, this call remains suspended while the UI stays Loading
     * and returns the single post-sync result.
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun getPagedImageFileInfo(
        api: RemoteApi,
        recursive: Boolean = false,
        supportVideo: Boolean = false,
        sortRule: Int = -1,
        coroutineScope: CoroutineScope,
    ): UiState<PagingPlayItems> = playbackUiStateBoundary {
        withContext(Dispatchers.Default) {
            val cacheResult = sourceRepo.ensureCacheReady(api, recursive, supportVideo)
            val cachedInfo = cacheResult.getOrNull()

            if (cachedInfo != null) {
                val session = PagedSourceSession(cachedInfo)
                val shuffleSeed = kotlin.random.Random.nextLong()
                val result = buildPagedResult(
                    api = api,
                    session = session,
                    supportVideo = supportVideo,
                    sortRule = sortRule,
                    coroutineScope = coroutineScope,
                    shuffleSeed = shuffleSeed,
                )

                if (result is UiState.Content) {
                    // We have data now; keep it in sync with later cache changes.
                    observeSyncCompletion(
                        pagingItems = result.data,
                        session = session,
                        api = api,
                        supportVideo = supportVideo,
                        sortRule = sortRule,
                        coroutineScope = coroutineScope,
                        shuffleSeed = shuffleSeed,
                    )
                    return@withContext result
                }

                // No data yet. ALWAYS await recovery on this request's own suspend
                // result instead of returning Loading plus a process-global event:
                // the sync may still be running (slow first sync), may have JUST finished between
                // buildPagedResult and here (a fast re-sync of a stale-empty cache —
                // checking a transient isCompleted flag would skip recovery and freeze
                // an Error built against the PRE-sync rows), or never needed to run
                // (genuinely fresh empty cache). The observer awaits the completion —
                // instantly when it is already terminal, including AlreadyFresh —
                // re-resolves the committed version and returns the rebuilt result, so
                // every interleaving converges on the post-sync truth.
                return@withContext awaitSlowSyncRecovery(
                    api = api,
                    cachedInfo = cachedInfo,
                    supportVideo = supportVideo,
                    sortRule = sortRule,
                    coroutineScope = coroutineScope,
                )
            }

            // ensureCacheReady FAILED for a cacheable source (sync error / empty after
            // timeout). Surface the error instead of falling through to a second full
            // network traversal. Only a successful null (= source isn't cache-backed)
            // should use the legacy full-load path below.
            if (cacheResult.isFailure) {
                return@withContext UiState.Error(
                    cacheResult.exceptionOrNull()?.getSimpleMessage() ?: "No content found!"
                )
            }

            val fullResult = getImageFileInfo(api, recursive, supportVideo, sortRule)
            return@withContext when (fullResult) {
                is UiState.Content -> {
                    if (fullResult.data.isEmpty()) {
                        UiState.Error("No content found!")
                    } else {
                        UiState.Content(PagingPlayItems.fromList(fullResult.data, coroutineScope))
                    }
                }
                is UiState.Error -> UiState.Error(fullResult.msg ?: "Error")
                UiState.Loading -> UiState.Loading
            }
        }
    }

    private fun observeSyncCompletion(
        pagingItems: PagingPlayItems,
        session: PagedSourceSession,
        api: RemoteApi,
        supportVideo: Boolean,
        sortRule: Int,
        coroutineScope: CoroutineScope,
        shuffleSeed: Long,
    ) {
        coroutineScope.launchBoundedRecoveryObserver(
            // Await THIS sync's terminal event exactly once — no shared-flow
            // racing, no cross-source contamination, and no replayed side effects.
            awaitEvent = { session.info.syncCompletion.await() },
            reconcile = { result ->
                withContext(Dispatchers.Default) {
                    when (result) {
                        // Cache was already fresh: nothing on disk changed, keep pages.
                        is CacheSyncResult.AlreadyFresh -> Unit
                        // Completed OR Failed both reconcile against the DB:
                        //  - Completed: the DB may have been rewritten (added/replaced/
                        //    removed/re-sorted), so stale loaded pages must not mix with
                        //    fresh ones.
                        //  - Failed: the cache layer has settled metadata after either
                        //    rolling back failed refresh rows or committing a first-sync
                        //    partial result. Re-read the surviving cache so the UI keeps
                        //    existing data when possible and only empties when DB truth
                        //    is actually empty.
                        is CacheSyncResult.Completed, is CacheSyncResult.Failed -> {
                            val current = session.info
                            val newVersion = sourceRepo.resolveCommittedSyncVersion(current)
                            // The insertion-order snapshot was only ever a stability
                            // measure while the first sync's rows were growing. A
                            // name/date sort must EXIT it now that the sync reached a
                            // terminal state — otherwise the user's chosen ordering
                            // would stay insertion-ordered for the whole session.
                            // Random keeps it (ordering is invisible behind the
                            // client-side shuffle, and skipping the refresh below
                            // avoids pointless churn).
                            val wantsSortedOrder = sortRule != SortRule.Random.value
                            val exitSnapshot = current.initialSnapshot && wantsSortedOrder
                            val repinned = current.copy(
                                committedSyncVersion = newVersion,
                                initialSnapshot = current.initialSnapshot && !wantsSortedOrder &&
                                    newVersion == current.committedSyncVersion,
                            )
                            val newCount = sourceRepo.countMedia(repinned, supportVideo)
                            when {
                                // Same version AND same count = exactly the rows the UI
                                // is already showing (a version's rows are immutable once
                                // its sync ended). Skip the refresh unless the session
                                // still owes the user a sorted ordering: re-sampling
                                // would only cause visual churn. This covers a failed
                                // refresh that rolled back, AND the Unsplash rate-limit
                                // case where the first sync's partial rows were committed
                                // as the same version this session already displays.
                                !exitSnapshot && newVersion == current.committedSyncVersion &&
                                    newCount == pagingItems.totalCount -> Unit
                                else -> {
                                    pagingItems.refreshPrepared(
                                        claimIf = { session.info === current },
                                    ) {
                                        prepareCandidateRefresh(
                                            candidate = repinned,
                                            api = api,
                                            supportVideo = supportVideo,
                                            sortRule = sortRule,
                                            shuffleSeed = shuffleSeed,
                                            commitCandidatePin = {
                                                session.commitCandidate(current, repinned)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            onAttemptFailure = { stage, attempt, failure ->
                Log.e(
                    "PlayViewModel",
                    recoveryFailureLogMessage(stage, attempt, failure),
                )
            },
            onExhausted = { failure ->
                Log.e(
                    "PlayViewModel",
                    recoveryFailureLogMessage(
                        RecoveryObserverStage.Exhausted,
                        null,
                        failure,
                    ),
                )
            },
        )
    }

    /**
     * For the slow-first-sync case, keep this request suspended while the caller's
     * UI remains Loading. Completion and the rebuilt state are delivered through
     * the same coroutine, so a fast recovery cannot be overwritten by a later
     * Loading assignment and separate PlayPages cannot consume each other's state.
     */
    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun awaitSlowSyncRecovery(
        api: RemoteApi,
        cachedInfo: CachedSourceInfo,
        supportVideo: Boolean,
        sortRule: Int,
        coroutineScope: CoroutineScope,
    ): UiState<PagingPlayItems> = try {
        awaitRecoveredUiState(
            awaitCompletion = { cachedInfo.syncCompletion.await() },
            rebuild = {
                // The sync that we were waiting on has now finished. Re-resolve the
                // committed version (the pending-time cachedInfo predates it), build
                // the final result from the DB and return it. No further
                // observeSyncCompletion is needed — this sync is the terminal event
                // for this source key.
                val newVersion = sourceRepo.resolveCommittedSyncVersion(cachedInfo)
                val session = PagedSourceSession(
                    cachedInfo.copy(committedSyncVersion = newVersion)
                )
                val recovered = buildPagedResult(
                    api = api,
                    session = session,
                    supportVideo = supportVideo,
                    sortRule = sortRule,
                    coroutineScope = coroutineScope,
                    shuffleSeed = kotlin.random.Random.nextLong(),
                )
                when (recovered) {
                    is UiState.Content -> recovered
                    // Sync finished with still no data — surface an empty/error state.
                    else -> UiState.Error("No content found!")
                }
            },
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        UiState.Error(e.getSimpleMessage())
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun buildPagedResult(
        api: RemoteApi,
        session: PagedSourceSession,
        supportVideo: Boolean,
        sortRule: Int,
        coroutineScope: CoroutineScope,
        shuffleSeed: Long,
    ): UiState<PagingPlayItems> {
        val cachedInfo = session.info
        val totalCount = sourceRepo.countMedia(cachedInfo, supportVideo)
        if (totalCount == 0) {
            return UiState.Error("No content found!")
        }

        val isRandom = sortRule == SortRule.Random.value
        val pageSortRule = if (isRandom) -1 else sortRule

        // ONE seed per session: a page reload must reproduce the SAME order, or an
        // evicted page coming back would silently remap its indices to different
        // media (breaking every identity-based re-anchor). Seeding by offset keeps
        // pages independent; a new session (or screen re-entry) still reshuffles.
        val firstPage = loadConvertedMediaPage(
            api = api,
            info = cachedInfo,
            supportVideo = supportVideo,
            pageSortRule = pageSortRule,
            isRandom = isRandom,
            shuffleSeed = shuffleSeed,
            offset = 0,
            limit = PagingPlayItems.DEFAULT_PAGE_SIZE,
        )

        if (firstPage.isEmpty()) {
            return UiState.Error("No content found!")
        }

        val pagingItems = PagingPlayItems(
            totalCount = totalCount,
            initialPage = firstPage,
            coroutineScope = coroutineScope,
            loadPage = { offset, limit ->
                loadPinnedPageOrStageRecovery(
                    session = session,
                    loadPinned = { pinned ->
                        loadConvertedMediaPage(
                            api = api,
                            info = pinned,
                            supportVideo = supportVideo,
                            pageSortRule = pageSortRule,
                            isRandom = isRandom,
                            shuffleSeed = shuffleSeed,
                            offset = offset,
                            limit = limit,
                        )
                    },
                    resolveLiveVersion = { pinned ->
                        sourceRepo.resolveCommittedSyncVersion(pinned)
                    },
                )
            },
            locateIndex = { item ->
                // Stable-key lookup in the backing store: resolves the item's NEW
                // index after a refresh even when its page isn't loaded. Random
                // sessions shuffle only WITHIN a page, so the ordinal still lands
                // in the right page; the in-memory search then pins it exactly.
                val info = session.info
                var located: Int? = null
                for (candidate in cachePathCandidates(api, item)) {
                    located = sourceRepo.locateMediaIndex(info, supportVideo, pageSortRule, candidate)
                    if (located != null) break
                }
                located
            },
        )
        session.onPinLost = { recovery ->
            pagingItems.refreshPrepared(
                claimIf = { session.isCurrent(recovery) },
                onAbandoned = { session.abandonRecovery(recovery) },
            ) {
                prepareCandidateRefresh(
                    candidate = recovery.candidate,
                    api = api,
                    supportVideo = supportVideo,
                    sortRule = sortRule,
                    shuffleSeed = shuffleSeed,
                    commitCandidatePin = { session.commitRecovery(recovery) },
                )
            }
        }
        return UiState.Content(pagingItems)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun prepareCandidateRefresh(
        candidate: CachedSourceInfo,
        api: RemoteApi,
        supportVideo: Boolean,
        sortRule: Int,
        shuffleSeed: Long,
        commitCandidatePin: () -> Boolean,
    ): PreparedPagingRefresh? {
        val totalCount = sourceRepo.countMedia(candidate, supportVideo)
        if (totalCount <= 0) {
            return PreparedPagingRefresh(
                totalCount = totalCount,
                firstPage = emptyList(),
                commitCandidatePin = commitCandidatePin,
            )
        }

        val isRandom = sortRule == SortRule.Random.value
        val firstPage = loadConvertedMediaPage(
            api = api,
            info = candidate,
            supportVideo = supportVideo,
            pageSortRule = if (isRandom) -1 else sortRule,
            isRandom = isRandom,
            shuffleSeed = shuffleSeed,
            offset = 0,
            limit = PagingPlayItems.DEFAULT_PAGE_SIZE,
        )
        if (firstPage.isEmpty()) return null

        return PreparedPagingRefresh(
            totalCount = totalCount,
            firstPage = firstPage,
            commitCandidatePin = commitCandidatePin,
        )
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun loadConvertedMediaPage(
        api: RemoteApi,
        info: CachedSourceInfo,
        supportVideo: Boolean,
        pageSortRule: Int,
        isRandom: Boolean,
        shuffleSeed: Long,
        offset: Int,
        limit: Int,
    ): List<Any> {
        val rawPage = sourceRepo.loadMediaPage(
            info,
            supportVideo,
            pageSortRule,
            offset = offset,
            limit = limit,
        )
        val converted = convertNetworkFiles(api, rawPage)
        return if (isRandom) {
            converted.shuffled(kotlin.random.Random(shuffleSeed + offset))
        } else {
            converted
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun convertNetworkFiles(api: RemoteApi, files: List<NetworkFile>): List<Any> {
        return when (api) {
            is Local -> files.map { it.path }
            is WebDav -> files.map { networkFile ->
                UrlWithAuth(
                    url = StringBuilder()
                        .append(api.url.replace(Url(api.url).fullPath, ""))
                        .append(if (networkFile.path.startsWith("/")) networkFile.path else "/${networkFile.path}")
                        .toString(),
                    key = HttpHeaders.Authorization,
                    value = "Basic ${Base64.encode("${api.user}:${RConfig.decryptAsync(api.passwd)}".toByteArray())}"
                )
            }
            is GitHubSource -> {
                val token = RConfig.decryptAsync(api.token)
                if (token.isBlank()) {
                    files.map { it as Any }
                } else {
                    files.map { UrlWithAuth(it.path, HttpHeaders.Authorization, "token $token") }
                }
            }
            is UnSplashSource -> files.map { file ->
                DataWithType(file.path, file.mimeType.removePrefix("image/").ifBlank { "jpg" })
            }
            is PexelsSource -> files.map { it.path }
            is TMDBSource -> files.map { it.path }
            is S3Source -> convertNetworkFilesForPlayback(api, files)
            is RssSource -> convertNetworkFilesForPlayback(api, files)
            else -> files.map { it as Any }
        }
    }

    suspend fun getFileInfo(remoteStorage: RemoteStorage): UiState<Any> {
        val fileInfo = sourceRepo.getItem(remoteStorage)
        return if (fileInfo.isSuccess) {
            UiState.Content(fileInfo.getOrNull()!!)
        } else {
            UiState.Error(fileInfo.toString())
        }
    }

}
