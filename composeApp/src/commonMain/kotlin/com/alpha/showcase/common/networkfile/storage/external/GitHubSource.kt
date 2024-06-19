package com.alpha.showcase.common.networkfile.storage.external

import com.alpha.showcase.common.networkfile.storage.remote.RemoteApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("GitHub")
open class GitHubSource(
    override val name: String,
    val repoUrl: String,
    val token: String,
    val path: String = "",
    val branchName: String? = null
) : RemoteApi<String>

fun GitHubSource.getOwnerAndRepo(): Pair<String, String>? {
    val regex = "https://github.com/(.*)/(.*)".toRegex()
    val matchResult = regex.find(repoUrl)

    if (matchResult != null) {
        val owner = matchResult.groupValues[1]
        val repo = matchResult.groupValues[2]
        return owner to repo
    }
    return null
}