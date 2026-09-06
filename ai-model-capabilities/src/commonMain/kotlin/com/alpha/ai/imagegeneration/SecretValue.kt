package com.alpha.ai.imagegeneration

/** A short-lived secret whose character contents are never exposed by diagnostics. */
class SecretValue : AutoCloseable {
    private var owned: CharArray

    constructor(value: String) {
        owned = value.toCharArray()
    }

    constructor(value: CharArray) {
        owned = value.copyOf()
    }

    fun <T> use(block: (CharArray) -> T): T {
        val temporary = owned.copyOf()
        return try {
            block(temporary)
        } finally {
            temporary.fill('\u0000')
        }
    }

    override fun close() {
        owned.fill('\u0000')
    }

    override fun toString(): String = "SecretValue(***)"
}
