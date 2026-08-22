@file:OptIn(ExperimentalStdlibApi::class)

package com.alpha.showcase.common.security

import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.browser.localStorage
import org.w3c.dom.get
import org.w3c.dom.set

actual object ConfigKeyProvider {
    private const val KEY = "showcase_config_key_v2"

    actual fun getOrCreateKeyMaterial(): ByteArray {
        localStorage[KEY]?.let { stored ->
            val decoded = try {
                stored.hexToByteArray()
            } catch (error: IllegalArgumentException) {
                throw IllegalStateException("Stored config encryption key is corrupted.", error)
            }
            check(decoded.size == CONFIG_KEY_SIZE_BYTES) {
                "Stored config encryption key has invalid size: ${decoded.size} bytes."
            }
            return decoded
        }

        return CryptographyRandom.Default.nextBytes(CONFIG_KEY_SIZE_BYTES).also { generated ->
            localStorage[KEY] = generated.toHexString()
        }
    }
}
