package com.alpha.showcase.common.ui.source

import com.alpha.showcase.common.networkfile.storage.StorageSources
import com.alpha.showcase.common.networkfile.storage.remote.OAuthRcloneApi
import com.alpha.showcase.common.networkfile.storage.remote.RcloneRemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import com.alpha.showcase.common.repo.SourceListRepo
import com.alpha.showcase.common.ui.vm.BaseViewModel
import com.alpha.showcase.common.ui.vm.UiState
import com.alpha.showcase.common.ui.vm.succeeded
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

open class SourceViewModel: BaseViewModel() {

  companion object: SourceViewModel()

  private val sourcesRepo = SourceListRepo()
  private val _sourceListStateFlow = MutableStateFlow<UiState<StorageSources>>(UiState.Loading)
  val sourceListStateFlow = _sourceListStateFlow as StateFlow<UiState<StorageSources>>

  init {
    viewModelScope.launch {
      getSourceList()
    }
  }

  private suspend fun getSourceList(){
    _sourceListStateFlow.emit(UiState.Content(sourcesRepo.getSources()))
  }

  suspend fun addSourceList(remoteApi: RemoteApi<Any>): Boolean{
    val result = sourcesRepo.saveSource(remoteApi)
    if (result){
      val storageSources = sourcesRepo.getSources()
      _sourceListStateFlow.emit(UiState.Content(storageSources))
    }
    return result
  }

  suspend fun configSource(remoteApi: RemoteApi<Any>){
    return withContext(Dispatchers.Default) {
      sourcesRepo.setUpSourcesAndConfig(remoteApi)
    }
  }

  suspend fun deleteSource(remoteApi: RemoteApi<Any>): Boolean{
    val result = sourcesRepo.deleteSource(remoteApi)
    val storageSources = sourcesRepo.getSources()
    _sourceListStateFlow.emit(UiState.Content(storageSources))
    return result
  }


  fun checkDuplicateName(name: String): Boolean {
    sourceListStateFlow.value.let {
      if (it is UiState.Content && it.succeeded) {
        it.data.sources.forEach {source ->
          if (source.name == name) {
            return false
          }
        }
      }
    }
    return true
  }

  fun onStartPlay(remoteApi: RemoteApi<Any>){

  }

  suspend fun checkConnection(remoteApi: RemoteApi<Any>): Result<Any> =
    sourcesRepo.checkConnection(remoteApi)

  suspend fun <T: OAuthRcloneApi> linkOAuth(
    oAuthRcloneApi: T,
    onRetrieveOauthUrl: (String?) -> Unit
  ): T? {
    val result = sourcesRepo.linkConnection(oAuthRcloneApi) {
      viewModelScope.launch {
        withContext(Dispatchers.Main) {
          onRetrieveOauthUrl(it)
        }
      }
    }
    return result
  }

  suspend fun getFilesItemList(remoteApi: RcloneRemoteApi, path: String): Result<Any> =
    sourcesRepo.getSourceFileDirItems(remoteApi, path)


}