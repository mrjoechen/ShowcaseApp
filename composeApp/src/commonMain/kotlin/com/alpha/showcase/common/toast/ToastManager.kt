package com.alpha.showcase.common.toast

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.alpha.showcase.common.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

object ToastManager {
    // Toast消息队列
    private var toastQueue by mutableStateOf<List<ToastMessage>>(emptyList())

    // 当前显示的Toast
    var currentToastFlow  = MutableStateFlow<ToastMessage?>(null)

    // 是否有活跃的Toast任务
    private var toastJob: kotlinx.coroutines.Job? = null

    private val scope = CoroutineScope(Dispatchers.Main)

    // 显示一个Toast
    fun showToast(
        message: String,
        type: ToastType = ToastType.INFO,
        duration: Long = 2000L,
        source: String,
        priority: Int = 0
    ) {
        val toastMessage = ToastMessage(message, type, duration, source, priority)
        addToQueue(toastMessage)
        Log.d("ToastManager", "showToast: $toastMessage")
    }

    // 添加到队列并排序
    private fun addToQueue(toast: ToastMessage) {
        val currentQueue = toastQueue.toMutableList()
        currentQueue.add(toast)
        // 根据优先级排序
        currentQueue.sortByDescending { it.priority }
        toastQueue = currentQueue

        // 如果当前没有显示的Toast，显示队列中的第一个
        if (currentToastFlow.value == null && toastQueue.isNotEmpty() && toastJob?.isActive != true) {
            showNextToast()
        }
    }

    // 显示下一个Toast
    private fun showNextToast() {
        if (toastQueue.isEmpty()) {
            scope.launch {
                currentToastFlow.emit(null)
            }
            return
        }

        // 获取并移除队列中的第一个Toast
        val nextToast = toastQueue[0]
        toastQueue = toastQueue.drop(1)
        scope.launch {
            currentToastFlow.emit(nextToast)
        }
        // 设置定时器，时间到后显示下一个Toast
        toastJob = scope.launch {
            delay(nextToast.duration)
            currentToastFlow.emit(null)
            showNextToast()
        }
    }
}


enum class ToastType {
    SUCCESS,
    FAILED,
    ERROR,
    INFO
}

data class ToastMessage(
    val message: String,
    val type: ToastType = ToastType.INFO,
    val duration: Long = 2000L, // 默认显示2秒
    val source: String, // 消息来源
    val priority: Int = 0 // 优先级，数字越大优先级越高
)