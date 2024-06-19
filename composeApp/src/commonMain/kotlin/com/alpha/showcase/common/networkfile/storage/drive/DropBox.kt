package com.alpha.showcase.common.networkfile.storage.drive

import com.alpha.showcase.common.networkfile.storage.remote.OAuthRcloneApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@SerialName("DropBox")
open class DropBox(
    override val name: String,
    override var token: String = "",
    @Transient val cid: String = "",
    @Transient val sid: String = "",
    override val path: String = ""
) : OAuthRcloneApi {
    override fun genRcloneOption(): List<String> {
        val options = ArrayList<String>()
        options.add(name)
        options.add("dropbox")
        options.add("client_id")
        options.add(cid)
        options.add("client_secret")
        options.add(sid)
        return options
    }

    override fun genRcloneConfig(): Map<String, String> {
        val config = mutableMapOf<String, String>()
        config["type"] = "dropbox"
        config["client_id"] = cid
        config["client_secret"] = sid
        config["token"] = token
        return config
    }
}