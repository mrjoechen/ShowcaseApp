package com.alpha.ai.imagegeneration

import com.alpha.ai.imagegeneration.internal.SensitiveDataRedactor
import kotlin.test.Test
import kotlin.test.assertFalse

class SensitiveDataRedactorTest {
    @Test
    fun `redactor removes bearer token and signed query`() {
        val input = "Authorization: Bearer secret https://x.test/file?X-Amz-Signature=abc"
        val output = SensitiveDataRedactor.redact(input, listOf("secret"))

        assertFalse(output.contains("secret"))
        assertFalse(output.contains("abc"))
    }

    @Test
    fun `redactor removes api key headers and base64 values`() {
        val input = "x-api-key: key-value body=QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo="
        val output = SensitiveDataRedactor.redact(input, listOf("key-value"))

        assertFalse(output.contains("key-value"))
        assertFalse(output.contains("QUJDREVGR0hJSktMTU5PUFFSU1RVVldYWVo="))
    }

    @Test
    fun `redactor removes basic authorization and non http signed query values`() {
        val input = "Authorization: Basic dXNlcjpwYXNz storage://images/output?X-Amz-Signature=abc"
        val output = SensitiveDataRedactor.redact(input, emptyList())

        assertFalse(output.contains("dXNlcjpwYXNz"))
        assertFalse(output.contains("abc"))
    }
}
