package com.alpha.showcase.common.utils

import com.alpha.showcase.common.SUPABASE_ANON_KEY
import com.alpha.showcase.common.SUPABASE_URL
import com.alpha.showcase.common.storage.DeviceRegistrationConflictException
import com.alpha.showcase.common.storage.OwnedDeviceBinding
import com.alpha.showcase.common.storage.OwnedDeviceRegistrar
import com.alpha.showcase.common.storage.durableDeviceIds
import com.alpha.showcase.common.storage.objectStoreOf
import com.alpha.showcase.common.storage.requireDeviceId
import getPlatform
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.logging.LogLevel
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.time.Duration.Companion.seconds

private val PUBLIC_CONFIG_KEYS = setOf(
    "github_proxy",
    "music_api_baseurl",
    "online_track_interval",
)

internal fun requirePublicConfigKey(key: String): String {
    require(key in PUBLIC_CONFIG_KEYS) { "Config key is not public" }
    return key
}

private val CONFIG_KEY_PATTERN = Regex("[a-z0-9_]{1,64}")

internal fun requireCriticalConfigKey(key: String): String {
    require(key.matches(CONFIG_KEY_PATTERN)) { "Invalid critical config key" }
    return key
}

@Serializable
private data class ConfigValue(
    @SerialName("config_value")
    val value: String,
)

@Serializable
internal data class CriticalConfigRequest(
    val keys: List<String>,
)

@Serializable
internal data class CriticalConfigResponse(
    val values: Map<String, String?>,
    val missing: List<String> = emptyList(),
)

internal fun withRegisteredDeviceId(payload: JsonObject, deviceId: String): JsonObject {
    require("auth_user_id" !in payload && "user_id" !in payload) { "Owner must be derived by the server" }
    return JsonObject(payload + ("device_id" to JsonPrimitive(requireDeviceId(deviceId))))
}

@Serializable
private data class DeviceBindingRequest(
    @SerialName("p_device_id") val deviceId: String,
    @SerialName("p_build_type") val buildType: String,
)

/**
 * Deliberately excludes device/host names, carrier details, exact screen size, timezone and
 * platform build identifiers. This DTO is also the database write allowlist.
 */
@Serializable
internal data class SupabaseDeviceRegistration(
    @SerialName("p_device_id")
    val deviceId: String,
    @SerialName("p_model")
    val model: String,
    @SerialName("p_oem_name")
    val oemName: String,
    @SerialName("p_os_name")
    val osName: String,
    @SerialName("p_os_version")
    val osVersion: String,
    @SerialName("p_locale")
    val locale: String,
    @SerialName("p_app_version")
    val appVersion: String,
    @SerialName("p_app_namespace")
    val appNamespace: String,
    @SerialName("p_app_build")
    val appBuild: String,
    @SerialName("p_build_type")
    val buildType: String,
    @SerialName("p_os_api")
    val osApi: String,
    @SerialName("p_cpu_arch")
    val cpuArch: String?,
)

internal fun Device.toSupabaseRegistration(): SupabaseDeviceRegistration {
    require(buildType in setOf("debug", "beta", "release")) { "Invalid build type" }
    return SupabaseDeviceRegistration(
        deviceId = requireDeviceId(id),
        model = model.trim().take(256),
        oemName = oemName.trim().take(256),
        osName = osName.trim().take(128),
        osVersion = osVersion.trim().take(128),
        locale = locale
            .trim()
            .substringBefore('-')
            .substringBefore('_')
            .lowercase()
            .take(16),
        appVersion = appVersion.trim().take(64),
        appNamespace = appNameSpace.trim().take(256),
        appBuild = appBuild.trim().take(128),
        buildType = buildType,
        osApi = osApi.trim().take(64),
        cpuArch = cpuArch?.trim()?.take(64)?.takeIf(String::isNotEmpty),
    )
}

object Supabase {

    private val authenticatedClientMutex = Mutex()
    private var authenticatedClient: SupabaseClient? = null
    private val responseJson = Json { ignoreUnknownKeys = true }
    private val configCache = configurationCache()
    private val criticalConfigCache = configurationCache()
    private val deviceBindingStore by lazy { objectStoreOf<OwnedDeviceBinding>("owned_device_binding_v2") }
    private val deviceRegistrar = OwnedDeviceRegistrar(
        deviceIds = durableDeviceIds,
        readBinding = { deviceBindingStore.get() },
        saveBinding = { deviceBindingStore.set(it) },
        register = { deviceId ->
            val client = checkNotNull(authenticatedClientOrNull) { "Supabase client is unavailable" }
            try {
                client.postgrest.rpc("bind_device", DeviceBindingRequest(deviceId, getPlatform().getDevice().buildType))
            } catch (error: PostgrestRestException) {
                if (error.code == "23505") throw DeviceRegistrationConflictException()
                throw error
            }
        },
    )

    private fun configurationCache() = SupabaseConfigCache(
        allowStaleOnFailure = { error ->
            // A server-side access revocation must not keep serving an old credential.
            error !is RestException || error.statusCode !in setOf(401, 403)
        },
        onFailure = { Log.w("Supabase", "Configuration request failed") },
    )

    suspend fun warmUp() {
        enableAuthenticated()
    }

    suspend fun ensureAuthenticatedUserId(): String = SupabaseAuth.ensureAuthenticatedUserId()

    internal val authenticatedClientOrNull: SupabaseClient?
        get() = authenticatedClient

    /**
     * Clears plaintext browser sessions written by older app versions. On native platforms this is
     * a no-op; their platform session storage is managed by the Auth client.
     */
    fun clearLegacyBrowserSession() {
        clearLegacySupabaseBrowserSession(SUPABASE_URL)
    }

    internal suspend fun enableAuthenticated(): SupabaseClient? =
        authenticatedClientMutex.withLock {
            authenticatedClient ?: createClient()?.also {
                authenticatedClient = it
            }
        }

    internal suspend fun disableAuthenticated() {
        val clientToClose = authenticatedClientMutex.withLock {
            authenticatedClient.also { authenticatedClient = null }
        }
        runCatching { clientToClose?.close() }
            .onFailure { Log.w("Supabase", "Failed to close authenticated client") }
        configCache.clear()
        criticalConfigCache.clear()
    }

    private fun createClient(): SupabaseClient? {
        if (SUPABASE_URL.isBlank() || SUPABASE_ANON_KEY.isBlank()) return null
        return try {
            createSupabaseClient(
                supabaseUrl = SUPABASE_URL,
                supabaseKey = SUPABASE_ANON_KEY,
            ) {
                requestTimeout = 60.seconds
                // Avoid SDK logs that may contain authentication or request context in browser consoles.
                defaultLogLevel = LogLevel.WARNING
                install(Auth) {
                    autoSaveToStorage = true
                    autoLoadFromStorage = true
                    sessionManager = createSupabaseSessionManager(SUPABASE_URL)
                    // Anonymous authentication does not need a persistent OAuth PKCE verifier.
                    codeVerifierCache = MemoryCodeVerifierCache()
                }
                install(Postgrest) { requireValidSession = true }
                install(Functions) { requireValidSession = true }
            }
        } catch (error: Exception) {
            Log.w("Supabase", "Failed to initialize Supabase client")
            null
        }
    }

    suspend fun getConfigValue(key: String, forceRefresh: Boolean = false): String? {
        val publicKey = requirePublicConfigKey(key)
        val userId = authenticatedUserIdOrNull() ?: return null
        val client = authenticatedClientOrNull ?: return null
        return configCache.getValue(userId, publicKey, forceRefresh) {
            client.from("config").select(Columns.list("config_value")) {
                filter { eq("config_key", publicKey) }
            }.decodeSingleOrNull<ConfigValue>()?.value
        }
    }

    suspend fun getCriticalConfigValue(key: String, forceRefresh: Boolean = false): String? {
        val criticalKey = requireCriticalConfigKey(key)
        val userId = authenticatedUserIdOrNull() ?: return null
        val client = authenticatedClientOrNull ?: return null
        return criticalConfigCache.getValue(userId, criticalKey, forceRefresh) {
            val response = client.functions.invoke(
                function = "get-critical-config",
                body = CriticalConfigRequest(listOf(criticalKey)),
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                ),
            )
            check(response.status == HttpStatusCode.OK) { "Critical config request failed" }
            responseJson.decodeFromString<CriticalConfigResponse>(response.bodyAsText())
                .values[criticalKey]
        }
    }

    private suspend fun authenticatedUserIdOrNull(): String? = try {
        ensureAuthenticatedUserId()
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        null
    }

    internal suspend fun registerDevice(device: Device) {
        val userId = ensureAuthenticatedUserId()
        val deviceId = deviceRegistrar.ensureRegistered(userId)
        val client = checkNotNull(authenticatedClientOrNull) {
            "Authenticated Supabase client is unavailable"
        }
        client.postgrest.rpc("register_device", device.copy(id = deviceId).toSupabaseRegistration())
    }

    internal suspend fun insertFeedback(feedback: UserFeedback) {
        val userId = ensureAuthenticatedUserId()
        val deviceId = deviceRegistrar.ensureRegistered(userId)
        val client = checkNotNull(authenticatedClientOrNull) {
            "Authenticated Supabase client is unavailable"
        }
        client.from("user_feedbacks").insert(withRegisteredDeviceId(responseJson.encodeToJsonElement(feedback).jsonObject, deviceId))
    }

    internal suspend fun insertAnalyticsEvent(event: EventLog) {
        val userId = ensureAuthenticatedUserId()
        val deviceId = deviceRegistrar.ensureRegistered(userId)
        val client = checkNotNull(authenticatedClientOrNull) {
            "Authenticated Supabase client is unavailable"
        }
        client.from("analytics_events").insert(withRegisteredDeviceId(responseJson.encodeToJsonElement(event).jsonObject, deviceId))
    }
}
