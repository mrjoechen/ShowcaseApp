package com.alpha.showcase.common.utils

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.serialization.json.Json


object HttpClientUtils {
    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
}



suspend inline fun <reified T> HttpClient.get(
    block: HttpRequestBuilder.() -> Unit = {}
): Result<T> = request {
    get { block() }
}

suspend inline fun <reified T> request(
    requester: () -> HttpResponse
): Result<T> = try {
    val httpResponse: HttpResponse = requester()
    val response: T = httpResponse.body()
    Result.success(response)
} catch (exception: ResponseException) {
    Result.failure(exception)
} catch (exception: Throwable) {
    exception.printStackTrace()
    Result.failure(exception)
}