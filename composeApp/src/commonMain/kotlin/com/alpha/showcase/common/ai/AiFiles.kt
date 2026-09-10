package com.alpha.showcase.common.ai

import coil3.Image

internal data class EncodedAiImage(val bytes: ByteArray, val mimeType: String = "image/jpeg")
internal data class AiOriginalImage(val bytes: ByteArray, val extension: String)

internal interface AiFiles {
    suspend fun write(name: String, bytes: ByteArray)
    suspend fun read(name: String): ByteArray
    suspend fun delete(name: String)
    fun imageModel(name: String): Any
}

internal expect fun createAiFiles(): AiFiles
internal expect suspend fun encodeAiImage(
    image: Image, maxBytes: Long = 12L * 1024 * 1024, maxEdge: Int = 1536,
): EncodedAiImage

internal expect suspend fun exportAiImage(name: String, bytes: ByteArray): Boolean
internal expect suspend fun shareAiImage(name: String, bytes: ByteArray)

/** Installed Android clients keep queued work alive with WorkManager. */
internal expect fun scheduleAiBackgroundWork()

internal expect fun createAiLibraryStore(): com.alpha.showcase.common.storage.ObjectStore<AiLibrary>
