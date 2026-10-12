package com.alpha.showcase.common.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSUserDefaults

private val storeJson = Json { ignoreUnknownKeys = true }

/**
 * NSUserDefaults is the only storage tvOS keeps until the app is deleted, and it is capped at
 * roughly 500 KB in total. Only small [StoreDurability.Persistent] user state belongs here.
 */
class TvosObjectStore<T : @Serializable Any>(
    private val key: String,
    private val serializer: KSerializer<T>,
) : ObjectStore<T> {
    private val defaults = NSUserDefaults.standardUserDefaults

    override suspend fun set(value: T) {
        val encoded = storeJson.encodeToString(serializer, value)
        val bytes = encoded.encodeToByteArray().size
        if (bytes > WARN_VALUE_BYTES) {
            println("TvosObjectStore: '$key' is $bytes bytes; NSUserDefaults on tvOS is limited to ~500 KB in total")
        }
        defaults.setObject(encoded, forKey = key)
    }

    override suspend fun get(): T? = defaults.stringForKey(key)?.let {
        storeJson.decodeFromString(serializer, it)
    }

    override suspend fun delete() = defaults.removeObjectForKey(key)

    private companion object {
        const val WARN_VALUE_BYTES = 128 * 1024
    }
}

/** [StoreDurability.Cache] values live in purgeable Caches files so they do not consume the defaults quota. */
class TvosCacheObjectStore<T : @Serializable Any>(
    private val path: Path,
    private val serializer: KSerializer<T>,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : ObjectStore<T> {
    private val mutex = Mutex()

    override suspend fun set(value: T) = io {
        path.parent?.let(fileSystem::createDirectories)
        val temporary = path.parent!!.resolve("${path.name}.tmp")
        try {
            fileSystem.write(temporary) { writeUtf8(storeJson.encodeToString(serializer, value)) }
            fileSystem.atomicMove(temporary, path)
        } finally {
            fileSystem.delete(temporary, mustExist = false)
        }
    }

    // A reclaimed or unreadable cache is equivalent to an empty one.
    override suspend fun get(): T? = io {
        runCatching {
            if (!fileSystem.exists(path)) null
            else storeJson.decodeFromString(serializer, fileSystem.read(path) { readUtf8() })
        }.getOrElse {
            fileSystem.delete(path, mustExist = false)
            null
        }
    }

    override suspend fun delete() = io { fileSystem.delete(path, mustExist = false) }

    private suspend fun <R> io(block: () -> R): R = withContext(Dispatchers.IO) { mutex.withLock { block() } }
}

actual inline fun <reified T : @Serializable Any> objectStoreOf(key: String, durability: StoreDurability): ObjectStore<T> =
    when (durability) {
        StoreDurability.Persistent -> TvosObjectStore("showcase.$key", serializer<T>())
        StoreDurability.Cache -> {
            // Earlier tvOS builds kept every value in NSUserDefaults; release that quota.
            NSUserDefaults.standardUserDefaults.removeObjectForKey("showcase.$key")
            TvosCacheObjectStore(cacheDir.toPath() / "object_store" / "$key.json", serializer<T>())
        }
    }
