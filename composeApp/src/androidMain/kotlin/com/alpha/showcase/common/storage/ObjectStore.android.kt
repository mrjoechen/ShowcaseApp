package com.alpha.showcase.common.storage

import AndroidApp
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import kotlinx.serialization.Serializable
import okio.Path.Companion.toPath


class AndroidObjectStore<T : @Serializable Any>(private val kstore: KStore<T>) : ObjectStore<T> {

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
	val storageDir = AndroidApp.cacheDir!!
	return AndroidObjectStore(storeOf("$storageDir/${key}.json".toPath()))
}