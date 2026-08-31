package com.alpha.showcase.common.repo

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExternalImageApiKeyEditTest {

    @Test
    fun unchangedStoredValueIsLocked() {
        val edit = ExternalImageApiKeyEdit(
            existingStoredValue = "encrypted-token",
            changed = false,
        )

        assertTrue(edit.isLocked)
    }

    @Test
    fun changedValueIsNotLocked() {
        val edit = ExternalImageApiKeyEdit(
            input = "replacement-token",
            existingStoredValue = "encrypted-token",
            changed = true,
        )

        assertFalse(edit.isLocked)
    }

    @Test
    fun changedBlankInputIsMissing() {
        val edit = ExternalImageApiKeyEdit(input = "   ")

        assertTrue(edit.isMissing)
    }

    @Test
    fun changedInputIsTrimmedForRequests() = runTest {
        val edit = ExternalImageApiKeyEdit(input = "  new-token  ")

        val value = edit.valueForRequest {
            error("A new token must not be decrypted")
        }

        assertEquals("new-token", value)
    }

    @Test
    fun unchangedStoredValueIsDecryptedForRequests() = runTest {
        val edit = ExternalImageApiKeyEdit(
            existingStoredValue = "encrypted-token",
            changed = false,
        )

        val value = edit.valueForRequest { storedValue ->
            "decrypted:$storedValue"
        }

        assertEquals("decrypted:encrypted-token", value)
    }

    @Test
    fun changedBlankInputHasNoRequestValue() = runTest {
        val edit = ExternalImageApiKeyEdit(input = "   ")

        val value = edit.valueForRequest {
            error("A blank new token must not be decrypted")
        }

        assertNull(value)
    }

    @Test
    fun changedInputIsTrimmedBeforeEncryption() {
        val edit = ExternalImageApiKeyEdit(input = "  new-token  ")

        val value = edit.valueForStorage(enabled = true) { plainValue ->
            "encrypted:$plainValue"
        }

        assertEquals("encrypted:new-token", value)
    }

    @Test
    fun unchangedStoredValueIsPreservedWithoutEncryption() {
        val edit = ExternalImageApiKeyEdit(
            existingStoredValue = "encrypted-token",
            changed = false,
        )

        val value = edit.valueForStorage(enabled = true) {
            error("An unchanged stored token must not be encrypted again")
        }

        assertEquals("encrypted-token", value)
    }

    @Test
    fun changedBlankInputHasNoStorageValue() {
        val edit = ExternalImageApiKeyEdit(input = "   ")

        val value = edit.valueForStorage(enabled = true) {
            error("A blank token must not be encrypted")
        }

        assertNull(value)
    }

    @Test
    fun disabledStorageDoesNotEncryptInput() {
        val edit = ExternalImageApiKeyEdit(input = "new-token")

        val value = edit.valueForStorage(enabled = false) {
            error("A disabled token must not be encrypted")
        }

        assertNull(value)
    }

    @Test
    fun disabledStoragePreservesExistingStoredValueWithoutEncryption() {
        val edit = ExternalImageApiKeyEdit(
            input = "replacement-token",
            existingStoredValue = "encrypted-token",
            changed = true,
        )

        val value = edit.valueForStorage(enabled = false) {
            error("A disabled token must not be encrypted")
        }

        assertEquals("encrypted-token", value)
    }
}
