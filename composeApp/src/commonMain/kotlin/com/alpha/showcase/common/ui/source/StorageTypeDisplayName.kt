package com.alpha.showcase.common.ui.source

import androidx.compose.runtime.Composable
import com.alpha.showcase.common.networkfile.storage.StorageType
import com.alpha.showcase.common.networkfile.storage.TYPE_LOCAL
import com.alpha.showcase.common.networkfile.storage.remote.TYPE_GALLERY
import org.jetbrains.compose.resources.stringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.source_type_gallery
import showcaseapp.composeapp.generated.resources.source_type_local

@Composable
fun StorageType.localizedDisplayName(): String = when (type) {
    TYPE_GALLERY -> stringResource(Res.string.source_type_gallery)
    TYPE_LOCAL -> stringResource(Res.string.source_type_local)
    else -> displayName
}
