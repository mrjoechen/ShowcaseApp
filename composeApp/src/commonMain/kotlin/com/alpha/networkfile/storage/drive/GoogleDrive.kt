package com.alpha.networkfile.storage.drive

import com.alpha.networkfile.model.NetworkFile
import com.alpha.networkfile.storage.remote.OAuthRcloneApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@SerialName("GoogleDrive")
open class GoogleDrive(
    override val name: String,
    override var token: String = "",
    val scope: String = "drive.readonly",
    val folderId: String = "",
    @Transient val cid: String = "",
    @Transient val sid: String = "",
    override val path: String = ""
) : OAuthRcloneApi {

    override fun genRcloneOption(): List<String> {
        val options = ArrayList<String>()
        options.add(name)
        options.add("drive")
        options.add("client_id")
        options.add(cid)
        options.add("client_secret")
        options.add(sid)
        options.add("token")
        options.add(token)
        options.add("root_folder_id")
        options.add(folderId)
        options.add("scope")
        options.add(scope)
        return options
    }

    override fun genRcloneConfig(): Map<String, String> {
        val config = mutableMapOf<String, String>()
        config["type"] = "drive"
        config["client_id"] = cid
        config["client_secret"] = sid
        config["token"] = token
        config["root_folder_id"] = folderId
        config["scope"] = scope
        return config
    }
}