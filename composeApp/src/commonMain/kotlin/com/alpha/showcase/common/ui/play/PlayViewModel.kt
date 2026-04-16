package com.alpha.showcase.common.ui.play

import DEBUG
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.Ftp
import com.alpha.showcase.common.networkfile.storage.remote.GitHubSource
import com.alpha.showcase.common.networkfile.storage.remote.Local
import com.alpha.showcase.common.networkfile.storage.remote.RcloneRemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteStorage
import com.alpha.showcase.common.networkfile.storage.remote.Sftp
import com.alpha.showcase.common.networkfile.storage.remote.Smb
import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import com.alpha.showcase.common.networkfile.util.getStringRandom
import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.repo.CachedSourceInfo
import com.alpha.showcase.common.repo.RepoManager
import com.alpha.showcase.common.repo.SourceListRepo
import com.alpha.showcase.common.ui.ext.getSimpleMessage
import com.alpha.showcase.common.ui.settings.SettingsViewModel.Companion.viewModelScope
import com.alpha.showcase.common.ui.settings.SortRule
import com.alpha.showcase.common.ui.vm.UiState
import com.alpha.showcase.common.utils.Log
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.fullPath
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


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
    ): UiState<List<Any>> {

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
                        list.sortedBy {
                            when (it) {
                                is NetworkFile -> {
                                    it.fileName
                                }

                                is String -> {
                                    it
                                }

                                is DataWithType -> {
                                    it.data.toString()
                                }

                                else -> ""
                            }
                        }
                    }

                    SortRule.NameDesc.value -> {
                        list.sortedByDescending {
                            when (it) {
                                is NetworkFile -> {
                                    it.fileName
                                }

                                is String -> {
                                    it
                                }

                                is DataWithType -> {
                                    it.data.toString()
                                }

                                else -> ""
                            }

                        }
                    }

                    SortRule.DateAsc.value -> {
                        list.sortedBy {
                            when (it) {
                                is NetworkFile -> {
                                    it.modTime
                                }

                                is String -> {
                                    it
                                }

                                is DataWithType -> {
                                    it.data.toString()
                                }

                                else -> ""
                            }

                        }
                    }

                    SortRule.DateDesc.value -> {
                        list.sortedByDescending {
                            when (it) {
                                is NetworkFile -> {
                                    it.modTime
                                }

                                is String -> {
                                    it
                                }

                                is DataWithType -> {
                                    it.data.toString()
                                }

                                else -> ""
                            }

                        }
                    }

                    else -> {
                        list
                    }

                }

                Result.success(sorted)

            }

        }


        return if (imageFiles.isSuccess) {
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
                                    "Basic ${Base64.encode("${api.user}:${RConfig.decrypt(api.passwd)}".toByteArray())}"
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
                    val token = RConfig.decrypt(api.token)
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

                else -> {
                    UiState.Content(imageFiles.getOrNull()!!)
                }
            }

        } else {
            UiState.Error(imageFiles.exceptionOrNull()?.getSimpleMessage()?: "Error")
        }
    }

    /**
     * Paged version of getImageFileInfo for cached sources (WebDav, SMB).
     * Returns a PagingPlayItems that loads data in pages from the database.
     * Falls back to full loading for non-cached sources.
     */
    @OptIn(ExperimentalEncodingApi::class)
    suspend fun getPagedImageFileInfo(
        api: RemoteApi,
        recursive: Boolean = false,
        supportVideo: Boolean = false,
        sortRule: Int = -1,
        coroutineScope: CoroutineScope,
    ): UiState<PagingPlayItems> = withContext(Dispatchers.Default) {

        // Try paged loading for cached sources
        val cacheResult = sourceRepo.ensureCacheReady(api, recursive)
        val cachedInfo = cacheResult.getOrNull()

        if (cachedInfo != null) {
            // Cached source - use paged loading
            return@withContext buildPagedResult(api, cachedInfo, supportVideo, sortRule, coroutineScope)
        }

        // Non-cached source or cache not supported - fall back to full list loading
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

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun buildPagedResult(
        api: RemoteApi,
        cachedInfo: CachedSourceInfo,
        supportVideo: Boolean,
        sortRule: Int,
        coroutineScope: CoroutineScope,
    ): UiState<PagingPlayItems> {
        val totalCount = sourceRepo.countMedia(cachedInfo, supportVideo)
        if (totalCount == 0) {
            return UiState.Error("No content found!")
        }

        val effectiveSortRule = sortRule
        val isRandom = sortRule == SortRule.Random.value

        // Load first page
        val firstPageRaw = sourceRepo.loadMediaPage(
            cachedInfo, supportVideo, if (isRandom) -1 else effectiveSortRule,
            offset = 0, limit = PagingPlayItems.DEFAULT_PAGE_SIZE
        )
        val firstPage = convertNetworkFiles(api, firstPageRaw)
        if (isRandom) {
            firstPage.shuffled()
        }

        if (firstPage.isEmpty()) {
            return UiState.Error("No content found!")
        }

        val pagingItems = PagingPlayItems(
            totalCount = totalCount,
            initialPage = if (isRandom) firstPage.shuffled() else firstPage,
            coroutineScope = coroutineScope,
            loadPage = { offset, limit ->
                val pageRaw = sourceRepo.loadMediaPage(
                    cachedInfo, supportVideo, if (isRandom) -1 else effectiveSortRule,
                    offset = offset, limit = limit
                )
                val converted = convertNetworkFiles(api, pageRaw)
                if (isRandom) converted.shuffled() else converted
            }
        )
        return UiState.Content(pagingItems)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun convertNetworkFiles(api: RemoteApi, files: List<NetworkFile>): List<Any> {
        return when (api) {
            is Local -> files.map { it.path }
            is WebDav -> files.map { networkFile ->
                UrlWithAuth(
                    url = StringBuilder()
                        .append(api.url.replace(Url(api.url).fullPath, ""))
                        .append(if (networkFile.path.startsWith("/")) networkFile.path else "/${networkFile.path}")
                        .toString(),
                    key = HttpHeaders.Authorization,
                    value = "Basic ${Base64.encode("${api.user}:${RConfig.decrypt(api.passwd)}".toByteArray())}"
                )
            }
            is GitHubSource -> {
                val token = RConfig.decrypt(api.token)
                if (token.isBlank()) {
                    files.map { it as Any }
                } else {
                    files.map { UrlWithAuth(it.path, HttpHeaders.Authorization, "token $token") }
                }
            }
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

    fun onClear() {

    }
}
