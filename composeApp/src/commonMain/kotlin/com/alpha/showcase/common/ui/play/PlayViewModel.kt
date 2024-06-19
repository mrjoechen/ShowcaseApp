package com.alpha.showcase.common.ui.play

import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.external.GitHubSource
import com.alpha.showcase.common.networkfile.storage.remote.Local
import com.alpha.showcase.common.networkfile.storage.remote.RcloneRemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteStorage
import com.alpha.showcase.common.repo.RepoManager
import com.alpha.showcase.common.ui.settings.SortRule
import com.alpha.showcase.common.ui.vm.UiState
import com.alpha.showcase.common.utils.getExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.suspendCoroutine


const val UNIQUE_WORK_ID = "RService"
val SUPPORT_MIME_FILTER_IMAGE = listOf("image/jpeg", "image/webp", "image/png", "image/bmp")
val SUPPORT_MIME_FILTER_VIDEO = listOf("video/mp4", "video/x-matroska", "video/webm", "video/quicktime")
val IMAGE_EXT_SUPPORT =
  listOf("jpg", "png", "jpeg", "bmp", "webp", "heic", "JPG", "PNG", "JPEG", "BMP", "WEBP", "HEIC")
val VIDEO_EXT_SUPPORT = listOf("mp4", "mkv", "webm", "mov")

class PlayViewModel {

  private val sourceRepo by lazy {
    RepoManager()
  }

  private lateinit var rServiceUrlWithAuth: Pair<String, Pair<String, String>?>

  suspend fun getImageFileInfo(
    api: RemoteApi<Any>,
    recursive: Boolean = false,
    supportVideo: Boolean = false,
    sortRule: Int = -1
  ): UiState<List<Any>> {

    if (api is RcloneRemoteApi && api !is Local) {
      rServiceUrlWithAuth = startRService(api)!!
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
          val list = mutableListOf<Any>()
//          val rServiceUrlWithAuth = startRService(api)
          rServiceUrlWithAuth.run {
            imageFiles.getOrNull()?.forEachIndexed { _, networkFile ->
              rServiceUrlWithAuth.second?.apply {
                list.add(
                  UrlWithAuth(
                    StringBuilder().append(rServiceUrlWithAuth.first)
                      .append((networkFile as NetworkFile).path).toString(),
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

        is GitHubSource -> {
          // add Auth token
          if (api.token.isBlank()){
            UiState.Content(imageFiles.getOrNull()!!)
          }else {
            val list = mutableListOf<UrlWithAuth>()
            imageFiles.getOrNull()?.forEach{
              list.add(UrlWithAuth(it as String, "Authorization", "token ${api.token}"))
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

  private suspend fun startRService(remoteStorage: RcloneRemoteApi): Pair<String, Pair<String, String>?>? {
    return suspendCoroutine {scope ->

    }
  }

}