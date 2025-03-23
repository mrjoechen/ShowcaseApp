package com.alpha.showcase.common.toast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ToastManager {
    // Toast消息队列
    private var toastQueue by mutableStateOf<List<ToastMessage>>(emptyList())

    // 当前显示的Toast
    var currentToast by mutableStateOf<ToastMessage?>(null)
        private set

    // 是否有活跃的Toast任务
    private var toastJob: kotlinx.coroutines.Job? = null

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
    }

    // 添加到队列并排序
    private fun addToQueue(toast: ToastMessage) {
        val currentQueue = toastQueue.toMutableList()
        currentQueue.add(toast)
        // 根据优先级排序
        currentQueue.sortByDescending { it.priority }
        toastQueue = currentQueue

        // 如果当前没有显示的Toast，显示队列中的第一个
        if (currentToast == null && toastQueue.isNotEmpty() && toastJob?.isActive != true) {
            showNextToast()
        }
    }

    // 显示下一个Toast
    private fun showNextToast() {
        if (toastQueue.isEmpty()) {
            currentToast = null
            return
        }

        // 获取并移除队列中的第一个Toast
        val nextToast = toastQueue[0]
        toastQueue = toastQueue.drop(1)
        currentToast = nextToast

        // 设置定时器，时间到后显示下一个Toast
        toastJob = CoroutineScope(Dispatchers.Main).launch {
            delay(nextToast.duration)
            currentToast = null
            showNextToast()
        }
    }
}

// 创建CompositionLocal提供ToastManager
val LocalToastManager = compositionLocalOf<ToastManager> {
    error("ToastManager not provided")
}

@Composable
fun rememberToastManager(): ToastManager {
    return remember { ToastManager() }
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