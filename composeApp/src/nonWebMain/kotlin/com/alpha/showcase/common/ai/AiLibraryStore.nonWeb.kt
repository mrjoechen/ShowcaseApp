package com.alpha.showcase.common.ai

import com.alpha.showcase.common.storage.ObjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

/** AI metadata is durable application data, independent of evictable image/settings caches. */
internal class FileAiLibraryStore(
    private val fileSystem: FileSystem,
    private val path: Path,
    private val withFileLock: (() -> Unit) -> Unit = { it() },
) : ObjectStore<AiLibrary> {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var lastSnapshot: AiLibrary? = null

    override suspend fun get(): AiLibrary? = withContext(Dispatchers.Default) {
        processMutex.withLock { read().also { lastSnapshot = it } }
    }

    private fun read(): AiLibrary? = if (!fileSystem.exists(path)) null
        else fileSystem.read(path) { json.decodeFromString<AiLibrary>(readUtf8()) }

    override suspend fun set(value: AiLibrary) = withContext(Dispatchers.Default) {
        processMutex.withLock {
            path.parent?.let { fileSystem.createDirectories(it) }
            withFileLock {
                val stored = read() ?: AiLibrary()
                // A settings write from a stale instance must not revert a newer summary.
                val changed = value.summaries.filter { (key, content) ->
                    lastSnapshot?.summaries?.get(key) != content ||
                        lastSnapshot?.summaryRevisions?.get(key) != value.summaryRevisions[key]
                }
                val summaries = value.summaries + stored.summaries + changed
                val revisions = (value.summaryRevisions + stored.summaryRevisions) - changed.keys +
                    value.summaryRevisions.filterKeys { it in changed }
                val history = (stored.summaryHistory.keys + value.summaryHistory.keys + summaries.keys).mapNotNull { key ->
                    val previous = (stored.summaryHistory[key].orEmpty() + value.summaryHistory[key].orEmpty() +
                        listOfNotNull(stored.summaries[key], value.summaries[key]))
                        .filter { it != summaries[key] }.distinct()
                    if (previous.isEmpty()) null else key to previous
                }.toMap()
                val merged = value.copy(summaries = summaries, summaryHistory = history, summaryRevisions = revisions)
                val temporary = path.parent!!.resolve("${path.name}.tmp")
                try {
                    fileSystem.write(temporary) { writeUtf8(json.encodeToString(merged)) }
                    fileSystem.atomicMove(temporary, path)
                    lastSnapshot = merged
                } finally { fileSystem.delete(temporary, mustExist = false) }
            }
        }
    }

    override suspend fun delete(): Unit = error("AI summaries are durable user data and cannot be cleared as cache")

    private companion object {
        // Also prevents overlapping JVM file locks when multiple stores share a process.
        val processMutex = Mutex()
    }
}
