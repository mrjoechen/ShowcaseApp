package com.alpha.showcase.common.cache

import androidx.room3.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * REAL migration coverage: each test opens an actual old-schema database built
 * from the exported schema JSONs (composeApp/schemas), seeds legacy rows with raw
 * SQL, runs the production migration chain ([sourceCacheMigrations] + the 1->2
 * AutoMigration) and validates the historical v5 schema, the resulting v6
 * schema, and the retained data.
 * Complements [CachedItemVersionCoexistenceTest], which only exercises a
 * freshly-created database.
 */
class SourceCacheMigrationTest {

    private companion object {
        const val DB_FQN = "com.alpha.showcase.common.cache.SourceCacheDatabase"

        // The test JVM's working directory is the composeApp project dir under a
        // normal Gradle run; fall back to the repo-root-relative path for IDE runs.
        val schemaDirectory: String = listOf("schemas", "composeApp/schemas")
            .firstOrNull { File(it, DB_FQN).isDirectory }
            ?: error("Room schema directory not found from ${File(".").absolutePath}")
    }

    private fun newHelper(): MigrationTestHelper {
        val dbFile = File.createTempFile("source-cache-migration", ".db").also { it.delete() }
        return MigrationTestHelper(
            schemaDirectoryPath = File(schemaDirectory).toPath(),
            databasePath = dbFile.toPath(),
            driver = BundledSQLiteDriver(),
            databaseClass = SourceCacheDatabase::class,
        )
    }

    private fun SQLiteConnection.queryLong(sql: String): Long =
        prepare(sql).use { statement ->
            check(statement.step()) { "no row for: $sql" }
            statement.getLong(0)
        }

    private fun SQLiteConnection.queryString(sql: String): String =
        prepare(sql).use { statement ->
            check(statement.step()) { "no row for: $sql" }
            statement.getText(0)
        }

    private fun SQLiteConnection.insertItemV1V3V4(
        path: String,
        name: String,
        mimeType: String,
        syncVersion: Long,
        mediaKindColumn: Boolean,
    ) {
        // v1 lacks media_kind; v3/v4 share the v1 column set plus media_kind, which
        // legacy code populated per-row (the 2->3 migration backfills v1 data).
        val columns = buildString {
            append("source_type, source_key, parent_path, name, path, is_directory, size, mime_type, ")
            if (mediaKindColumn) append("media_kind, ")
            append("modified_time, created_at, last_accessed, is_recursive, metadata, sync_version")
        }
        val mediaKindValue = if (mediaKindColumn) "0, " else ""
        execSQL(
            """
            INSERT INTO cached_items ($columns)
            VALUES ('unsplash', 'k1', '/', '$name', '$path', 0, 1, '$mimeType', $mediaKindValue 0, 1, 1, 0, NULL, $syncVersion)
            """.trimIndent()
        )
    }

    private fun SQLiteConnection.insertMetadataPreV4(status: String, totalItems: Int) {
        execSQL(
            """
            INSERT INTO cache_metadata (source_type, source_key, last_updated, next_update_time,
                total_items, update_strategy, is_recursive, version, status, source_config_hash)
            VALUES ('unsplash', 'k1', 1, 2, $totalItems, 'STALE_WHILE_REVALIDATE', 0, NULL, '$status', 'hash')
            """.trimIndent()
        )
    }

    @Test
    fun migrateFrom1To5_preservesRowsBackfillsMediaKindAndCommittedVersion() = runTest {
        val helper = newHelper()
        helper.createDatabase(version = 1).apply {
            insertItemV1V3V4("https://img/a.jpg", "a.jpg", "image/jpeg", 100, mediaKindColumn = false)
            // No usable mime type: the 2->3 backfill must classify via extension.
            insertItemV1V3V4("https://img/b.png", "b.png", "", 100, mediaKindColumn = false)
            insertItemV1V3V4("https://img/notes.txt", "notes.txt", "text/plain", 100, mediaKindColumn = false)
            insertMetadataPreV4(status = "VALID", totalItems = 3)
            close()
        }

        val connection = helper.runMigrationsAndValidate(
            version = 5,
            migrations = sourceCacheMigrations.toList(),
        )
        connection.use {
            assertEquals(3, it.queryLong("SELECT COUNT(*) FROM cached_items"))
            assertEquals(
                2,
                it.queryLong("SELECT COUNT(*) FROM cached_items WHERE media_kind = 1"),
                "mime- and extension-based image rows must be backfilled as images",
            )
            assertEquals(
                0,
                it.queryLong("SELECT COUNT(*) FROM cached_items WHERE path LIKE '%.txt' AND media_kind != 0"),
            )
            assertEquals(
                100,
                it.queryLong("SELECT committed_sync_version FROM cache_metadata WHERE source_key = 'k1'"),
                "the existing cache's single version must be promoted to committed",
            )
        }
    }

    @Test
    fun migrateFrom2To5_backfillsMediaKindAndCommittedVersion() = runTest {
        val helper = newHelper()
        helper.createDatabase(version = 2).apply {
            insertItemV1V3V4("https://img/a.jpg", "a.jpg", "image/jpeg", 300, mediaKindColumn = false)
            // No usable mime type: the 2->3 backfill must classify via extension.
            insertItemV1V3V4("https://img/b.mp4", "b.mp4", "", 300, mediaKindColumn = false)
            insertMetadataPreV4(status = "VALID", totalItems = 2)
            close()
        }

        val connection = helper.runMigrationsAndValidate(
            version = 5,
            migrations = sourceCacheMigrations.toList(),
        )
        connection.use {
            assertEquals(2, it.queryLong("SELECT COUNT(*) FROM cached_items"))
            assertEquals(1, it.queryLong("SELECT COUNT(*) FROM cached_items WHERE media_kind = 1"))
            assertEquals(
                1,
                it.queryLong("SELECT COUNT(*) FROM cached_items WHERE media_kind = 2"),
                "extension-based video row must be backfilled as video",
            )
            assertEquals(
                300,
                it.queryLong("SELECT committed_sync_version FROM cache_metadata WHERE source_key = 'k1'"),
            )
        }
    }

    @Test
    fun migrateFrom3To5_interruptedSyncCollapsesToOneCompleteCommittedVersion() = runTest {
        // A v3 sync that died mid-run left MIXED versions: the (source,key,path)
        // unique index made new rows REPLACE old rows per path, so paths b/c carry
        // the aborted run's version 200 while a/d/e still carry 100. Neither
        // version alone is complete — pinning committed to MAX(200) without
        // normalization would surface only 2 of 5 files after upgrade.
        val helper = newHelper()
        helper.createDatabase(version = 3).apply {
            insertItemV1V3V4("https://img/a.jpg", "a.jpg", "image/jpeg", 100, mediaKindColumn = true)
            insertItemV1V3V4("https://img/b.jpg", "b.jpg", "image/jpeg", 200, mediaKindColumn = true)
            insertItemV1V3V4("https://img/c.jpg", "c.jpg", "image/jpeg", 200, mediaKindColumn = true)
            insertItemV1V3V4("https://img/d.jpg", "d.jpg", "image/jpeg", 100, mediaKindColumn = true)
            insertItemV1V3V4("https://img/e.jpg", "e.jpg", "image/jpeg", 100, mediaKindColumn = true)
            insertMetadataPreV4(status = "UPDATING", totalItems = 5)
            close()
        }

        val connection = helper.runMigrationsAndValidate(
            version = 5,
            migrations = sourceCacheMigrations.toList(),
        )
        connection.use {
            val committed = it.queryLong("SELECT committed_sync_version FROM cache_metadata WHERE source_key = 'k1'")
            assertEquals(200, committed)
            assertEquals(
                5,
                it.queryLong("SELECT COUNT(*) FROM cached_items WHERE sync_version = $committed"),
                "ALL surviving rows must be reachable under the committed version",
            )
            assertEquals(
                1,
                it.queryLong("SELECT COUNT(DISTINCT sync_version) FROM cached_items WHERE source_key = 'k1'"),
                "interrupted-sync version mix must collapse to a single version",
            )
        }
    }

    @Test
    fun migrateFrom3To5_legitimatelyEmptyCacheKeepsDisplayableCommitMarker() = runTest {
        val helper = newHelper()
        helper.createDatabase(version = 3).apply {
            // Metadata only: a source that synced successfully and found 0 items.
            insertMetadataPreV4(status = "VALID", totalItems = 0)
            close()
        }

        val connection = helper.runMigrationsAndValidate(
            version = 5,
            migrations = sourceCacheMigrations.toList(),
        )
        connection.use {
            assertEquals(
                1,
                it.queryLong("SELECT committed_sync_version FROM cache_metadata WHERE source_key = 'k1'"),
                "an empty-but-valid cache must keep a displayable committed marker " +
                    "(sentinel 1) so the offline 'known empty' answer survives the upgrade",
            )
        }
    }

    @Test
    fun migrateFrom3To5_interruptedFirstSyncWithoutRowsStaysUncommitted() = runTest {
        // UPDATING with no rows = a first sync that never finished. It proved
        // nothing, so it must NOT receive the empty-cache sentinel: post-upgrade
        // it stays non-displayable and takes the first-sync path.
        val helper = newHelper()
        helper.createDatabase(version = 3).apply {
            insertMetadataPreV4(status = "UPDATING", totalItems = 0)
            close()
        }

        val connection = helper.runMigrationsAndValidate(
            version = 5,
            migrations = sourceCacheMigrations.toList(),
        )
        connection.use {
            assertEquals(
                0,
                it.queryLong("SELECT committed_sync_version FROM cache_metadata WHERE source_key = 'k1'"),
            )
        }
    }

    @Test
    fun migrateFrom4To5_uniqueIndexAllowsVersionCoexistenceForSamePath() = runTest {
        val helper = newHelper()
        helper.createDatabase(version = 4).apply {
            insertItemV1V3V4("https://img/a.jpg", "a.jpg", "image/jpeg", 100, mediaKindColumn = true)
            execSQL(
                """
                INSERT INTO cache_metadata (source_type, source_key, last_updated, next_update_time,
                    total_items, update_strategy, is_recursive, version, status, source_config_hash,
                    committed_sync_version)
                VALUES ('unsplash', 'k1', 1, 2, 1, 'STALE_WHILE_REVALIDATE', 0, NULL, 'VALID', 'hash', 100)
                """.trimIndent()
            )
            close()
        }

        val connection = helper.runMigrationsAndValidate(
            version = 5,
            migrations = sourceCacheMigrations.toList(),
        )
        connection.use {
            // Under the v4 index (source,key,path) this would REPLACE/violate; the
            // v5 index includes sync_version, so a re-sync's row for the SAME path
            // must be insertable alongside the committed one.
            it.execSQL(
                """
                INSERT INTO cached_items (source_type, source_key, parent_path, name, path,
                    is_directory, size, mime_type, media_kind, modified_time, created_at,
                    last_accessed, is_recursive, metadata, sync_version)
                VALUES ('unsplash', 'k1', '/', 'a.jpg', 'https://img/a.jpg', 0, 1, 'image/jpeg', 1, 0, 1, 1, 0, NULL, 200)
                """.trimIndent()
            )
            assertEquals(2, it.queryLong("SELECT COUNT(*) FROM cached_items WHERE path = 'https://img/a.jpg'"))
            assertEquals(1, it.queryLong("SELECT COUNT(*) FROM cached_items WHERE sync_version = 100"))
            assertTrue(
                it.queryLong("SELECT committed_sync_version FROM cache_metadata WHERE source_key = 'k1'") == 100L,
                "4->5 must not disturb the committed version",
            )
        }
    }

    @Test
    fun migrateFrom5To6_restoresRoomIdentityWithoutLosingCacheRows() = runTest {
        val helper = newHelper()
        helper.createDatabase(version = 5).apply {
            insertItemV1V3V4("https://img/a.jpg", "a.jpg", "image/jpeg", 100, mediaKindColumn = true)
            execSQL(
                """
                INSERT INTO cache_metadata (source_type, source_key, last_updated, next_update_time,
                    total_items, update_strategy, is_recursive, version, status, source_config_hash,
                    committed_sync_version)
                VALUES ('unsplash', 'k1', 1, 2, 1, 'STALE_WHILE_REVALIDATE', 0, NULL, 'VALID', 'hash', 100)
                """.trimIndent()
            )
            assertEquals(
                1,
                queryLong(
                    """
                    SELECT COUNT(*) FROM pragma_table_info('cache_metadata')
                    WHERE name = 'committed_sync_version' AND dflt_value IS NULL
                    """.trimIndent()
                ),
                "the v5 fixture must represent the historical fresh-create schema",
            )
            assertEquals(
                "bff64184737c474804cfa9164ec50523",
                queryString("SELECT identity_hash FROM room_master_table WHERE id = 42"),
            )
            assertEquals(
                0,
                queryLong(
                    """
                    SELECT COUNT(*) FROM pragma_index_list('cached_items')
                    WHERE name = 'index_cached_items_source_type_source_key_sync_version_media_kind'
                    """.trimIndent()
                ),
            )
            close()
        }

        val connection = helper.runMigrationsAndValidate(
            version = 6,
            migrations = sourceCacheMigrations.toList(),
        )
        connection.use {
            assertEquals(6, it.queryLong("PRAGMA user_version"))
            assertEquals(
                "56d77b1234f84bfe59fc2c4bcaff46a6",
                it.queryString("SELECT identity_hash FROM room_master_table WHERE id = 42"),
            )
            assertEquals(1, it.queryLong("SELECT COUNT(*) FROM cached_items"))
            assertEquals(
                1,
                it.queryLong(
                    """
                    SELECT COUNT(*) FROM pragma_index_list('cached_items')
                    WHERE name = 'index_cached_items_source_type_source_key_sync_version_media_kind'
                    """.trimIndent()
                ),
                "the v5 database must gain the version/media filter index",
            )
            assertEquals(
                100,
                it.queryLong("SELECT committed_sync_version FROM cache_metadata WHERE source_key = 'k1'"),
                "the schema migration must preserve the committed cache generation",
            )
            assertEquals(
                1,
                it.queryLong(
                    """
                    SELECT COUNT(*) FROM pragma_table_info('cache_metadata')
                    WHERE name = 'committed_sync_version' AND dflt_value = '0'
                    """.trimIndent()
                ),
                "v6 must normalize the committed version default",
            )
        }
    }

    @Test
    fun migrateUpgradedV5To6_acceptsExistingCommittedVersionDefault() = runTest {
        val helper = newHelper()
        helper.createDatabase(version = 4).apply {
            insertItemV1V3V4("https://img/a.jpg", "a.jpg", "image/jpeg", 100, mediaKindColumn = true)
            execSQL(
                """
                INSERT INTO cache_metadata (source_type, source_key, last_updated, next_update_time,
                    total_items, update_strategy, is_recursive, version, status, source_config_hash,
                    committed_sync_version)
                VALUES ('unsplash', 'k1', 11, 22, 1, 'STALE_WHILE_REVALIDATE', 0, 'v1', 'VALID', 'hash', 100)
                """.trimIndent()
            )
            close()
        }

        helper.runMigrationsAndValidate(
            version = 5,
            migrations = sourceCacheMigrations.toList(),
        ).use {
            assertEquals(
                1,
                it.queryLong(
                    """
                    SELECT COUNT(*) FROM pragma_table_info('cache_metadata')
                    WHERE name = 'committed_sync_version' AND dflt_value = '0'
                    """.trimIndent()
                ),
                "a database upgraded from v4 carries DEFAULT 0 into its bff v5 schema",
            )
            assertEquals(
                "bff64184737c474804cfa9164ec50523",
                it.queryString("SELECT identity_hash FROM room_master_table WHERE id = 42"),
            )
        }

        helper.runMigrationsAndValidate(
            version = 6,
            migrations = sourceCacheMigrations.toList(),
        ).use {
            assertEquals(6, it.queryLong("PRAGMA user_version"))
            assertEquals(1, it.queryLong("SELECT COUNT(*) FROM cached_items"))
            assertEquals(1, it.queryLong("SELECT COUNT(*) FROM cache_metadata"))
            assertEquals(100, it.queryLong("SELECT committed_sync_version FROM cache_metadata"))
            assertEquals(22, it.queryLong("SELECT next_update_time FROM cache_metadata"))
            assertEquals("hash", it.queryString("SELECT source_config_hash FROM cache_metadata"))
        }
    }
}
