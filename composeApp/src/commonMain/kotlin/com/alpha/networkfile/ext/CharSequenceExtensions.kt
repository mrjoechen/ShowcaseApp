package com.alpha.networkfile.ext

fun <T : CharSequence> T.takeIfNotBlank(): T? = ifBlank {null}

fun <T : CharSequence> T.takeIfNotEmpty(): T? = ifEmpty {null}
