package com.alpha.showcase.common

import kotlin.js.JsModule


@JsModule("showcase-heic")

private external object NativeHeicBridge {
    fun decode(id: String, encoded: String, success: (Int, Int, String) -> Unit, failure: (String) -> Unit)
    fun cancel(id: String)
}

internal actual object HeicBridge {
    actual fun decode(id: String, encoded: String, success: (Int, Int, String) -> Unit, failure: (String) -> Unit) =
        NativeHeicBridge.decode(id, encoded, success, failure)
    actual fun cancel(id: String) = NativeHeicBridge.cancel(id)
}
