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

  suspend fun addSourceList(remoteApi: RemoteApi): Boolean{
    val result = sourcesRepo.saveSource(remoteApi)
    if (result){
      val storageSources = sourcesRepo.getSources()
      _sourceListStateFlow.emit(UiState.Content(storageSources))
    }
    return result
  }

  suspend fun deleteSource(remoteApi: RemoteApi): Boolean{
    val result = sourcesRepo.deleteSource(remoteApi)
    val storageSources = sourcesRepo.getSources()
    _sourceListStateFlow.emit(UiState.Content(storageSources))
    return result
  }

  fun getSource(name: String): RemoteApi?{
    if (_sourceListStateFlow.value is UiState.Content){
      return (_sourceListStateFlow.value as UiState.Content<StorageSources>).data.sources.find { it.name == name }
    }
    return null
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

  fun onStartPlay(remoteApi: RemoteApi){

  }

  suspend fun checkConnection(remoteApi: RemoteApi): Result<Any> =
    sourcesRepo.checkConnection(remoteApi)

  suspend fun getFilesItemList(remoteApi: RcloneRemoteApi, path: String): Result<Any> =
    sourcesRepo.getSourceFileDirItems(remoteApi, path)


}