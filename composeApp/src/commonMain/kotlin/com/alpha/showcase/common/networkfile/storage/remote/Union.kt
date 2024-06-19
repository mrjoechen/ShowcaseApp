package com.alpha.showcase.common.networkfile.storage.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName("UNION")
class Union(
    override val name: String,
    override val path: String,
    val remotes: List<String>,
) : RcloneRemoteApi {

    override fun genRcloneOption(): List<String> {
        val options = ArrayList<String>()
        options.add(name)
        options.add("union")
        remotes.forEach {
            options.add("$it ")
        }
        return options
    }

    override fun genRcloneConfig(): Map<String, String> {
        val config = mutableMapOf<String, String>()
//    config["name"] = "union"
//    config["upstreams"] = "upstreama:test/dir upstreamb:"
        return config
    }

}