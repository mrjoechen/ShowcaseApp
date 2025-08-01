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
import kotlinx.coroutines.withContext

object Supabase {

    var supabase: SupabaseClient? = null

    init {
        if (SUPABASE_URL.isNotEmpty() && SUPABASE_ANON_KEY.isNotEmpty()){
            supabase = createSupabaseClient(
                supabaseUrl = SUPABASE_URL,
                supabaseKey = SUPABASE_ANON_KEY
            ) {
//        install(Auth)
                install(Postgrest)
            }
        }
    }

    val db get() = supabase?.postgrest

    suspend fun test() {
        supabase?: return
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
        supabase?: return null
        return supabase!!.from(table).select {
            filter {
                eq(keyColumn, key)
            }
        }.decodeSingleOrNull<Map<String, String>>()?.get(valueColumn)
    }

    suspend inline fun <reified T : Any> insertValue(table: String, value: T) {
        supabase?: return
        supabase!!.postgrest[table].insert(value)
    }

    suspend fun getConfigValue(key: String): String? {
        return getValue(
            "config",
            "config_key",
            key,
            "config_value"
        )
    }
}