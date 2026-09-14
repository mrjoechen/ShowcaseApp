package com.alpha.showcase.common

import coil3.key.Keyer
import coil3.request.Options
import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.RemoteStorage
import okio.ByteString.Companion.encodeUtf8

internal class NetworkFileKeyer : Keyer<NetworkFile> {
    override fun key(data: NetworkFile, options: Options): String = networkImageCacheKey(data)
}

internal fun networkImageCacheKey(file: NetworkFile): String {
    val remote = file.remote as? RemoteStorage
    // Editing a source keeps its ID: also scope bytes to the endpoint and credentials.
    val identity = listOf(remote?.id, remote?.schema, remote?.host, remote?.port,
        remote?.user, remote?.passwd, file.remote.name, file.path, file.modTime, file.size)
        .joinToString("\u0000").encodeUtf8().sha256().hex()
    return "network-image-v2:$identity"
}
