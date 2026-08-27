package com.alpha.showcase.common.networkfile.storage.remote

import com.alpha.showcase.common.networkfile.storage.StorageType

const val TYPE_EXTERNAL = 100
const val TYPE_TMDB = 101
const val TYPE_GITHUB = 102
const val TYPE_UNSPLASH = 103
const val TYPE_GITEE = 104
const val TYPE_PEXELS = 105
const val TYPE_IMMICH = 106
const val TYPE_WEIBO = 107
const val TYPE_ALBUM = 108
const val TYPE_GALLERY = 109
const val TYPE_S3 = 110
const val TYPE_RSS = 111
const val TYPE_MTPHOTO = 112

sealed class ExternalSource(
    typeName: String = "UNKNOWN",
    type: Int = TYPE_EXTERNAL,
    displayName: String = typeName,
) : StorageType(typeName, type, displayName)
data object TMDB: ExternalSource("TMDB", TYPE_TMDB)
data object GITHUB: ExternalSource("GitHub", TYPE_GITHUB)
data object UNSPLASH: ExternalSource("Unsplash", TYPE_UNSPLASH)
data object PEXELS: ExternalSource("Pexels", TYPE_PEXELS)
data object GITEE: ExternalSource("Gitee", TYPE_GITEE)
data object IMMICH: ExternalSource("Immich", TYPE_IMMICH)
data object WEIBO: ExternalSource("Weibo", TYPE_WEIBO)
data object ALBUM: ExternalSource("Music Album", TYPE_ALBUM)
data object GALLERY: ExternalSource("Gallery", TYPE_GALLERY)
data object S3 : ExternalSource(
    typeName = "Amazon S3",
    type = TYPE_S3,
    displayName = "S3 Compatible",
)
data object RSS: ExternalSource("RSS Feed", TYPE_RSS)
data object MTPHOTO : ExternalSource("MTPhoto", TYPE_MTPHOTO)
