@file:OptIn(ExperimentalForeignApi::class)

package com.alpha.showcase.common.storage

import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.files.Path
import kotlinx.serialization.Serializable
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask


val fileManager:NSFileManager = NSFileManager.defaultManager

val cachesUrl: NSURL = fileManager.URLForDirectory(
	directory = NSCachesDirectory,
	appropriateForURL = null,
	create = true,
	inDomain = NSUserDomainMask,
	error = null
)!!

val storageUrl: NSURL = fileManager.URLForDirectory(
	directory = NSApplicationSupportDirectory,
	appropriateForURL = null,
	create = true,
	inDomain = NSUserDomainMask,
	error = null
)!!

val storageDir = storageUrl.path!!
val cacheDir = cachesUrl.path!!

class IosObjectStore<T : @Serializable Any>(private val kstore: KStore<T>) : ObjectStore<T> {
	init {
		if (!fileManager.fileExistsAtPath(storageDir)) {
			fileManager.createDirectoryAtPath(storageDir, true, null, null)
		}
	}
	override suspend fun set(value: T) {
		kstore.set(value)
	}

	override suspend fun delete() {
		kstore.delete()
	}

	override suspend fun get(): T? {
		return kstore.get()
	}
}

actual inline fun <reified T : @Serializable Any> objectStoreOf(key: String): ObjectStore<T> {
	return IosObjectStore(storeOf(Path("$storageDir/${key}.json")))
}