package com.alpha.showcase.api

internal actual fun builtInPexelsApiKey(): String =
    error("Pexels API key must be supplied by the Web source configuration")

internal actual fun builtInUnsplashApiKey(): String =
    error("Unsplash API key must be supplied by the Web source configuration")

internal actual fun builtInTmdbApiToken(): String =
    error("TMDB API token must be supplied by the Web source configuration")
