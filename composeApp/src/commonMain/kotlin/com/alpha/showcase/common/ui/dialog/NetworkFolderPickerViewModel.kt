package com.alpha.showcase.common.ui.dialog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.Ftp
import com.alpha.showcase.common.networkfile.storage.remote.RcloneRemoteApi
import com.alpha.showcase.common.networkfile.storage.remote.Sftp
import com.alpha.showcase.common.networkfile.storage.remote.Smb
import com.alpha.showcase.common.networkfile.storage.remote.WebDav
import com.alpha.showcase.repo.NativeFtpSourceRepo
import com.alpha.showcase.repo.NativeSftpSourceRepo
import com.alpha.showcase.repo.NativeSmbSourceRepo
import com.alpha.showcase.repo.NativeWebdavSourceRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class NetworkFolderPickerUiState(
    val currentPath: String = "/",
    val items: List<NetworkFolderItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val displayMode: DisplayMode = DisplayMode.FOLDERS_ONLY
)

data class NetworkFolderItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: String
)

enum class DisplayMode {
    FOLDERS_ONLY,
    FOLDERS_AND_FILES
}

class NetworkFolderPickerViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(NetworkFolderPickerUiState())
    val uiState: StateFlow<NetworkFolderPickerUiState> = _uiState.asStateFlow()
    
    private var remoteApi: RcloneRemoteApi? = null
    
    // Repository instances
    private val ftpRepo = NativeFtpSourceRepo()
    private val sftpRepo = NativeSftpSourceRepo()
    private val smbRepo = NativeSmbSourceRepo()
    private val webdavRepo = NativeWebdavSourceRepo()
    
    fun initialize(remote: RcloneRemoteApi, initialPath: String) {
        remoteApi = remote
        _uiState.value = _uiState.value.copy(currentPath = initialPath)
        loadDirectory(initialPath)
    }
    
    fun setDisplayMode(mode: DisplayMode) {
        _uiState.value = _uiState.value.copy(displayMode = mode)
        refreshCurrentPath()
    }
    
    fun navigateToPath(path: String) {
        loadDirectory(path)
    }
    
    fun navigateUp() {
        val currentPath = _uiState.value.currentPath
        if (currentPath.isNotEmpty() && currentPath != "/") {
            val parentPath = if (currentPath.contains("/")) {
                val lastSlashIndex = currentPath.lastIndexOf("/")
                if (lastSlashIndex == 0) "/" else currentPath.substring(0, lastSlashIndex)
            } else {
                "/"
            }
            loadDirectory(parentPath)
        }
    }
    
    fun refreshCurrentPath() {
        loadDirectory(_uiState.value.currentPath)
    }
    
    private fun loadDirectory(path: String) {
        val remote = remoteApi ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null
            )
            
            try {
                val result = getDirectoryItems(remote, path)
                result.fold(
                    onSuccess = { networkFiles ->
                        val items = networkFiles.map { networkFile ->
                            NetworkFolderItem(
                                name = networkFile.fileName,
                                path = networkFile.path,
                                isDirectory = networkFile.isDirectory,
                                size = networkFile.size,
                                lastModified = networkFile.modTime
                            )
                        }.let { allItems ->
                            // 根据显示模式过滤
                            when (_uiState.value.displayMode) {
                                DisplayMode.FOLDERS_ONLY -> allItems.filter { it.isDirectory }
                                DisplayMode.FOLDERS_AND_FILES -> allItems
                            }
                        }.sortedWith { a, b ->
                            // 文件夹排在前面，然后按名称排序
                            when {
                                a.isDirectory && !b.isDirectory -> -1
                                !a.isDirectory && b.isDirectory -> 1
                                else -> a.name.compareTo(b.name, ignoreCase = true)
                            }
                        }
                        
                        _uiState.value = _uiState.value.copy(
                            currentPath = path,
                            items = items,
                            isLoading = false,
                            errorMessage = null
                        )
                    },
                    onFailure = { exception ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = exception.message ?: "未知错误"
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "网络错误"
                )
            }
        }
    }
    
    private suspend fun getDirectoryItems(remote: RcloneRemoteApi, path: String): Result<List<NetworkFile>> {
        return try {
            withContext(Dispatchers.IO){
                val updatedRemote = updateRemotePath(remote, path)
                when (updatedRemote) {
                    is Ftp -> ftpRepo.getFileDirItems(updatedRemote)
                    is Sftp -> sftpRepo.getFileDirItems(updatedRemote)
                    is Smb -> smbRepo.getFileDirItems(updatedRemote)
                    is WebDav -> webdavRepo.getFileDirItems(updatedRemote)
                    else -> Result.failure(Exception("不支持的远程存储类型"))
                }
            }

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun updateRemotePath(remote: RcloneRemoteApi, newPath: String): RcloneRemoteApi {
        return when (remote) {
            is Ftp -> remote.copy(path = newPath)
            is Sftp -> remote.copy(path = newPath)
            is Smb -> remote.copy(path = newPath)
            is WebDav -> remote.copy(path = newPath)
            else -> remote
        }
    }
}