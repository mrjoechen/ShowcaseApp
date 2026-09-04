package com.alpha.showcase.common.repo

import com.alpha.showcase.api.pexels.PexelsApi
import com.alpha.showcase.api.tmdb.TmdbApi
import com.alpha.showcase.api.unsplash.UnsplashApi

internal actual val externalImageApiRequiresUserCredentials: Boolean = true

internal actual fun createUnsplashApi(apiKey: String?): UnsplashApi {
    return UnsplashApi(requireConfiguredProviderApiKey("Unsplash", apiKey))
}

internal actual fun createPexelsApi(apiKey: String?): PexelsApi {
    return PexelsApi(requireConfiguredProviderApiKey("Pexels", apiKey))
}

internal actual fun createTmdbApi(apiToken: String?): TmdbApi {
    return TmdbApi(requireConfiguredProviderApiKey("TMDB", apiToken))
}
