package com.alpha.showcase.common.repo

import com.alpha.showcase.api.pexels.PexelsApi
import com.alpha.showcase.api.tmdb.TmdbApi
import com.alpha.showcase.api.unsplash.UnsplashApi

internal actual val externalImageApiRequiresUserCredentials: Boolean = false

internal actual fun createUnsplashApi(apiKey: String?): UnsplashApi {
    return apiKey?.takeIf { it.isNotBlank() }?.let(::UnsplashApi) ?: UnsplashApi()
}

internal actual fun createPexelsApi(apiKey: String?): PexelsApi {
    return apiKey?.takeIf { it.isNotBlank() }?.let(::PexelsApi) ?: PexelsApi()
}

internal actual fun createTmdbApi(apiToken: String?): TmdbApi {
    return apiToken?.takeIf { it.isNotBlank() }?.let(::TmdbApi) ?: TmdbApi()
}
