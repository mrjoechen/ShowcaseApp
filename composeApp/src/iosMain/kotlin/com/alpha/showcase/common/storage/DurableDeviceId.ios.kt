@file:OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.alpha.showcase.common.storage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemUpdate
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

private const val SERVICE_NAME = "com.alpha.showcase.device"
private const val ACCOUNT_NAME = "durable_device_id"

actual fun getDurableDeviceId(): String? {
    return keychainRead(SERVICE_NAME, ACCOUNT_NAME)
}

actual fun saveDurableDeviceId(deviceId: String) {
    val existing = keychainRead(SERVICE_NAME, ACCOUNT_NAME)
    if (existing != null) {
        keychainUpdate(SERVICE_NAME, ACCOUNT_NAME, deviceId)
    } else {
        keychainWrite(SERVICE_NAME, ACCOUNT_NAME, deviceId)
    }
}

@Suppress("UNCHECKED_CAST")
private fun keychainRead(service: String, account: String): String? {
    return try {
        memScoped {
            val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 6, null, null)
            CFDictionaryAddValue(query, kSecClass as CFTypeRef?, kSecClassGenericPassword as CFTypeRef?)
            CFDictionaryAddValue(query, kSecAttrService as CFTypeRef?, CFBridgingRetain(service))
            CFDictionaryAddValue(query, kSecAttrAccount as CFTypeRef?, CFBridgingRetain(account))
            CFDictionaryAddValue(query, kSecReturnData as CFTypeRef?, kCFBooleanTrue as CFTypeRef?)
            CFDictionaryAddValue(query, kSecMatchLimit as CFTypeRef?, kSecMatchLimitOne as CFTypeRef?)

            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)

            if (status == errSecSuccess) {
                val data = CFBridgingRelease(result.value) as? NSData
                data?.let {
                    deviceIdFromKeychainData(it)
                }
            } else {
                null
            }
        }
    } catch (_: Exception) {
        null
    }
}

private fun keychainWrite(service: String, account: String, value: String) {
    try {
        val data = deviceIdToKeychainData(value)
        val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 5, null, null)
        CFDictionaryAddValue(query, kSecClass as CFTypeRef?, kSecClassGenericPassword as CFTypeRef?)
        CFDictionaryAddValue(query, kSecAttrService as CFTypeRef?, CFBridgingRetain(service))
        CFDictionaryAddValue(query, kSecAttrAccount as CFTypeRef?, CFBridgingRetain(account))
        CFDictionaryAddValue(query, kSecValueData as CFTypeRef?, CFBridgingRetain(data))
        CFDictionaryAddValue(query, kSecAttrAccessible as CFTypeRef?, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly as CFTypeRef?)

        SecItemAdd(query, null)
    } catch (_: Exception) {
        // Silently fail
    }
}

private fun keychainUpdate(service: String, account: String, value: String) {
    try {
        val data = deviceIdToKeychainData(value)
        val query = CFDictionaryCreateMutable(kCFAllocatorDefault, 3, null, null)
        CFDictionaryAddValue(query, kSecClass as CFTypeRef?, kSecClassGenericPassword as CFTypeRef?)
        CFDictionaryAddValue(query, kSecAttrService as CFTypeRef?, CFBridgingRetain(service))
        CFDictionaryAddValue(query, kSecAttrAccount as CFTypeRef?, CFBridgingRetain(account))

        val update = CFDictionaryCreateMutable(kCFAllocatorDefault, 1, null, null)
        CFDictionaryAddValue(update, kSecValueData as CFTypeRef?, CFBridgingRetain(data))

        val status = SecItemUpdate(query, update)
        if (status != errSecSuccess) {
            // If update fails, try delete + add
            keychainWrite(service, account, value)
        }
    } catch (_: Exception) {
        // Silently fail
    }
}

// Foundation bridges NSString/String at runtime; covered by the native round-trip regression.
@Suppress("CAST_NEVER_SUCCEEDS")
internal fun deviceIdToKeychainData(value: String): NSData =
    checkNotNull((value as NSString).dataUsingEncoding(NSUTF8StringEncoding))

@Suppress("CAST_NEVER_SUCCEEDS")
internal fun deviceIdFromKeychainData(data: NSData): String? =
    NSString.create(data = data, encoding = NSUTF8StringEncoding) as? String
