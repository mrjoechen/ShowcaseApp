package com.alpha.showcase.common.ui.source

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GallerySelectionFlowTest {
    @Test
    fun firstLimitedGrantUsesAuthorizationSelectionWithoutOpeningAnotherPicker() = runTest {
        val events = mutableListOf<String>()
        val selected = selectGalleryImages(
            GalleryReadAccess.NotDetermined,
            requestAccess = { events += "authorize"; GalleryReadAccess.Limited },
            authorizedImages = { events += "read"; listOf("a", "b") },
            pickImages = { error("A second selection page must not open") },
            pickLimitedImages = { error("Initial grant must not open the limited picker") },
        )
        assertEquals(listOf("a", "b"), selected)
        assertEquals(listOf("authorize", "read"), events)
    }

    @Test
    fun emptyInitialGrantDoesNotOpenAnotherPicker() = runTest {
        assertEquals(emptyList(), selectGalleryImages(
            GalleryReadAccess.NotDetermined,
            { GalleryReadAccess.Limited }, { emptyList() }, { error("Unexpected picker") }, { error("Unexpected limited picker") },
        ))
    }

    @Test
    fun firstFullGrantRequiresAnExplicitSelection() = runTest {
        assertEquals(listOf("chosen"), selectGalleryImages(
            GalleryReadAccess.NotDetermined,
            { GalleryReadAccess.Full }, { error("Must not import the entire library") }, { listOf("chosen") }, { error("Unexpected limited picker") },
        ))
    }

    @Test
    fun secondLimitedAddUsesOnlyTheLimitedPicker() = runTest {
        assertEquals(listOf("new"), selectGalleryImages(
            GalleryReadAccess.Limited, { error("Unexpected authorization") },
            { error("Must not import all previously authorized photos") },
            { error("PHPicker must not open under limited access") }, { listOf("new", "new") },
        ))
    }

    @Test
    fun existingFullAccessUsesRegularPicker() = runTest {
        assertEquals(listOf("chosen"), selectGalleryImages(
            GalleryReadAccess.Full, { error("Unexpected authorization") },
            { error("Must not import entire library") }, { listOf("chosen") },
            { error("Unexpected limited picker") },
        ))
    }

    @Test
    fun refreshingAuthorizationDoesNotSelectNewImages() {
        assertEquals(setOf("old"), retainAuthorizedGallerySelection(setOf("old"), listOf("old", "new")))
        assertEquals(emptySet(), retainAuthorizedGallerySelection(emptySet(), listOf("old", "new")))
        assertEquals(setOf("old"), retainAuthorizedGallerySelection(setOf("old", "revoked"), listOf("old", "new")))
    }

    @Test
    fun permissionSwitchRetriesWithLiveAccessAndReleasesPreviousSelection() = runTest {
        var access = GalleryReadAccess.Limited
        val routes = mutableListOf<String>()
        val result = retryGallerySelectionOnAccessChange {
            selectGalleryImages(access, { error("Unexpected request") }, { error("Unexpected import") },
                pickImages = { routes += "system"; listOf("chosen") },
                pickLimitedImages = {
                    routes += "limited"
                    access = GalleryReadAccess.Full
                    throw GalleryAccessChangedException()
                },
            )
        }
        assertEquals(listOf("limited", "system"), routes)
        assertEquals(listOf("chosen"), result)
    }

    @Test
    fun secondLaunchAndFullToLimitedSwitchUseCurrentPermission() = runTest {
        var access = GalleryReadAccess.Full
        val routes = mutableListOf<String>()
        suspend fun launchPicker() = retryGallerySelectionOnAccessChange {
            selectGalleryImages(access, { error("Unexpected request") }, { error("Unexpected import") },
                pickImages = {
                    routes += "system"
                    access = GalleryReadAccess.Limited
                    throw GalleryAccessChangedException()
                },
                pickLimitedImages = { routes += "limited"; listOf("chosen") },
            )
        }
        assertEquals(listOf("chosen"), launchPicker())
        assertEquals(listOf("chosen"), launchPicker())
        assertEquals(listOf("system", "limited", "limited"), routes)
    }

    @Test
    fun deniedAccessNeverOpensPicker() = runTest {
        for (initial in listOf(GalleryReadAccess.NotDetermined, GalleryReadAccess.Denied)) {
            assertFailsWith<GalleryPermissionDeniedException> {
                selectGalleryImages(initial, { GalleryReadAccess.Denied },
                    { error("Unexpected read") }, { error("Unexpected picker") }, { error("Unexpected limited picker") })
            }
        }
    }

    @Test
    fun cancellationDoesNotContinueToPicker() = runTest {
        assertFailsWith<CancellationException> {
            selectGalleryImages(GalleryReadAccess.NotDetermined,
                { throw CancellationException() }, { error("Unexpected read") }, { error("Unexpected picker") }, { error("Unexpected limited picker") })
        }
    }

    @Test
    fun partialOrRevokedAccessCannotSilentlyDropSelectedPhotos() {
        assertFailsWith<GallerySelectionAccessException> {
            requireAllGalleryImagesAccessible(listOf("a", "b"), setOf("a"))
        }
        assertFailsWith<GallerySelectionAccessException> {
            requireAllGalleryImagesAccessible(listOf("a"), emptySet())
        }
        requireAllGalleryImagesAccessible(listOf("b", "a"), setOf("a", "b", "other"))
    }
}
