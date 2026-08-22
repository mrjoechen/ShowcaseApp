package com.alpha.showcase.common.networkfile.util

object RConfig {

    private fun notInitializedError(): Nothing {
        throw IllegalStateException("RConfig is not initialized. Call initializeConfigEncryption() during startup.")
    }

    var decryptBlocking: ((String) -> String) = { notInitializedError() }
        private set
    var encryptBlocking: ((String) -> String) = { notInitializedError() }
        private set

    private var decryptAsyncBlock: suspend (String) -> String = { notInitializedError() }
    private var encryptAsyncBlock: suspend (String) -> String = { notInitializedError() }

    fun initEnCryptAndDecrypt(
        encrypt: (String) -> String,
        decrypt: (String) -> String,
        encryptAsync: suspend (String) -> String,
        decryptAsync: suspend (String) -> String,
    ) {
        this.encryptBlocking = encrypt
        this.decryptBlocking = decrypt
        this.encryptAsyncBlock = encryptAsync
        this.decryptAsyncBlock = decryptAsync
    }

    suspend fun encryptAsync(value: String): String = encryptAsyncBlock(value)

    suspend fun decryptAsync(value: String): String = decryptAsyncBlock(value)

}
