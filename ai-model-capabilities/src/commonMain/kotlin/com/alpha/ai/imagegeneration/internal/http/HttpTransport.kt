package com.alpha.ai.imagegeneration.internal.http

internal interface HttpTransport {
    fun prepare(request: HttpRequest, limitBytes: Long): PreparedHttpCall
}

internal interface PreparedHttpCall {
    fun start(): RunningHttpCall
}

internal interface RunningHttpCall {
    suspend fun await(): HttpResponse
    fun cancelIfActive()
}
