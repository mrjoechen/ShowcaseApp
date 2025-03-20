package com.alpha.showcase.common.networkfile.model

import kotlinx.serialization.Serializable

@Serializable
data class LocalFile(
    val path: String,
    val fileName: String,
    val isDirectory: Boolean,
    val size: Long,
    val mimeType: String,
    val modTime: String
)