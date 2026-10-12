package com.alpha.showcase.common.storage

import kotlinx.serialization.Serializable

interface ObjectStore<T : @Serializable Any> {
	suspend fun set(value: T)
	suspend fun delete()
	suspend fun get(): T?
}

/** How much a stored value matters if the OS reclaims storage. */
enum class StoreDurability {
	/** User state that must survive relaunch. */
	Persistent,
	/** Rebuildable data; platforms with scarce durable storage (tvOS) keep it in purgeable storage. */
	Cache,
}

expect inline fun <reified T : @Serializable Any> objectStoreOf(
	key: String,
	durability: StoreDurability = StoreDurability.Persistent,
): ObjectStore<T>
