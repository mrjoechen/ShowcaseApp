package com.alpha.showcase.common.ai

import coil3.Image

internal actual fun createAiFiles(): AiFiles = error("AI is unavailable in the browser")
internal actual suspend fun encodeAiImage(image: Image, maxBytes: Long): EncodedAiImage =
    error("AI is unavailable in the browser")

internal actual suspend fun exportAiImage(name: String, bytes: ByteArray): Boolean = error("AI is unavailable in the browser")

internal actual fun createAiLibraryStore(): com.alpha.showcase.common.storage.ObjectStore<AiLibrary> =
    error("AI is unavailable in the browser")
