@file:OptIn(ExperimentalForeignApi::class)

package com.alpha.showcase.common.storage

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask


val fileManager:NSFileManager = NSFileManager.defaultManager

internal fun userDirectory(directory: NSSearchPathDirectory): NSURL? = fileManager.URLForDirectory(
	directory = directory,
	appropriateForURL = null,
	create = true,
	inDomain = NSUserDomainMask,
	error = null
)

val cachesUrl: NSURL = userDirectory(NSCachesDirectory)
	?: NSURL.fileURLWithPath(NSTemporaryDirectory(), isDirectory = true)

/** Where app data lives; each platform decides what to do when Application Support is unusable. */
internal expect fun appDataDirectory(caches: NSURL): NSURL

val storageUrl: NSURL = appDataDirectory(cachesUrl)

val storageDir = storageUrl.path!!
val cacheDir = cachesUrl.path!!
