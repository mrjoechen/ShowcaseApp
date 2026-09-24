package com.alpha.showcase.common.ai

import androidx.room3.*
import kotlinx.coroutines.Dispatchers
import okio.BufferedSink
import okio.BufferedSource
import okio.ByteString.Companion.encodeUtf8
import kotlin.time.Clock
import kotlin.uuid.Uuid

// This database is durable user data. Never register it with SourceDerivedCache or use
// destructive migrations. There is intentionally no delete/eviction API for summaries.
@Entity(tableName = "summary_content")
internal data class SummaryContentRow(@PrimaryKey val contentId: String, val byteCount: Long?)

@Entity(tableName = "summary_revision", indices = [Index("contentId")])
internal data class SummaryRevisionRow(@PrimaryKey val revisionId: String, val contentId: String, val body: String)

@Entity(tableName = "summary_head")
internal data class SummaryHeadRow(@PrimaryKey val contentId: String, val revisionId: String)

@Entity(tableName = "summary_file_reference", primaryKeys = ["contentId", "referenceId"])
internal data class SummaryReferenceRow(val contentId: String, val referenceId: String, val body: String)

@Database(entities = [SummaryContentRow::class, SummaryRevisionRow::class, SummaryHeadRow::class, SummaryReferenceRow::class], version = 1, exportSchema = true)
@ConstructedBy(ImageSummaryDatabaseConstructor::class)
internal abstract class ImageSummaryDatabase : RoomDatabase() {
    abstract fun summaries(): ImageSummaryDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
internal expect object ImageSummaryDatabaseConstructor : RoomDatabaseConstructor<ImageSummaryDatabase> {
    override fun initialize(): ImageSummaryDatabase
}

internal expect fun imageSummaryDatabaseBuilder(): RoomDatabase.Builder<ImageSummaryDatabase>

internal object ImageSummaryDatabaseProvider {
    val database by lazy { imageSummaryDatabaseBuilder().setQueryCoroutineContext(Dispatchers.Default).build() }
}

internal data class SummaryImportResult(val added: Int, val alreadyPresent: Int, val conflicts: Int)

internal interface SummaryRepository {
    suspend fun find(identity: ImageContentIdentity): AiSummaryContent?
    suspend fun save(identity: ImageContentIdentity, content: AiSummaryContent, language: String)
    suspend fun migrate(identity: ImageContentIdentity, records: Map<String, List<AiSummaryContent>>, language: String)
    suspend fun rememberReference(reference: SummaryFileReference) {}
}

internal class DatabaseSummaryRepository(private val dao: () -> ImageSummaryDao = { ImageSummaryDatabaseProvider.database.summaries() }) : SummaryRepository {
    override suspend fun find(identity: ImageContentIdentity): AiSummaryContent? = dao().find(identity)
    override suspend fun save(identity: ImageContentIdentity, content: AiSummaryContent, language: String) = dao().save(identity, content, language)
    override suspend fun migrate(identity: ImageContentIdentity, records: Map<String, List<AiSummaryContent>>, language: String) = dao().migrate(identity, records, language)
    override suspend fun rememberReference(reference: SummaryFileReference) = dao().rememberReference(reference)
    suspend fun import(source: BufferedSource) = dao().importArchive(source)
    suspend fun export(sink: BufferedSink) = dao().exportArchive(sink)
    suspend fun count() = dao().countRevisions()
}

@Dao
internal abstract class ImageSummaryDao {
    @Query("SELECT * FROM summary_content WHERE contentId = :id")
    abstract suspend fun content(id: String): SummaryContentRow?
    @Query("SELECT * FROM summary_revision WHERE revisionId = :id")
    abstract suspend fun revision(id: String): SummaryRevisionRow?
    @Query("SELECT * FROM summary_head WHERE contentId = :id")
    abstract suspend fun head(id: String): SummaryHeadRow?
    @Query("SELECT COUNT(*) FROM summary_revision")
    abstract suspend fun countRevisions(): Long
    @Query("SELECT * FROM summary_revision WHERE revisionId > :after ORDER BY revisionId LIMIT 16")
    abstract suspend fun revisions(after: String): List<SummaryRevisionRow>
    @Query("SELECT * FROM summary_head WHERE contentId > :after ORDER BY contentId LIMIT 100")
    abstract suspend fun heads(after: String): List<SummaryHeadRow>
    @Query("SELECT * FROM summary_file_reference WHERE contentId > :content OR (contentId = :content AND referenceId > :reference) ORDER BY contentId, referenceId LIMIT 100")
    abstract suspend fun references(content: String, reference: String): List<SummaryReferenceRow>
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertContent(row: SummaryContentRow)
    @Query("UPDATE summary_content SET byteCount = :size WHERE contentId = :id AND byteCount IS NULL")
    abstract suspend fun completeLength(id: String, size: Long)
    @Insert(onConflict = OnConflictStrategy.ABORT)
    abstract suspend fun insertRevision(row: SummaryRevisionRow)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun select(row: SummaryHeadRow)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertReference(row: SummaryReferenceRow)

    private suspend fun register(id: String, size: Long?): Boolean {
        val old = content(id)
        if (old?.byteCount != null && size != null && old.byteCount != size) return false
        insertContent(SummaryContentRow(id, size))
        if (size != null) completeLength(id, size)
        return true
    }

    @Transaction
    open suspend fun rememberReference(reference: SummaryFileReference) {
        SummaryArchive.validate(reference)
        if (head(reference.contentId) != null) {
            insertReference(SummaryReferenceRow(reference.contentId, reference.referenceId, summaryJson.encodeToString(reference)))
        }
    }

    @Transaction
    open suspend fun find(identity: ImageContentIdentity): AiSummaryContent? {
        val known = content(identity.contentId) ?: return null
        require(known.byteCount == null || known.byteCount == identity.byteCount) { "Image identity length conflict" }
        return head(identity.contentId)?.let { revision(it.revisionId) }?.let {
            summaryJson.decodeFromString<SummaryRevision>(it.body).content()
        }
    }

    @Transaction
    open suspend fun save(identity: ImageContentIdentity, content: AiSummaryContent, language: String) {
        require(register(identity.contentId, identity.byteCount)) { "Image identity length conflict" }
        val value = SummaryRevision(Uuid.random().toString(), identity.contentId, identity.byteCount,
            summary = content.summary, narration = content.narration, tags = content.tags,
            outputLanguageTag = language, generatedAtEpochMillis = Clock.System.now().toEpochMilliseconds())
        SummaryArchive.validate(value)
        insertRevision(SummaryRevisionRow(value.revisionId, value.contentId, summaryJson.encodeToString(value)))
        select(SummaryHeadRow(value.contentId, value.revisionId))
    }

    /** Old opaque keys are promoted only after the matching displayed file is verified. */
    @Transaction
    open suspend fun migrate(identity: ImageContentIdentity, records: Map<String, List<AiSummaryContent>>, language: String) {
        if (records.isEmpty()) return
        require(register(identity.contentId, identity.byteCount))
        var candidate: String? = null
        records.forEach { (key, versions) ->
            versions.forEach { content ->
                val id = "legacy-" + (identity.contentId + key + summaryJson.encodeToString(content)).encodeUtf8().sha256().hex()
                if (revision(id) == null) {
                    val value = SummaryRevision(id, identity.contentId, identity.byteCount,
                        summary = content.summary, narration = content.narration, tags = content.tags,
                        outputLanguageTag = language, generatedAtEpochMillis = 0, sourceKind = "unspecified")
                    SummaryArchive.validate(value)
                    insertRevision(SummaryRevisionRow(id, identity.contentId, summaryJson.encodeToString(value)))
                }
                candidate = id
            }
        }
        if (head(identity.contentId) == null) candidate?.let { select(SummaryHeadRow(identity.contentId, it)) }
    }

    /** Parsing and writes share one transaction: truncation/checksum/cancellation rolls everything back. */
    @Transaction
    open suspend fun importArchive(source: BufferedSource): SummaryImportResult {
        var added = 0
        var present = 0
        var conflicts = 0
        val rejected = mutableSetOf<String>()
        val acceptedContent = mutableSetOf<String>()
        SummaryArchive.read(source, revision = { value ->
            val old = revision(value.revisionId)
            if ((old != null && summaryJson.decodeFromString<SummaryRevision>(old.body) != value) || !register(value.contentId, value.byteCount)) {
                conflicts++; rejected += value.revisionId
            } else {
                if (old == null) {
                    insertRevision(SummaryRevisionRow(value.revisionId, value.contentId, summaryJson.encodeToString(value)))
                    added++
                } else present++
                acceptedContent += value.contentId
            }
        }, selection = { value ->
            // Imports never override a choice already made on this device.
            if (value.revisionId !in rejected && head(value.contentId) == null) {
                select(SummaryHeadRow(value.contentId, value.revisionId))
            }
        }, reference = { value ->
            if (value.contentId in acceptedContent) insertReference(SummaryReferenceRow(value.contentId, value.referenceId, summaryJson.encodeToString(value)))
        })
        return SummaryImportResult(added, present, conflicts)
    }

    /** Consistent database snapshot; paging bounds memory even with years of retained history. */
    @Transaction
    open suspend fun exportArchive(sink: BufferedSink): SummaryArchiveCounts {
        val writer = SummaryArchive.Writer(sink)
        var after = ""
        while (true) {
            val page = revisions(after)
            if (page.isEmpty()) break
            page.forEach { writer.revision(summaryJson.decodeFromString(it.body)) }
            after = page.last().revisionId
        }
        after = ""
        while (true) {
            val page = heads(after)
            if (page.isEmpty()) break
            page.forEach { writer.selection(SummarySelection(it.contentId, revisionId = it.revisionId)) }
            after = page.last().contentId
        }
        after = ""
        var reference = ""
        while (true) {
            val page = references(after, reference)
            if (page.isEmpty()) break
            page.forEach { writer.reference(summaryJson.decodeFromString(it.body)) }
            after = page.last().contentId; reference = page.last().referenceId
        }
        return writer.finish()
    }
}
