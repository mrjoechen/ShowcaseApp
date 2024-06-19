package com.alpha.showcase.common.ui.vm

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope

abstract class BaseViewModel {
  val viewModelScope: CoroutineScope = MainScope()

}

interface BaseState
sealed interface UiState<out T> : BaseState {
  data class Content<out T>(val data: T) : UiState<T>
  object Loading : UiState<Nothing>
  data class Error(val msg: String? = "Error") : UiState<Nothing>
}

val UiState<*>.succeeded
  get() = this is UiState.Content && data != null