@file:OptIn(ExperimentalForeignApi::class)

package com.alpha.showcase.common.storage

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSURL

/**
 * Application Support may be missing or read-only on tvOS. App data then falls back to a Caches
 * subdirectory instead of crashing; either way tvOS may reclaim it.
 */
internal actual fun appDataDirectory(caches: NSURL): NSURL {
	userDirectory(NSApplicationSupportDirectory)?.takeIf { it.writableDirectoryPath() != null }?.let { return it }
	val fallback = caches.URLByAppendingPathComponent("ApplicationSupport", isDirectory = true)!!
	checkNotNull(fallback.writableDirectoryPath()) { "No writable app data directory: ${fallback.path}" }
	return fallback
}

private fun NSURL.writableDirectoryPath(): String? {
	val path = path ?: return null
	if (!fileManager.fileExistsAtPath(path)) fileManager.createDirectoryAtPath(path, true, null, null)
	return path.takeIf { fileManager.isWritableFileAtPath(it) }
}
