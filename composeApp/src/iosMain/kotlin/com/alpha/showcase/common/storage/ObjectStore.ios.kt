@file:OptIn(ExperimentalForeignApi::class)

package com.alpha.showcase.common.storage

import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.files.Path
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer


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

/**
 * One store per key for the whole process. KStore caches the last value it read or wrote, so separate
 * instances over the same file would keep serving their own copy after another instance changed it.
 */
@PublishedApi
internal object IosObjectStores {
	private val lock = SynchronizedObject()
	private val stores = mutableMapOf<String, Pair<String, IosObjectStore<*>>>()

	fun <T : @Serializable Any> getOrCreate(key: String, typeName: String, create: () -> IosObjectStore<T>): IosObjectStore<T> =
		synchronized(lock) {
			val (storedType, store) = stores.getOrPut(key) { typeName to create() }
			check(storedType == typeName) { "Object store '$key' holds $storedType, not $typeName" }
			@Suppress("UNCHECKED_CAST")
			store as IosObjectStore<T>
		}
}

@Suppress("UNUSED_PARAMETER")
actual inline fun <reified T : @Serializable Any> objectStoreOf(key: String, durability: StoreDurability): ObjectStore<T> =
	IosObjectStores.getOrCreate(key, serializer<T>().descriptor.serialName) {
		IosObjectStore(storeOf(Path("$storageDir/${key}.json")))
	}