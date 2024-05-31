package com.alpha.showcase.common.storage

import io.ktor.serialization.kotlinx.json.DefaultJson
import kotlinx.browser.localStorage
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.w3c.dom.get
import org.w3c.dom.set

class WasmJsObjectStore<T : @Serializable Any>(
    private val key: String,
    private val serializer: KSerializer<T>
) : ObjectStore<T> {

    override suspend fun set(value: T) {
        localStorage[key] = Json.encodeToJsonElement(serializer, value).toString()
    }

    override suspend fun delete() {
        localStorage.removeItem(key)
    }

    override suspend fun get(): T? {
        val str = localStorage[key] ?: return null
        return Json.decodeFromString(serializer, str)
    }
}

actual inline fun <reified T : @Serializable Any> objectStoreOf(key: String): ObjectStore<T> {
    return WasmJsObjectStore(key, DefaultJson.serializersModule.serializer<T>())
}