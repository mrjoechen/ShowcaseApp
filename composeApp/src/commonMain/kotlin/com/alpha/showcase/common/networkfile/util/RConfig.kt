package com.alpha.showcase.common.networkfile.util

import TEST_IV
import TEST_KEY
import com.alpha.showcase.common.utils.decodePass
import com.alpha.showcase.common.utils.encodePass

object RConfig {

    var decrypt: ((String) -> String) = { it.decodePass(TEST_KEY, TEST_IV) }
        private set
    var encrypt: ((String) -> String) = { it.encodePass(TEST_KEY, TEST_IV) }
        private set

    fun initEnCryptAndDecrypt(encrypt: (String) -> String, decrypt: (String) -> String) {
        this.encrypt = encrypt
        this.decrypt = decrypt
    }

}