package com.alpha.showcase.common.utils

import com.alpha.showcase.common.toast.ToastManager
import com.alpha.showcase.common.toast.ToastType
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource


/**
 * Created by chenqiao on 2022/11/22.
 * e-mail : mrjctech@gmail.com
 */
object ToastUtil {

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
        ToastManager.showToast(
            type = ToastType.INFO,
            message = errMsg.key,
            duration = 2500L,
            source = ""
        )
    }

    fun error(errMsg: StringResource) {
        Log.e(errMsg.key)
        ToastManager.showToast(
            type = ToastType.ERROR,
            message = errMsg.key,
            duration = 2500L,
            source = ""
        )
    }

    fun success(errMsg: StringResource) {
        Log.i(errMsg.key)
        ToastManager.showToast(
            type = ToastType.SUCCESS,
            message = errMsg.key,
            duration = 2500L,
            source = ""
        )
    }

}