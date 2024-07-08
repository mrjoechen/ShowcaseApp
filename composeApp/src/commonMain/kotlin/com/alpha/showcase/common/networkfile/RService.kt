package com.alpha.showcase.common.networkfile

import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual


const val R_SERVICE_WORKER_ARG_PORT = "port"
const val R_SERVICE_WORKER_ARG_ALLOW_REMOTE_ACCESS = "allowRemoteAccess"
const val R_SERVICE_WORKER_ARG_USER = "user"
const val R_SERVICE_WORKER_ARG_PASSWD = "passwd"
const val R_SERVICE_WORKER_ARG_SERVE_PATH = "servePath"
const val R_SERVICE_WORKER_ARG_BASE_URL = "baseUrl"
const val R_SERVICE_WORKER_ARG_REMOTE = "remote"

const val R_SERVICE_ACCESS_BASE_URL = "access_url"

const val WAIT_FOR_SERVE_START = 2500L
const val LOCAL_ADDRESS = "http://localhost:"

interface RService{
    suspend fun startRService(inputData: Data, onProgress: (Data?) -> Unit)

    fun stopRService()
}



data class Data(val mValues: Map<String, Any> = emptyMap()) {
    companion object {
        fun dataOf(vararg pairs: Pair<String, Any>): Data = Data(pairs.toMap())
    }

    fun toJson(): String {
        val json = Json { serializersModule = DataSerializationModule }
        return json.encodeToString(serializer(), this)
    }

    inline fun <reified T> get(key: String, defaultValue: T): T {
        val value = mValues[key]
        return if (value is T) value else defaultValue
    }

    fun getBoolean(key: String, defaultValue: Boolean? = null): Boolean? =
        mValues[key] as? Boolean ?: defaultValue

    fun getInt(key: String, defaultValue: Int? = null): Int? =
        mValues[key] as? Int ?: defaultValue

    fun getFloat(key: String, defaultValue: Float? = null): Float? =
        mValues[key] as? Float ?: defaultValue

    fun getDouble(key: String, defaultValue: Double? = null): Double? =
        mValues[key] as? Double ?: defaultValue

    fun getLong(key: String, defaultValue: Long? = null): Long? =
        mValues[key] as? Long ?: defaultValue

    fun getString(key: String, defaultValue: String? = null): String? =
        mValues[key] as? String ?: defaultValue

    fun getIntArray(key: String, defaultValue: List<Any>? = null): List<Any>? =
        mValues[key] as? List<Int> ?: defaultValue

    fun getFloatArray(key: String, defaultValue: List<Float>? = null): List<Float>? =
        mValues[key] as? List<Float> ?: defaultValue

    fun getDoubleArray(key: String, defaultValue: List<Double>? = null): List<Double>? =
        mValues[key] as? List<Double> ?: defaultValue

    fun getLongArray(key: String, defaultValue: List<Long>? = null): List<Long>? =
        mValues[key] as? List<Long> ?: defaultValue

    fun getBooleanArray(key: String, defaultValue: List<Boolean>? = null): List<Boolean>? =
        mValues[key] as? List<Boolean> ?: defaultValue

    fun getStringArray(key: String, defaultValue: List<String>? = null): List<String>? =
        mValues[key] as? List<String> ?: defaultValue
}

// Serialization module for handling complex types
val DataSerializationModule = SerializersModule {
    // Add polymorphic serialization support for known types
    // Example for Int, you should add similar for other types as needed
    contextual(Int.serializer())
    contextual(Float.serializer())
    contextual(Double.serializer())
    contextual(Boolean.serializer())
    contextual(Long.serializer())
    contextual(String.serializer())
    contextual(ListSerializer(String.serializer()))
    contextual(ListSerializer(Int.serializer()))
    contextual(ListSerializer(Float.serializer()))
    contextual(ListSerializer(Double.serializer()))
    contextual(ListSerializer(Boolean.serializer()))
    contextual(ListSerializer(Long.serializer()))
}