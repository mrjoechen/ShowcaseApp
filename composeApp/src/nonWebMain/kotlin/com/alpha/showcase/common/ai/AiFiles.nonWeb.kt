package com.alpha.showcase.common.ai

import getPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath

internal actual fun createAiFiles(): AiFiles = object : AiFiles {
    private val root = getPlatform().getConfigDirectory().toPath().resolve("ai-images")
    private fun path(name: String) = root.resolve(name.also {
        require(it.matches(Regex("[a-zA-Z0-9-]+\\.(jpg|png|webp|gif|tiff|bmp|avif|heic|heif)")))
    })
    override suspend fun write(name: String, bytes: ByteArray) = withContext(Dispatchers.Default) {
        require(bytes.size <= 32 * 1024 * 1024)
        FileSystem.SYSTEM.createDirectories(root)
        val target = path(name)
        val temporary = root.resolve("$name.tmp")
        try {
            FileSystem.SYSTEM.write(temporary) { write(bytes) }
            FileSystem.SYSTEM.atomicMove(temporary, target)
        } finally { FileSystem.SYSTEM.delete(temporary, mustExist = false) }
    }
    override suspend fun read(name: String): ByteArray = withContext(Dispatchers.Default) {
        val target = path(name)
        require((FileSystem.SYSTEM.metadata(target).size ?: Long.MAX_VALUE) <= 32 * 1024 * 1024)
        FileSystem.SYSTEM.read(target) { readByteArray() }
    }
    override suspend fun delete(name: String) = withContext(Dispatchers.Default) {
        FileSystem.SYSTEM.delete(path(name), mustExist = false)
    }
    override fun imageModel(name: String): Any = path(name).toString()
}
