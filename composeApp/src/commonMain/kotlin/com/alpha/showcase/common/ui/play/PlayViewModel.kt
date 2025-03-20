package com.alpha.showcase.common.ui.play

import com.alpha.showcase.common.DEBUG
import com.alpha.showcase.common.networkfile.DEFAULT_SERVE_PORT
import com.alpha.showcase.common.networkfile.Data.Companion.dataOf
import com.alpha.showcase.common.networkfile.R_SERVICE_ACCESS_BASE_URL
import com.alpha.showcase.common.networkfile.R_SERVICE_WORKER_ARG_ALLOW_REMOTE_ACCESS
import com.alpha.showcase.common.networkfile.R_SERVICE_WORKER_ARG_BASE_URL
import com.alpha.showcase.common.networkfile.R_SERVICE_WORKER_ARG_PASSWD
import com.alpha.showcase.common.networkfile.R_SERVICE_WORKER_ARG_PORT
import com.alpha.showcase.common.networkfile.R_SERVICE_WORKER_ARG_REMOTE
import com.alpha.showcase.common.networkfile.R_SERVICE_WORKER_ARG_SERVE_PATH
import com.alpha.showcase.common.networkfile.R_SERVICE_WORKER_ARG_USER
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.GitHubSource
import com.alpha.showcase.common.networkfile.storage.remote.Local
import com.alpha.showcase.common.networkfile.storage.remote.RcloneRemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteStorage
import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import com.alpha.showcase.common.networkfile.util.getStringRandom
import com.alpha.showcase.common.repo.RepoManager
import com.alpha.showcase.common.repo.SourceListRepo
import com.alpha.showcase.common.repo.USE_NATIVE_WEBDAV_CLIENT
import com.alpha.showcase.common.ui.settings.SettingsViewModel.Companion.viewModelScope
import com.alpha.showcase.common.ui.settings.SortRule
import com.alpha.showcase.common.ui.vm.UiState
import com.alpha.showcase.common.utils.Log
import com.alpha.showcase.common.utils.getExtension
import io.ktor.http.Url
import io.ktor.http.fullPath
import rService
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi


val SUPPORT_MIME_FILTER_IMAGE = listOf("image/jpeg", "image/webp", "image/png", "image/bmp")
val SUPPORT_MIME_FILTER_VIDEO =
    listOf("video/mp4", "video/x-matroska", "video/webm", "video/quicktime")
val IMAGE_EXT_SUPPORT =
    listOf("jpg", "png", "jpeg", "bmp", "webp", "heic", "JPG", "PNG", "JPEG", "BMP", "WEBP", "HEIC")
val VIDEO_EXT_SUPPORT = listOf("mp4", "mkv", "webm", "mov")

open class PlayViewModel {

    companion object : PlayViewModel()

    private val sourceRepo by lazy {
        RepoManager()
    }

    private val rService by lazy {
        rService()
    }

    private val sourceListRepo by lazy {
        SourceListRepo()
    }

    private lateinit var rServiceUrlWithAuth: Pair<String, Pair<String, String>?>

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun getImageFileInfo(
        api: RemoteApi,
        recursive: Boolean = false,
        supportVideo: Boolean = false,
        sortRule: Int = -1
    ): UiState<List<Any>> {

        if (api is RcloneRemoteApi && api !is Local && api !is WebDav|| (api is WebDav && !USE_NATIVE_WEBDAV_CLIENT)) {
            sourceListRepo.setUpSourcesAndConfig(api)
            rServiceUrlWithAuth = startRService(api as RcloneRemoteApi)!!
        }

        var imageFiles = sourceRepo.getItems(api, recursive) {

            when (it) {
                is NetworkFile -> {
                    it.mimeType in SUPPORT_MIME_FILTER_IMAGE || it.path.getExtension() in IMAGE_EXT_SUPPORT || (supportVideo && it.mimeType in SUPPORT_MIME_FILTER_VIDEO)
                }

                is String -> {
                    it.getExtension() in IMAGE_EXT_SUPPORT || (supportVideo && it.getExtension() in VIDEO_EXT_SUPPORT)
                }

                else -> false
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

                    if (api is WebDav && USE_NATIVE_WEBDAV_CLIENT) {
                        val list = mutableListOf<UrlWithAuth>()
                        imageFiles.getOrNull()?.forEach {networkFile ->
                            list.add(
                                UrlWithAuth(
                                    (networkFile as NetworkFile).let {
                                        StringBuilder().append(api.url.replace(Url(api.url).fullPath, ""))
                                            .append(if (it.path.startsWith("/")) it.path else "/${it.path}")
                                            .toString()
                                    },
                                    "Authorization",
                                    "Basic ${Base64.encode("${api.user}:${api.passwd}".toByteArray())}"
                                )
                            )
                        }
                        UiState.Content(list)
                    } else {
                        val list = mutableListOf<Any>()
//          val rServiceUrlWithAuth = startRService(api)
                        rServiceUrlWithAuth.run {
                            imageFiles.getOrNull()?.forEachIndexed { _, networkFile ->
                                rServiceUrlWithAuth.second?.apply {
                                    list.add(
                                        UrlWithAuth(
                                            StringBuilder().append(rServiceUrlWithAuth.first)
                                                .append((networkFile as NetworkFile).path)
                                                .toString(),
                                            first,
                                            second
                                        )
                                    )
                                } ?: run {
                                    list.add(
                                        StringBuilder().append(rServiceUrlWithAuth.first)
                                            .append((networkFile as NetworkFile).path).toString()
                                    )
                                }
                            }

                            if (list.size > 0) {
                                UiState.Content(list)
                            } else {
                                UiState.Error("No content found!")
                            }
                        } ?: UiState.Error("Service start failed!")
                    }

                }

                is GitHubSource -> {
                    // add Auth token
                    if (api.token.isBlank()) {
                        UiState.Content(imageFiles.getOrNull()!!)
                    } else {
                        val list = mutableListOf<UrlWithAuth>()
                        imageFiles.getOrNull()?.forEach {
                            list.add(
                                UrlWithAuth(
                                    it as String,
                                    "Authorization",
                                    "token ${api.token}"
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
            UiState.Error(imageFiles.exceptionOrNull()?.message ?: "Error")
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

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun startRService(remoteStorage: RcloneRemoteApi): Pair<String, Pair<String, String>?>? {
        val result = CompletableDeferred<Pair<String, Pair<String, String>?>?>()

        val user = getStringRandom(12)!!
        val pass = getStringRandom(12)!!

        val inputData = if (DEBUG) {
            dataOf(
                R_SERVICE_WORKER_ARG_BASE_URL to "showcase",
                R_SERVICE_WORKER_ARG_ALLOW_REMOTE_ACCESS to true,
                R_SERVICE_WORKER_ARG_PORT to DEFAULT_SERVE_PORT,
                R_SERVICE_WORKER_ARG_REMOTE to remoteStorage.name,
                R_SERVICE_WORKER_ARG_SERVE_PATH to remoteStorage.path
            )
        } else {
            dataOf(
                R_SERVICE_WORKER_ARG_USER to user,
                R_SERVICE_WORKER_ARG_PASSWD to pass,
                R_SERVICE_WORKER_ARG_REMOTE to remoteStorage.name,
                R_SERVICE_WORKER_ARG_SERVE_PATH to remoteStorage.path
            )
        }

        viewModelScope.launch {
            rService.startRService(inputData) { data ->
                Log.d("RServiceManager onProgress: $data")
                val url = data?.getString(R_SERVICE_ACCESS_BASE_URL, "")
                if (url != null && !result.isCompleted) {
                    val encodeToString = Base64.encode("$user:$pass".toByteArray())
                    result.complete(url to ("Authorization" to "Basic $encodeToString"))
                }
            }
        }

        return result.await()
    }

    fun onClear() {
        rService.stopRService()
//        runBlocking {
//            sourceListRepo.clearRcloneConfig()
//        }
    }
}