package com.alpha.showcase.common.smb

import kotlin.concurrent.Volatile

@Volatile
private var smbBridgeInvoker: ((String) -> String?)? = null

fun registerSmbBridgeInvoker(invoker: (String) -> String?) {
    smbBridgeInvoker = invoker
}

internal fun invokeRegisteredSmbBridge(requestJson: String): String {
    val invoker = smbBridgeInvoker
        ?: throw IllegalStateException(
            "SMB bridge invoker is not registered. Ensure iosApp registers SMB bridge on launch."
        )

    return invoker(requestJson)
        ?: throw IllegalStateException("SMB bridge returned null response")
}
