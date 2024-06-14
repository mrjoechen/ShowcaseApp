package com.alpha.showcase.common.utils

import showcaseapp.composeapp.generated.resources.Res
import io.ktor.http.URLBuilder
import showcaseapp.composeapp.generated.resources.branch_name_is_invalid
import showcaseapp.composeapp.generated.resources.host_is_invalid
import showcaseapp.composeapp.generated.resources.ip_is_invalid
import showcaseapp.composeapp.generated.resources.name_is_invalid
import showcaseapp.composeapp.generated.resources.path_is_invalid
import showcaseapp.composeapp.generated.resources.port_is_invalid
import showcaseapp.composeapp.generated.resources.url_is_invalid


fun checkName(
    name: String?,
    showToast: Boolean = false,
    block: (() -> Unit)? = null
): Boolean {
    if (name.isNullOrEmpty()) {
        if (showToast) ToastUtil.error(Res.string.name_is_invalid)
        return false
    }

    if (name.contains("/") ||
        name.contains("\\") ||
        name.contains(":") ||
        name.contains("*") ||
        name.contains("?") ||
        name.contains("\"") ||
        name.contains("<") ||
        name.contains(">") ||
        name.contains("|") ||
        name.contains(" ")
    ) {
        if (showToast) ToastUtil.error(Res.string.name_is_invalid)
        return false
    }
    block?.invoke()
    return true
}

// check if the host is legal
fun checkHost(
    host: String?,
    showToast: Boolean = false,
    block: (() -> Unit)? = null
): Boolean {
    if (host == null || host.isEmpty()) {
        if (showToast) ToastUtil.error(Res.string.host_is_invalid)
        return false
    }

    if (host.contains("/") ||
        host.contains("\\") ||
        host.contains(":") ||
        host.contains("*") ||
        host.contains("?") ||
        host.contains("\"") ||
        host.contains("<") ||
        host.contains(">") ||
        host.contains("|")
    ) {
        if (showToast) ToastUtil.error(Res.string.host_is_invalid)
        return false
    }

    if (host.matches(Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\$"))) {
        val ipSplit = host.split(".")
        var result = true
        for (i in ipSplit) {
            result = result && (i.toInt() in 0..255)
        }
        return if (result) {
            block?.invoke()
            true
        } else {
            if (showToast) ToastUtil.error(Res.string.host_is_invalid)
            false
        }
    }
    block?.invoke()
    return true
}

fun checkIp(ip: String?, showToast: Boolean = false, block: (() -> Unit)? = null): Boolean {
    if (ip == null || ip.isEmpty()) {
        if (showToast) ToastUtil.error(Res.string.ip_is_invalid)
        return false
    }
    if (ip.matches(Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\$"))) {
        val ipSplit = ip.split(".")
        for (i in ipSplit) {
            if (i.toInt() !in 0..255) {
                if (showToast) ToastUtil.error(Res.string.ip_is_invalid)
                return false
            }
        }
    } else {
        if (showToast) ToastUtil.error(Res.string.ip_is_invalid)
        return false
    }
    block?.invoke()
    return true
}

fun checkPort(
    port: String,
    showToast: Boolean = false,
    block: (() -> Unit)? = null
): Boolean {
    if (port.isBlank()) {
        block?.invoke()
        return true
    }
    try {
        if (port.toInt() !in 1..65535) {
            if (showToast) ToastUtil.error(Res.string.port_is_invalid)
            return false
        }
    } catch (e: Exception) {
        if (showToast) ToastUtil.error(Res.string.port_is_invalid)
        return false
    }

    block?.invoke()
    return true
}

fun checkPath(
    path: String,
    showToast: Boolean = false,
    block: (() -> Unit)? = null
): Boolean {
    if (path.contains("\\") ||
        path.contains(":") ||
        path.contains("*") ||
        path.contains("?") ||
        path.contains("\"") ||
        path.contains("<") ||
        path.contains(">") ||
        path.contains("|")
    ) {
        if (showToast) ToastUtil.error(Res.string.path_is_invalid)
        return false
    }

    block?.invoke()
    return true
}

fun isBranchNameValid(
    branchName: String,
    showToast: Boolean = false,
    block: (() -> Unit)? = null
): Boolean {
    // 分支名称只能包含字母、数字、破折号和下划线
    val pattern = Regex("^[a-zA-Z0-9-_]+$")
    // 使用正则表达式进行匹配
    val matches = pattern.matches(branchName)
    if (!matches) {
        if (showToast) ToastUtil.error(Res.string.branch_name_is_invalid)
        return false
    }

    block?.invoke()
    return true
}


// check if url is legal
fun checkUrl(
    url: String?,
    showToast: Boolean = false,
    block: (() -> Unit)? = null
): Boolean {

    if (url.isNullOrEmpty()) {
        if (showToast) ToastUtil.error(Res.string.url_is_invalid)
        return false
    }
    val match = try {
        val uri = URLBuilder(url).build()
        val schemaMatches = uri.protocol.name.matches(Regex("^(http|https)\$"))
        val hostMatches =
            uri.host.matches(Regex("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\$"))
                    || uri.host.matches(Regex("^(?:[a-zA-Z0-9]+(?:-[a-zA-Z0-9]+)*\\.)+[a-zA-Z]+\$"))
        val portMatches = uri.port == -1 || uri.port in 1..65535
        val pathMatches = uri.encodedPath.isBlank() || uri.encodedPath.matches(Regex("^(?:/[^/]+)+\$"))

        schemaMatches && hostMatches && portMatches && pathMatches
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    return if (match) {
        block?.invoke()
        true
    } else {
        if (showToast) ToastUtil.error(Res.string.url_is_invalid)
        false
    }
}