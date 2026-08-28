package com.alpha.showcase.common.ui.source

import com.alpha.showcase.common.networkfile.storage.LOCAL
import com.alpha.showcase.common.networkfile.storage.SMB
import com.alpha.showcase.common.networkfile.storage.remote.GALLERY
import com.alpha.showcase.common.networkfile.storage.remote.MTPHOTO
import com.alpha.showcase.common.networkfile.storage.remote.RSS
import com.alpha.showcase.common.networkfile.storage.remote.S3
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_MTPHOTO
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_RSS
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_S3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SourceSelectionRoutingTest {

    @Test
    fun mtPhotoSelectionProducesConfigurationNavigationType() {
        assertEquals(TYPE_MTPHOTO, configTypeForSourceSelection(MTPHOTO))
    }

    @Test
    fun s3AndRssSelectionsProduceConfigurationNavigationTypes() {
        assertEquals(TYPE_S3, configTypeForSourceSelection(S3))
        assertEquals(TYPE_RSS, configTypeForSourceSelection(RSS))
    }

    @Test
    fun existingConfigurableTypesStillNavigateWhileInlineTypesDoNot() {
        assertEquals(SMB.type, configTypeForSourceSelection(SMB))
        assertNull(configTypeForSourceSelection(LOCAL))
        assertNull(configTypeForSourceSelection(GALLERY))
    }
}
