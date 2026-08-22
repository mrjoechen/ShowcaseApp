package com.alpha.showcase.common.security

import com.alpha.showcase.common.networkfile.util.RConfig
import com.alpha.showcase.common.utils.decodePass
import com.alpha.showcase.common.utils.decodePassAsync
import com.alpha.showcase.common.utils.encodePass
import com.alpha.showcase.common.utils.encodePassAsync

const val CONFIG_KEY_SIZE_BYTES = 32

expect object ConfigKeyProvider {
    fun getOrCreateKeyMaterial(): ByteArray
}

fun initializeConfigEncryption() {
    val keyMaterial = ConfigKeyProvider.getOrCreateKeyMaterial()
    require(keyMaterial.size == CONFIG_KEY_SIZE_BYTES) {
        "Config encryption key must be $CONFIG_KEY_SIZE_BYTES bytes. Current size: ${keyMaterial.size}"
    }
    val stableKey = keyMaterial.copyOf()
    RConfig.initEnCryptAndDecrypt(
        encrypt = { value -> value.encodePass(stableKey) },
        decrypt = { value -> value.decodePass(stableKey) },
        encryptAsync = { value -> value.encodePassAsync(stableKey) },
        decryptAsync = { value -> value.decodePassAsync(stableKey) },
    )
}
