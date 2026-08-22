package com.alpha.showcase.common.utils

import com.alpha.showcase.common.toast.ToastManager
import com.alpha.showcase.common.toast.ToastType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString


/**
 * Created by chenqiao on 2022/11/22.
 * e-mail : mrjctech@gmail.com
 */
object ToastUtil {

    private val resourceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun error(
        errMsg: String,
        duration: Long = 2500L,
        source: String = ""
    ) {
        Log.e(errMsg)
        ToastManager.showToast(
            type = ToastType.ERROR,
            message = errMsg,
            duration = duration,
            source = source
        )
    }

    fun success(
        msg: String,
        duration: Long = 2500L,
        source: String = ""
    ) {
        Log.i(msg)
        ToastManager.showToast(
            type = ToastType.SUCCESS,
            message = msg,
            duration = duration,
            source = source
        )
    }

    fun toast(msg: String,
              duration: Long = 2500L,
              source: String = ""
    ) {
        Log.i(msg)
        ToastManager.showToast(
            type = ToastType.INFO,
            message = msg,
            duration = duration,
            source = source
        )
    }

    fun toast(errMsg: StringResource) {
        Log.i(errMsg.key)
        showResourceToast(ToastType.INFO, errMsg)
    }

    fun error(errMsg: StringResource) {
        Log.e(errMsg.key)
        showResourceToast(ToastType.ERROR, errMsg)
    }

    fun success(errMsg: StringResource) {
        Log.i(errMsg.key)
        showResourceToast(ToastType.SUCCESS, errMsg)
    }

    private fun showResourceToast(type: ToastType, resource: StringResource) {
        resourceScope.launch {
            ToastManager.showToast(
                type = type,
                message = getString(resource),
                duration = 2500L,
                source = ""
            )
        }
    }

}
