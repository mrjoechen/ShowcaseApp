@file:OptIn(ExperimentalForeignApi::class)

package com.alpha.showcase.common.storage

import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.files.Path
import kotlinx.serialization.Serializable


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

@Suppress("UNUSED_PARAMETER")
actual inline fun <reified T : @Serializable Any> objectStoreOf(key: String, durability: StoreDurability): ObjectStore<T> {
	return IosObjectStore(storeOf(Path("$storageDir/${key}.json")))
}