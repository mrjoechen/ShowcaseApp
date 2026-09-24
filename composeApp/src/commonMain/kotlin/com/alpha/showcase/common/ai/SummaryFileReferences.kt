package com.alpha.showcase.common.ai

import com.alpha.showcase.common.networkfile.model.NetworkFile
import com.alpha.showcase.common.networkfile.storage.remote.RemoteStorage
import com.alpha.showcase.common.ui.play.DataWithType
import com.alpha.showcase.common.ui.play.ResolvedImageModel
import com.alpha.showcase.common.ui.play.UrlWithAuth
import okio.ByteString.Companion.encodeUtf8

/** Descriptive provenance only. Imported paths never establish a verified local association. */
internal fun summaryFileReference(media: Any, identity: ImageContentIdentity): SummaryFileReference? {
    if (media is DataWithType) return summaryFileReference(media.data, identity)
    if (media is ResolvedImageModel) return summaryFileReference(media.stableKey, identity)
    val raw = when (media) { is NetworkFile -> media.path; is UrlWithAuth -> media.url; is String -> media; else -> return null }
    val isUrl = Regex("^[A-Za-z][A-Za-z0-9+.-]*://").containsMatchIn(raw)
    val path = if (isUrl) {
        val scheme = raw.substringBefore("://")
        val rest = raw.substringAfter("://").substringBefore('?').substringBefore('#')
        val authority = rest.substringBefore('/').substringAfterLast('@')
        "$scheme://$authority" + if ('/' in rest) "/${rest.substringAfter('/')}" else ""
    } else raw
    val protocol = (media as? NetworkFile)?.let { (it.remote as? RemoteStorage)?.schema?.trimEnd(':', '/') }
        ?: if (isUrl) raw.substringBefore("://") else "file"
    val name = (media as? NetworkFile)?.remote?.name.orEmpty()
    val fileName = (media as? NetworkFile)?.fileName ?: path.replace('\\', '/').substringAfterLast('/')
    val id = listOf(name, protocol, path, fileName).joinToString("\u0000").encodeUtf8().sha256().hex()
    val result = SummaryFileReference(identity.contentId, id, name, protocol, path, fileName)
    return runCatching { SummaryArchive.validate(result); result }.getOrNull()
}
