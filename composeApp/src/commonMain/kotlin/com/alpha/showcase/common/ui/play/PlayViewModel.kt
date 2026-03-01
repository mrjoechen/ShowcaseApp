package com.alpha.showcase.common.ui.play

import com.alpha.showcase.common.DEBUG
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

        var imageFiles = sourceRepo.getItems(api, recursive) {
            it.isImage() || (supportVideo && it.isVideo())
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
