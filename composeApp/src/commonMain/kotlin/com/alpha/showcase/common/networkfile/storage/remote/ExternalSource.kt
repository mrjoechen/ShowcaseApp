package com.alpha.showcase.common.networkfile.storage.remote

import com.alpha.showcase.common.networkfile.storage.StorageType

const val TYPE_EXTERNAL = 100
const val TYPE_TMDB = 101
const val TYPE_GITHUB = 102
const val TYPE_UNSPLASH = 103
const val TYPE_GITEE = 104
const val TYPE_PEXELS = 105

sealed class ExternalSource(typeName: String = "UNKNOWN", type: Int = TYPE_EXTERNAL): StorageType(typeName, type)
data object TMDB: ExternalSource("TMDB", TYPE_TMDB)
data object GITHUB: ExternalSource("GitHub", TYPE_GITHUB)
data object UNSPLASH: ExternalSource("Unsplash", TYPE_UNSPLASH)
data object PEXELS: ExternalSource("Pexels", TYPE_PEXELS)
data object GITEE: ExternalSource("Gitee", TYPE_GITEE)

