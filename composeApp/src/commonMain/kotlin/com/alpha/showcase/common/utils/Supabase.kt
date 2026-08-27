package com.alpha.showcase.common.utils

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import com.alpha.showcase.common.SUPABASE_URL
import com.alpha.showcase.common.SUPABASE_ANON_KEY
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

object Supabase {

    private val clientMutex = Mutex()
    private var client: SupabaseClient? = null

    val isEnabled: Boolean
        get() = client != null

    val clientOrNull: SupabaseClient?
        get() = client

    suspend fun enable(): SupabaseClient? = clientMutex.withLock {
        client ?: if (SUPABASE_URL.isNotEmpty() && SUPABASE_ANON_KEY.isNotEmpty()) {
            createSupabaseClient(
                supabaseUrl = SUPABASE_URL,
                supabaseKey = SUPABASE_ANON_KEY
            ) {
                install(Auth) {
                    autoSaveToStorage = true
                    autoLoadFromStorage = true
                }
                install(Postgrest)
            }.also { client = it }
        } else {
            null
        }
    }

    suspend fun disable() {
        val clientToClose = clientMutex.withLock {
            client.also { client = null }
        }
        runCatching { clientToClose?.close() }
            .onFailure { Log.w("Supabase", "Failed to close client: ${it.message}") }
    }

    val db get() = client?.postgrest

    suspend fun test() {
        client ?: return
        withContext(Dispatchers.Default){
            try {
                val value = getValue("hello", "key", "hi", "value")?:""
                Log.d("Supabase", value)
            }catch (ex: Exception){
                ex.printStackTrace()
            }
        }

    }

    suspend fun getValue(table: String, keyColumn: String, key: String, valueColumn: String): String? {
        val currentClient = enable() ?: return null
        return currentClient.from(table).select {
            filter {
                eq(keyColumn, key)
            }
        }.decodeSingleOrNull<Map<String, String>>()?.get(valueColumn)
    }

    suspend inline fun <reified T : Any> insertValue(table: String, value: T) {
        val currentClient = clientOrNull ?: return
        currentClient.postgrest[table].insert(value)
    }

    suspend fun getConfigValue(key: String): String? {
        if (!SupabaseAuth.ensureAuthenticated()) return null
        return getValue(
            "config",
            "config_key",
            key,
            "config_value"
        )
    }
}
