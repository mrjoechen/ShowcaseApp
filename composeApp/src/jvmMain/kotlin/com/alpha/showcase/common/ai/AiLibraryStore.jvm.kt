package com.alpha.showcase.common.ai

import java.io.RandomAccessFile

internal fun withJvmAiLibraryFileLock(path: String, write: () -> Unit) {
    // The stable sibling lock remains valid when atomicMove replaces the data file.
    RandomAccessFile("$path.lock", "rw").use { lockFile ->
        lockFile.channel.lock().use { write() }
    }
}
