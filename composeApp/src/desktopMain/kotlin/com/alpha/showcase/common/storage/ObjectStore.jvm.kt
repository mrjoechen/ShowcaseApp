package com.alpha.showcase.common.storage

import com.alpha.showcase.common.versionName
import com.alpha.showcase.common.author
import io.github.xxfast.kstore.KStore
import io.github.xxfast.kstore.file.storeOf
import java.nio.file.NoSuchFileException
import kotlinx.io.files.Path
import kotlinx.serialization.Serializable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.harawata.appdirs.AppDirsFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap

val storageDir = AppDirsFactory.getInstance().getUserDataDir("Showcase", versionName, author)!!

private val storeMutexMap = ConcurrentHashMap<String, Mutex>()
private fun storeMutex(key: String): Mutex = storeMutexMap.getOrPut(key) { Mutex() }

class JvmObjectStore<T : @Serializable Any>(
	private val kstore: KStore<T>,
	private val key: String
) : ObjectStore<T> {

	init {
		if (!File(storageDir).exists()) {
			File(storageDir).mkdirs()
		}
	}

	override suspend fun set(value: T) {
		storeMutex(key).withLock {
			try {
				kstore.set(value)
			} catch (_: NoSuchFileException) {
				// Concurrent temp-file replacement can occasionally fail on desktop; retry once.
				kstore.set(value)
			}
		}
	}

	override suspend fun delete() {
		storeMutex(key).withLock {
			kstore.delete()
		}
	}

	override suspend fun get(): T? {
		return storeMutex(key).withLock {
			kstore.get()
		}
	}
}

actual inline fun <reified T : @Serializable Any> objectStoreOf(key: String): ObjectStore<T> {
	return JvmObjectStore(storeOf(Path("$storageDir/${key}.json")), key)
}
