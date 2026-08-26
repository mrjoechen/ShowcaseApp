package com.alpha.showcase.common.ui.dialog

import com.alpha.showcase.common.networkfile.storage.FTP
import com.alpha.showcase.common.networkfile.storage.LOCAL
import com.alpha.showcase.common.networkfile.storage.SFTP
import com.alpha.showcase.common.networkfile.storage.SMB
import com.alpha.showcase.common.networkfile.storage.StorageType
import com.alpha.showcase.common.networkfile.storage.WEBDAV
import com.alpha.showcase.common.networkfile.storage.remote.ALBUM
import com.alpha.showcase.common.networkfile.storage.remote.GALLERY
import com.alpha.showcase.common.networkfile.storage.remote.GITEE
import com.alpha.showcase.common.networkfile.storage.remote.GITHUB
import com.alpha.showcase.common.networkfile.storage.remote.IMMICH
import com.alpha.showcase.common.networkfile.storage.remote.PEXELS
import com.alpha.showcase.common.networkfile.storage.remote.RSS
import com.alpha.showcase.common.networkfile.storage.remote.S3
import com.alpha.showcase.common.networkfile.storage.remote.TMDB
import com.alpha.showcase.common.networkfile.storage.remote.UNSPLASH
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import showcaseapp.composeapp.generated.resources.Res
import showcaseapp.composeapp.generated.resources.ic_amazon_s3
import showcaseapp.composeapp.generated.resources.ic_folder
import showcaseapp.composeapp.generated.resources.ic_ftp
import showcaseapp.composeapp.generated.resources.ic_gallery
import showcaseapp.composeapp.generated.resources.ic_gitee
import showcaseapp.composeapp.generated.resources.ic_github
import showcaseapp.composeapp.generated.resources.ic_immich
import showcaseapp.composeapp.generated.resources.ic_music_album
import showcaseapp.composeapp.generated.resources.ic_pexels
import showcaseapp.composeapp.generated.resources.ic_rss
import showcaseapp.composeapp.generated.resources.ic_smb
import showcaseapp.composeapp.generated.resources.ic_terminal
import showcaseapp.composeapp.generated.resources.ic_tmdb
import showcaseapp.composeapp.generated.resources.ic_unsplash_symbol
import showcaseapp.composeapp.generated.resources.ic_webdav
import showcaseapp.composeapp.generated.resources.source_type_category_album_services
import showcaseapp.composeapp.generated.resources.source_type_category_local
import showcaseapp.composeapp.generated.resources.source_type_category_network_storage
import showcaseapp.composeapp.generated.resources.source_type_category_other_online_content
import showcaseapp.composeapp.generated.resources.source_type_category_third_party

class SourceTypeSectionsTest {

    private val allSourceOptions = listOf(
        GALLERY to Res.drawable.ic_gallery,
        LOCAL to Res.drawable.ic_folder,
        SMB to Res.drawable.ic_smb,
        FTP to Res.drawable.ic_ftp,
        SFTP to Res.drawable.ic_terminal,
        WEBDAV to Res.drawable.ic_webdav,
        TMDB to Res.drawable.ic_tmdb,
        GITHUB to Res.drawable.ic_github,
        GITEE to Res.drawable.ic_gitee,
        UNSPLASH to Res.drawable.ic_unsplash_symbol,
        PEXELS to Res.drawable.ic_pexels,
        IMMICH to Res.drawable.ic_immich,
        ALBUM to Res.drawable.ic_music_album,
        S3 to Res.drawable.ic_amazon_s3,
        RSS to Res.drawable.ic_rss,
    )

    @Test
    fun availableSourcesUseApprovedCategoriesAndOrder() {
        val actual = sectionTypes(buildSourceTypeSections(allSourceOptions))

        assertEquals(
            listOf(
                Res.string.source_type_category_local to listOf(GALLERY, LOCAL),
                Res.string.source_type_category_network_storage to listOf(SMB, FTP, SFTP, WEBDAV, S3),
                Res.string.source_type_category_third_party to listOf(TMDB, UNSPLASH, PEXELS, GITHUB, GITEE),
                Res.string.source_type_category_album_services to listOf(IMMICH),
                Res.string.source_type_category_other_online_content to listOf(ALBUM, RSS),
            ),
            actual,
        )
    }

    @Test
    fun unavailableSourcesAndEmptyCategoriesAreOmitted() {
        val webSourceOptions = allSourceOptions.filter { (type) ->
            type in listOf(WEBDAV, TMDB, RSS)
        }

        assertEquals(
            listOf(
                Res.string.source_type_category_network_storage to listOf(WEBDAV),
                Res.string.source_type_category_third_party to listOf(TMDB),
                Res.string.source_type_category_other_online_content to listOf(RSS),
            ),
            sectionTypes(buildSourceTypeSections(webSourceOptions)),
        )
    }

    @Test
    fun everyAvailableSourceAppearsExactlyOnce() {
        val actualOptions = buildSourceTypeSections(allSourceOptions).flatMap(SourceTypeSection::options)

        assertEquals(allSourceOptions.size, actualOptions.size)
        assertEquals(
            allSourceOptions.map { it.first }.toSet(),
            actualOptions.map { it.first }.toSet(),
        )
    }

    @Test
    fun unmappedAvailableSourcesRemainVisibleInOtherOnlineContent() {
        val futureSource = StorageType(typeName = "Future source", type = 10_000)

        assertEquals(
            listOf(
                Res.string.source_type_category_other_online_content to listOf(futureSource),
            ),
            sectionTypes(
                buildSourceTypeSections(
                    listOf(futureSource to Res.drawable.ic_folder),
                ),
            ),
        )
    }

    private fun sectionTypes(
        sections: List<SourceTypeSection>,
    ): List<Pair<StringResource, List<StorageType>>> = sections.map { section ->
        section.titleRes to section.options.map { it.first }
    }
}
