package com.alpha.showcase.api.s3

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element

data class S3ObjectItem(
    val key: String,
    val size: Long,
    val lastModified: String,
    val etag: String?,
)

data class S3ListPage(
    val objects: List<S3ObjectItem>,
    val commonPrefixes: List<String>,
    val isTruncated: Boolean,
    val nextContinuationToken: String?,
)

object S3ListParser {
    fun parse(xml: String): S3ListPage {
        val document = Ksoup.parseXml(xml)
        val objects = document.getElementsByTag("Contents").mapNotNull { element ->
            val key = element.childText("Key") ?: return@mapNotNull null
            S3ObjectItem(
                key = key,
                size = element.childText("Size")?.toLongOrNull() ?: 0L,
                lastModified = element.childText("LastModified").orEmpty(),
                etag = element.childText("ETag")?.trim()?.removeSurrounding("\""),
            )
        }
        val prefixes = document.getElementsByTag("CommonPrefixes").mapNotNull { element ->
            element.childText("Prefix")
        }
        return S3ListPage(
            objects = objects,
            commonPrefixes = prefixes,
            isTruncated = document.getElementsByTag("IsTruncated").firstOrNull()
                ?.text()?.equals("true", ignoreCase = true) == true,
            nextContinuationToken = document.getElementsByTag("NextContinuationToken")
                .firstOrNull()?.text()?.takeIf { it.isNotBlank() },
        )
    }

    private fun Element.childText(name: String): String? = children()
        .firstOrNull { child -> child.tagName().substringAfter(':').equals(name, ignoreCase = true) }
        ?.text()
        ?.takeIf { it.isNotBlank() }
}
