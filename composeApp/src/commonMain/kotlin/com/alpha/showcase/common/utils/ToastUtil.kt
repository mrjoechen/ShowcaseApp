package com.alpha.showcase.common.utils

import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource


/**
 * Created by chenqiao on 2022/11/22.
 * e-mail : mrjctech@gmail.com
 */
object ToastUtil {

    fun error(errMsg: String) {
        Log.e(errMsg)
    }

    fun success(msg: String) {
        Log.i(msg)
    }

    fun toast(msg: String) {
        Log.i(msg)
    }

    fun error(errMsg: Int) {
        Log.e("$errMsg")
    }

    fun success(msg: Int) {
        Log.i("$msg")
    }

    fun toast(errMsg: StringResource) {
        Log.i(errMsg.key)
    }

    fun error(errMsg: StringResource) {
        Log.e(errMsg.key)
    }

    fun success(errMsg: StringResource) {
        Log.i(errMsg.key)
    }

}