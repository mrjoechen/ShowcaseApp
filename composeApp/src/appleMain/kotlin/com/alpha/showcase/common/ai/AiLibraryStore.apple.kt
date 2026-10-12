package com.alpha.showcase.common.ai

import com.alpha.showcase.common.storage.ObjectStore
import getPlatform
import okio.FileSystem
import okio.Path.Companion.toPath

internal actual fun createAiLibraryStore(): ObjectStore<AiLibrary> = FileAiLibraryStore(
    FileSystem.SYSTEM, getPlatform().getConfigDirectory().toPath().resolve("ai-library-v1.json"),
)
