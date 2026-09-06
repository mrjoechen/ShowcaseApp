package com.alpha.showcase.common.ai

import com.alpha.showcase.common.storage.ObjectStore
import getPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

internal actual fun createAiLibraryStore(): ObjectStore<AiLibrary> = FileAiLibraryStore(
    FileSystem.SYSTEM, getPlatform().getConfigDirectory().toPath().resolve("ai-library-v1.json"),
)

/** AI metadata is durable application data, independent of evictable image/settings caches. */
internal class FileAiLibraryStore(private val fileSystem: FileSystem, private val path: Path) : ObjectStore<AiLibrary> {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    override suspend fun get(): AiLibrary? = withContext(Dispatchers.Default) {
        if (!fileSystem.exists(path)) null
        else fileSystem.read(path) { json.decodeFromString<AiLibrary>(readUtf8()) }
    }
    override suspend fun set(value: AiLibrary) = withContext(Dispatchers.Default) {
        path.parent?.let { fileSystem.createDirectories(it) }
        val temporary = path.parent!!.resolve("${path.name}.tmp")
        try {
            fileSystem.write(temporary) { writeUtf8(json.encodeToString(value)) }
            fileSystem.atomicMove(temporary, path)
        } finally { fileSystem.delete(temporary, mustExist = false) }
    }
    override suspend fun delete() = withContext(Dispatchers.Default) { fileSystem.delete(path, mustExist = false) }
}
