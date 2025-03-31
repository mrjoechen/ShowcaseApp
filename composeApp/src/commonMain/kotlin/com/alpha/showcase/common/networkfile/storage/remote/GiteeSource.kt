package com.alpha.showcase.common.networkfile.storage.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("Gitee")
open class GiteeSource(
    override val name: String,
    val repoUrl: String,
    val token: String,
    val path: String = "",
    val branchName: String? = null
) : RemoteApi

fun GiteeSource.getOwnerAndRepo(): Pair<String, String>? {
    val regex = "https://gitee.com/(.*)/(.*)".toRegex()
    val matchResult = regex.find(repoUrl)

    if (matchResult != null) {
        val owner = matchResult.groupValues[1]
        val repo = matchResult.groupValues[2]
        return owner to repo
    }
    return null
}