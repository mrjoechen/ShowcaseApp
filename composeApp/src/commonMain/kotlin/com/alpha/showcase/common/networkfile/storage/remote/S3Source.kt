package com.alpha.showcase.common.networkfile.storage.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("S3")
data class S3Source(
    override val name: String,
    val endpoint: String,
    val accessKey: String,
    val secretKey: String,
    val bucket: String,
    val region: String = S3_DEFAULT_REGION,
    val prefix: String = "",
    val useSSL: Boolean = true,
) : RemoteApi

const val S3_DEFAULT_REGION = "us-east-1"
