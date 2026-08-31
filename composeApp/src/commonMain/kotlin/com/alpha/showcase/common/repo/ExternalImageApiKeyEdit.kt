package com.alpha.showcase.common.repo

internal data class ExternalImageApiKeyEdit(
    val input: String = "",
    val existingStoredValue: String? = null,
    val changed: Boolean = true,
) {
    val isLocked: Boolean
        get() = !changed && !existingStoredValue.isNullOrBlank()

    val isMissing: Boolean
        get() = if (changed) input.isBlank() else existingStoredValue.isNullOrBlank()

    suspend fun valueForRequest(
        decrypt: suspend (String) -> String,
    ): String? {
        if (changed) {
            return input.trim().takeIf { it.isNotEmpty() }
        }
        return existingStoredValue
            ?.takeIf { it.isNotBlank() }
            ?.let { decrypt(it) }
    }

    fun valueForStorage(
        enabled: Boolean,
        encrypt: (String) -> String,
    ): String? {
        if (!enabled) return existingStoredValue?.takeIf { it.isNotBlank() }
        if (!changed) return existingStoredValue?.takeIf { it.isNotBlank() }
        return input.trim()
            .takeIf { it.isNotEmpty() }
            ?.let(encrypt)
    }
}
