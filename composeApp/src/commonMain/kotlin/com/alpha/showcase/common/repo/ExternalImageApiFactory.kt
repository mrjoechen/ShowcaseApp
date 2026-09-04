package com.alpha.showcase.common.repo

import com.alpha.showcase.api.pexels.PexelsApi
import com.alpha.showcase.api.tmdb.TmdbApi
import com.alpha.showcase.api.unsplash.UnsplashApi

internal fun requireConfiguredProviderApiKey(provider: String, apiKey: String?): String {
    return apiKey?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("$provider API key is required")
}

internal expect val externalImageApiRequiresUserCredentials: Boolean

internal expect fun createUnsplashApi(apiKey: String? = null): UnsplashApi

internal expect fun createPexelsApi(apiKey: String? = null): PexelsApi

internal expect fun createTmdbApi(apiToken: String? = null): TmdbApi
