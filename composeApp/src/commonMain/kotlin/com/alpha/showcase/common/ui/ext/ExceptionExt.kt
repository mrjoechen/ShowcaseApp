package com.alpha.showcase.common.ui.ext

fun Throwable.getSimpleMessage(): String {
    return this.message?.lines()?.first()?.split(":")?.first()?:toString()
}