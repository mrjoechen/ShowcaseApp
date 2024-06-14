package com.alpha.showcase.common.ui.vm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

abstract class BaseViewModel {
  val viewModelScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

}

interface BaseState
sealed interface UiState<out T> : BaseState {
  data class Content<out T>(val data: T) : UiState<T>
  object Loading : UiState<Nothing>
  data class Error(val msg: String? = "Error") : UiState<Nothing>
}

val UiState<*>.succeeded
  get() = this is UiState.Content && data != null