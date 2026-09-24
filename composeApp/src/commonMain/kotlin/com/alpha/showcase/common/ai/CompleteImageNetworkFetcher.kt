@file:OptIn(coil3.annotation.ExperimentalCoilApi::class)

package com.alpha.showcase.common.ai

import coil3.network.*
import coil3.network.ktor3.asNetworkClient
import coil3.request.Options
import io.ktor.client.HttpClient
import okio.IOException

/** A range response is not a whole file even when an image decoder can display it. */
internal fun completeImageNetworkFetcherFactory() = NetworkFetcher.Factory(
    networkClient = {
        val delegate = HttpClient().asNetworkClient()
        object : NetworkClient {
            override suspend fun <T> executeRequest(request: NetworkRequest, block: suspend (NetworkResponse) -> T): T =
                delegate.executeRequest(request) { response ->
                    if (response.isPartialImage) throw IOException("Incomplete HTTP image response")
                    block(response)
                }
        }
    },
    cacheStrategy = { object : CacheStrategy {
        override suspend fun read(cacheResponse: NetworkResponse, networkRequest: NetworkRequest, options: Options): CacheStrategy.ReadResult =
            if (cacheResponse.isPartialImage) CacheStrategy.ReadResult(networkRequest)
            else CacheStrategy.DEFAULT.read(cacheResponse, networkRequest, options)

        override suspend fun write(cacheResponse: NetworkResponse?, networkRequest: NetworkRequest, networkResponse: NetworkResponse, options: Options): CacheStrategy.WriteResult =
            CacheStrategy.DEFAULT.write(cacheResponse, networkRequest, networkResponse, options)
    } },
)

private val NetworkResponse.isPartialImage: Boolean get() = code == 206 || headers["Content-Range"] != null
