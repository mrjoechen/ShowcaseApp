package com.alpha.showcase.common.ui.config

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

class MTPhotoConfigPageLayoutTest {

    private fun sourceFile(): File = sequenceOf(
        File("composeApp/src/commonMain/kotlin/com/alpha/showcase/common/ui/config/MTPhotoConfigPage.kt"),
        File("src/commonMain/kotlin/com/alpha/showcase/common/ui/config/MTPhotoConfigPage.kt"),
    ).firstOrNull(File::exists) ?: error("MTPhotoConfigPage.kt not found")

    @Test
    fun mtPhotoFormFieldsUseTheDefaultConfigFieldWidth() {
        val form = sourceFile()
            .readText()
            .substringAfter("\n    Column(\n")
            .substringBefore("\n        Spacer(Modifier.height(8.dp))")

        assertFalse(
            form.contains("fillMaxWidth"),
            "MTPhoto form fields should use the same default width as other config pages",
        )
    }
}
