package com.alpha.showcase.common.storage

import kotlinx.serialization.Serializable

interface ObjectStore<T : @Serializable Any> {
	suspend fun set(value: T)
	suspend fun delete()
	suspend fun get(): T?
}

expect inline fun <reified T : @Serializable Any> objectStoreOf(key: String): ObjectStore<T>