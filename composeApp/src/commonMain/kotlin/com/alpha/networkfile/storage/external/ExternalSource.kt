package com.alpha.networkfile.storage.external

import com.alpha.networkfile.storage.StorageType

const val TYPE_EXTERNAL = 100
const val TYPE_TMDB = 101
const val TYPE_GITHUB = 102
const val TYPE_UNSPLASH = 103

sealed class ExternalSource(typeName: String = "UNKNOWN", type: Int = TYPE_EXTERNAL): StorageType(typeName, type)
data object TMDB: ExternalSource("TMDB", TYPE_TMDB)
data object GITHUB: ExternalSource("GitHub", TYPE_GITHUB)
data object UNSPLASH: ExternalSource("Unsplash", TYPE_UNSPLASH)
