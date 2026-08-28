package com.alpha.showcase.common.cache

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.AutoMigration
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.async.executeSQL
import com.alpha.showcase.common.cache.dao.CacheMetadataDao
import com.alpha.showcase.common.cache.dao.CachedItemDao
import com.alpha.showcase.common.cache.dao.GallerySourceMediaDao
import com.alpha.showcase.common.cache.entity.CacheMetadata
import com.alpha.showcase.common.cache.entity.CachedItem
import com.alpha.showcase.common.cache.entity.CACHED_ITEM_MEDIA_KIND_IMAGE
import com.alpha.showcase.common.cache.entity.CACHED_ITEM_MEDIA_KIND_VIDEO
import com.alpha.showcase.common.cache.entity.GallerySourceMedia
import com.alpha.showcase.common.utils.IMAGE_EXT_SUPPORT
import com.alpha.showcase.common.utils.VIDEO_EXT_SUPPORT
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        CachedItem::class,
        CacheMetadata::class,
        GallerySourceMedia::class,
    ],
    version = 6,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
    ]
)
@ConstructedBy(SourceCacheDatabaseConstructor::class)
abstract class SourceCacheDatabase : RoomDatabase() {

    abstract fun cachedItemDao(): CachedItemDao

    abstract fun cacheMetadataDao(): CacheMetadataDao

    abstract fun gallerySourceMediaDao(): GallerySourceMediaDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object SourceCacheDatabaseConstructor : RoomDatabaseConstructor<SourceCacheDatabase> {
    override fun initialize(): SourceCacheDatabase
}

expect fun getSourceCacheDatabaseBuilder(): RoomDatabase.Builder<SourceCacheDatabase>

private val sourceCacheMigration2To3 = object : Migration(2, 3) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.executeSQL(
            """
            ALTER TABLE cached_items
            ADD COLUMN media_kind INTEGER NOT NULL DEFAULT 0
            """.trimIndent()
        )
        connection.executeSQL(
            """
            CREATE INDEX IF NOT EXISTS index_cached_items_source_type_source_key_media_kind
            ON cached_items(source_type, source_key, media_kind)
            """.trimIndent()
        )
        connection.executeSQL(buildUpdateMediaKindSql(CACHED_ITEM_MEDIA_KIND_IMAGE, "image/", IMAGE_EXT_SUPPORT))
        connection.executeSQL(buildUpdateMediaKindSql(CACHED_ITEM_MEDIA_KIND_VIDEO, "video/", VIDEO_EXT_SUPPORT))
    }
}

private val sourceCacheMigration3To4 = object : Migration(3, 4) {
    override suspend fun migrate(connection: SQLiteConnection) {
        connection.executeSQL(
            """
            ALTER TABLE cache_metadata
            ADD COLUMN committed_sync_version INTEGER NOT NULL DEFAULT 0
            """.trimIndent()
        )
        // Aggregate versions ONCE per source into a temp table. Correlating a
        // MAX(sync_version) subquery directly against cached_items would re-scan
        // the source's rows for EVERY row updated (SQLite runs it as a CORRELATED
        // SCALAR SUBQUERY — near-quadratic on tens of thousands of rows, stalling
        // startup during the upgrade); against this tiny per-source table each
        // lookup is an indexed point read.
        connection.executeSQL(
            """
            CREATE TEMP TABLE _source_versions AS
            SELECT source_type, source_key,
                   MAX(sync_version) AS max_version,
                   COUNT(DISTINCT sync_version) AS version_count
            FROM cached_items
            GROUP BY source_type, source_key
            """.trimIndent()
        )
        connection.executeSQL(
            "CREATE INDEX _source_versions_idx ON _source_versions(source_type, source_key)"
        )
        // v3's unique index was (source_type, source_key, path), so an interrupted
        // v3 sync leaves a MIX of versions: some paths already replaced with the
        // new run's version, the rest still on the old one. Neither version alone
        // is complete — v3 always read ALL rows regardless of version — so first
        // collapse every source's rows onto one version. Paths are unique pre-v5,
        // so the collapse cannot violate the unique index; only sources that
        // actually hold mixed versions are touched (a cleanly-synced source — the
        // common case — updates zero rows).
        connection.executeSQL(
            """
            UPDATE cached_items
            SET sync_version = (
                SELECT sv.max_version FROM _source_versions sv
                WHERE sv.source_type = cached_items.source_type
                  AND sv.source_key = cached_items.source_key
            )
            WHERE EXISTS (
                SELECT 1 FROM _source_versions sv
                WHERE sv.source_type = cached_items.source_type
                  AND sv.source_key = cached_items.source_key
                  AND sv.version_count > 1
            )
            """.trimIndent()
        )
        // Backfill the committed version for already-cached sources. No sync can be
        // running during a migration and rows were just normalized to one version,
        // so MAX(sync_version) now identifies the FULL surviving row set. Without
        // this, an upgraded user's first stale refresh would read unpinned (mix of
        // old + new in-flight rows) — or, before the normalization above, pin to a
        // partial version left by an interrupted v3 sync.
        //
        // A source with NO rows but a VALID, total_items = 0 metadata is a
        // legitimately-empty committed cache (pre-v4 code had no committed marker
        // for it). It gets the sentinel version 1 — far below any real version
        // (epoch-millis based) — so hasDisplayableCache() stays true and the
        // offline "known empty" answer survives the upgrade instead of degrading
        // into an error. Strictly status = 'VALID': an UPDATING row without items
        // is an interrupted FIRST sync that never proved anything — it must stay
        // uncommitted and take the first-sync path after the upgrade.
        connection.executeSQL(
            """
            UPDATE cache_metadata
            SET committed_sync_version = COALESCE((
                SELECT sv.max_version FROM _source_versions sv
                WHERE sv.source_type = cache_metadata.source_type
                  AND sv.source_key = cache_metadata.source_key
            ), CASE WHEN total_items = 0 AND status = 'VALID' THEN 1 ELSE 0 END)
            """.trimIndent()
        )
        connection.executeSQL("DROP TABLE _source_versions")
    }
}

private val sourceCacheMigration4To5 = object : Migration(4, 5) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // Replace the (source_type, source_key, path) unique index with one that
        // also includes sync_version, so a re-sync's new-version rows no longer
        // REPLACE the old committed-version rows (both coexist until the sync ends).
        // Pre-v5 each path had exactly one row, so no duplicates exist to break the
        // new unique index.
        connection.executeSQL(
            "DROP INDEX IF EXISTS index_cached_items_source_type_source_key_path"
        )
        connection.executeSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS
            index_cached_items_source_type_source_key_path_sync_version
            ON cached_items(source_type, source_key, path, sync_version)
            """.trimIndent()
        )
    }
}

private val sourceCacheMigration5To6 = object : Migration(5, 6) {
    override suspend fun migrate(connection: SQLiteConnection) {
        // Historical v5 had two physical forms under the same identity hash:
        // fresh v5 databases created committed_sync_version without DEFAULT,
        // while databases upgraded from v3/v4 inherited DEFAULT 0 from ALTER
        // TABLE. Normalize both forms to the v6 entity definition. This table is
        // tiny (one row per source), and the migration transaction keeps the
        // replacement atomic.
        connection.executeSQL(
            """
            CREATE TABLE cache_metadata_v6 (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                source_type TEXT NOT NULL,
                source_key TEXT NOT NULL,
                last_updated INTEGER NOT NULL,
                next_update_time INTEGER NOT NULL,
                total_items INTEGER NOT NULL,
                update_strategy TEXT NOT NULL,
                is_recursive INTEGER NOT NULL,
                version TEXT,
                status TEXT NOT NULL,
                source_config_hash TEXT,
                committed_sync_version INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        connection.executeSQL(
            """
            INSERT INTO cache_metadata_v6 (
                id, source_type, source_key, last_updated, next_update_time,
                total_items, update_strategy, is_recursive, version, status,
                source_config_hash, committed_sync_version
            )
            SELECT id, source_type, source_key, last_updated, next_update_time,
                   total_items, update_strategy, is_recursive, version, status,
                   source_config_hash, committed_sync_version
            FROM cache_metadata
            """.trimIndent()
        )
        connection.executeSQL("DROP TABLE cache_metadata")
        connection.executeSQL("ALTER TABLE cache_metadata_v6 RENAME TO cache_metadata")
        connection.executeSQL(
            """
            CREATE UNIQUE INDEX IF NOT EXISTS index_cache_metadata_source_type_source_key
            ON cache_metadata(source_type, source_key)
            """.trimIndent()
        )
        connection.executeSQL(
            """
            CREATE INDEX IF NOT EXISTS index_cache_metadata_last_updated
            ON cache_metadata(last_updated)
            """.trimIndent()
        )
        connection.executeSQL(
            """
            CREATE INDEX IF NOT EXISTS index_cache_metadata_next_update_time
            ON cache_metadata(next_update_time)
            """.trimIndent()
        )

        // This filter index was added after v5 had already been opened. Adding it
        // under the unchanged v5 version changed Room's identity hash and made
        // those databases fail integrity verification, so it belongs in v6.
        connection.executeSQL(
            """
            CREATE INDEX IF NOT EXISTS
            index_cached_items_source_type_source_key_sync_version_media_kind
            ON cached_items(source_type, source_key, sync_version, media_kind)
            """.trimIndent()
        )
    }
}

private fun buildUpdateMediaKindSql(
    mediaKind: Int,
    mimePrefix: String,
    extensions: List<String>,
): String {
    val extensionMatches = extensions.joinToString(" OR ") { extension ->
        "LOWER(name) LIKE '%.${extension.lowercase()}'"
    }
    return """
        UPDATE cached_items
        SET media_kind = $mediaKind
        WHERE is_directory = 0
        AND (
            mime_type LIKE '$mimePrefix%'
            OR $extensionMatches
        )
    """.trimIndent()
}

// Exposed (internal) as one array so tests exercise EXACTLY the migration chain
// production registers below.
internal val sourceCacheMigrations: Array<Migration> = arrayOf(
    sourceCacheMigration2To3,
    sourceCacheMigration3To4,
    sourceCacheMigration4To5,
    sourceCacheMigration5To6,
)

internal object SourceCacheDatabaseProvider {
    val database: SourceCacheDatabase by lazy {
        getSourceCacheDatabaseBuilder()
            .addMigrations(*sourceCacheMigrations)
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
    }
}
