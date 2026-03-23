package com.alpha.showcase.common.update

import com.alpha.showcase.common.ui.vm.BaseViewModel
import com.alpha.showcase.common.utils.Log
import com.alpha.showcase.common.utils.ToastUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.app_update_failed
import showcaseapp.composeapp.generated.resources.up_to_date

data class AppUpdateUiState(
    val latestUpdate: UpdateInfo? = null,
    val checking: Boolean = false,
    val installing: Boolean = false,
    val installProgress: UpdateInstallProgress? = null
)

open class AppUpdateViewModel : BaseViewModel() {

    companion object : AppUpdateViewModel()

    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState: StateFlow<AppUpdateUiState> = _uiState

    fun checkForUpdate(
        showUpToDateToast: Boolean = false,
        showFailureToast: Boolean = false
    ) {
        val currentState = _uiState.value
        if (currentState.checking || currentState.installing) return

        viewModelScope.launch {
            _uiState.value = currentState.copy(checking = true)

            AppUpdateManager.checkForUpdate()
                .onSuccess { result ->
                    when (result) {
                        UpdateCheckResult.UpToDate -> {
                            if (showUpToDateToast) {
                                ToastUtil.toast(Res.string.up_to_date)
                            }
                        }

                        is UpdateCheckResult.Available -> {
                            _uiState.value = _uiState.value.copy(
                                latestUpdate = result.info,
                                installProgress = null
                            )
                        }
                    }
                }
                .onFailure { error ->
                    if (showFailureToast) {
                        val errorMessage = error.message
                        if (errorMessage.isNullOrBlank()) {
                            ToastUtil.error(Res.string.app_update_failed)
                        } else {
                            ToastUtil.error(errorMessage)
                        }
                    } else {
                        Log.w("Check update failed: ${error.message ?: "unknown"}")
                    }
                }

            _uiState.value = _uiState.value.copy(checking = false)
        }
    }

    fun installUpdate() {
        val currentState = _uiState.value
        val updateInfo = currentState.latestUpdate ?: return
        if (currentState.installing) return

        viewModelScope.launch {
            _uiState.value = currentState.copy(
                installing = true,
                installProgress = UpdateInstallProgress(0L, updateInfo.asset?.sizeBytes?.takeIf { it > 0 })
            )

            AppUpdateManager.installUpdate(updateInfo) { progress ->
                _uiState.value = _uiState.value.copy(
                    installing = true,
                    installProgress = progress
                )
            }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        latestUpdate = null,
                        installing = false,
                        installProgress = null
                    )
                }
                .onFailure { error ->
                    val errorMessage = error.message
                    if (errorMessage.isNullOrBlank()) {
                        ToastUtil.error(Res.string.app_update_failed)
                    } else {
                        ToastUtil.error(errorMessage)
                    }
                    _uiState.value = _uiState.value.copy(
                        installing = false,
                        installProgress = null
                    )
                }
        }
    }

    fun dismissUpdateDialog() {
        if (_uiState.value.installing) return
        _uiState.value = _uiState.value.copy(
            latestUpdate = null,
            installProgress = null
        )
    }
}
