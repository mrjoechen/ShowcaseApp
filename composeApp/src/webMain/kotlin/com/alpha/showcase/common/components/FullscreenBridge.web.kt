package com.alpha.showcase.common.components

internal fun requestDocumentFullscreen(): Unit = js(
    "void (() => { " +
        "try { " +
        "const request = document.documentElement.requestFullscreen(); " +
        "if (request != null && typeof request.catch === 'function') request.catch(() => {}); " +
        "} catch (_) {} " +
        "})()"
)

internal fun exitDocumentFullscreen(): Unit = js(
    "void (() => { " +
        "try { " +
        "if (document.fullscreenElement == null) return; " +
        "const exit = document.exitFullscreen(); " +
        "if (exit != null && typeof exit.catch === 'function') exit.catch(() => {}); " +
        "} catch (_) {} " +
        "})()"
)
