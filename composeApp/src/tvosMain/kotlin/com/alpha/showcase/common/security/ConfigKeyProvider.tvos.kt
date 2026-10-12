@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.alpha.showcase.common.security

import dev.whyoleg.cryptography.random.CryptographyRandom
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

/** The key must outlive purgeable tvOS files, just like the preferences it encrypts. */
actual object ConfigKeyProvider {
    actual fun getOrCreateKeyMaterial(): ByteArray {
        readKey()?.let { return it }
        val generated = CryptographyRandom.Default.nextBytes(CONFIG_KEY_SIZE_BYTES)
        val data = generated.usePinned {
            NSData.create(bytes = it.addressOf(0), length = generated.size.toULong())
        }
        val status = withKeyQuery { query ->
            val retainedData = CFBridgingRetain(data)
            try {
                CFDictionaryAddValue(query, kSecValueData, retainedData)
                CFDictionaryAddValue(query, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)
                SecItemAdd(query, null)
            } finally {
                CFRelease(retainedData)
            }
        }
        return when (status) {
            errSecSuccess -> generated
            errSecDuplicateItem -> checkNotNull(readKey()) { "Config encryption key disappeared from Keychain." }
            else -> error("Unable to save config encryption key to Keychain ($status).")
        }
    }

    private fun readKey(): ByteArray? = withKeyQuery { query ->
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        memScoped {
            val result = alloc<CFTypeRefVar>()
            result.value = null
            when (val status = SecItemCopyMatching(query, result.ptr)) {
                errSecItemNotFound -> null
                errSecSuccess -> {
                    val data = checkNotNull(CFBridgingRelease(result.value) as? NSData)
                    check(data.length == CONFIG_KEY_SIZE_BYTES.toULong()) { "Invalid config encryption key in Keychain." }
                    ByteArray(CONFIG_KEY_SIZE_BYTES).also { bytes ->
                        bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
                    }
                }
                else -> error("Unable to read config encryption key from Keychain ($status).")
            }
        }
    }

    private inline fun <T> withKeyQuery(block: (CFMutableDictionaryRef) -> T): T {
        // The dictionary borrows its values; keep the two bridged strings alive until it is released.
        val query = checkNotNull(CFDictionaryCreateMutable(kCFAllocatorDefault, 6, null, null))
        val service = CFBridgingRetain("com.alpha.showcase.config")
        val account = CFBridgingRetain("config_key_v2")
        try {
            CFDictionaryAddValue(query, kSecClass as CFTypeRef?, kSecClassGenericPassword as CFTypeRef?)
            CFDictionaryAddValue(query, kSecAttrService, service)
            CFDictionaryAddValue(query, kSecAttrAccount, account)
            return block(query)
        } finally {
            CFRelease(query)
            CFRelease(service)
            CFRelease(account)
        }
    }
}
