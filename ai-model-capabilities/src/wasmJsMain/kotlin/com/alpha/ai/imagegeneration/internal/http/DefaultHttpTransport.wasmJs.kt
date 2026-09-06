package com.alpha.ai.imagegeneration.internal.http

/** Browser builds may share contracts, but cannot dispatch AI provider credentials. */
internal actual fun defaultHttpTransport(): HttpTransport = object : HttpTransport {
    override fun prepare(request: HttpRequest, limitBytes: Long): PreparedHttpCall =
        throw ImageGenerationTransportException("AI capabilities are unavailable in browsers")
}
