package com.alpha.showcase.common.networkfile.util

object RConfig {

    private fun notInitializedError(): Nothing {
        throw IllegalStateException("RConfig is not initialized. Call initializeConfigEncryption() during startup.")
    }

    var decrypt: ((String) -> String) = { notInitializedError() }
        private set
    var encrypt: ((String) -> String) = { notInitializedError() }
        private set

    fun initEnCryptAndDecrypt(encrypt: (String) -> String, decrypt: (String) -> String) {
        this.encrypt = encrypt
        this.decrypt = decrypt
    }

}
