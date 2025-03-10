package com.alpha.showcase.common.utils

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.Serializable
import com.alpha.showcase.common.SUPABASE_URL
import com.alpha.showcase.common.SUPABASE_ANON_KEY
import io.github.jan.supabase.auth.Auth

object Supabase {

    val supabase = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
//        install(Auth)
        install(Postgrest)
    }

    val db get() = supabase.postgrest

    suspend fun test() {
        supabase.from("countries")
            .select().decodeList<Country>().forEach {
                Log.d("Country:", it.toString())
            }
    }

    suspend fun getValue(table: String, keyColumn: String, key: String, valueColumn: String): String? {
        return supabase.from(table).select {
            filter {
                eq(keyColumn, key)
            }
        }.decodeSingleOrNull<Map<String, String>>()?.get(valueColumn)
    }
}

@Serializable
data class Country(
    val id: Int? = null,
    val name: String,
)