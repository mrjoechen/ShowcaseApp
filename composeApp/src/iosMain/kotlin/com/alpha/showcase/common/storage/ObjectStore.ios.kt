@file:OptIn(ExperimentalForeignApi::class)

package com.alpha.showcase.common.storage

import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.serialization.Serializable
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

val storageDir = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first().toString()
class IosObjectStore<T : @Serializable Any>(private val kstore: KStore<T>) : ObjectStore<T> {
	init {
		val fileManager = NSFileManager.defaultManager()
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
	return IosObjectStore(storeOf("$storageDir/${key}.json".toPath()))
}