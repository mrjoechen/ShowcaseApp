package com.alpha.showcase.common.ai

import okio.ByteString.Companion.encodeUtf8

internal fun testImageIdentity(label: String = "test-image"): ImageContentIdentity =
    ImageContentIdentity("sha256-file-v1:${label.encodeUtf8().sha256().hex()}", label.encodeUtf8().size.toLong())

/** Only the model-service seam is fake; database durability is tested against real SQLite. */
internal class TestSummaryRepository : SummaryRepository {
    val current = mutableMapOf<String, AiSummaryContent>()
    val versions = mutableListOf<AiSummaryContent>()
    override suspend fun find(identity: ImageContentIdentity) = current[identity.contentId]
    override suspend fun save(identity: ImageContentIdentity, content: AiSummaryContent, language: String) {
        versions += content
        current[identity.contentId] = content
    }
    override suspend fun migrate(identity: ImageContentIdentity, records: Map<String, List<AiSummaryContent>>, language: String) {
        if (identity.contentId !in current) records.values.lastOrNull()?.lastOrNull()?.let { current[identity.contentId] = it }
    }
}
