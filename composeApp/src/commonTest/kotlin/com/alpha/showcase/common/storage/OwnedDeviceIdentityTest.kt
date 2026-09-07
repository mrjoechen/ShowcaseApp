package com.alpha.showcase.common.storage

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class OwnedDeviceIdentityTest {
    private val original = "00000000-0000-4000-8000-000000000001"
    private val replacement = "00000000-0000-4000-8000-000000000002"

    @Test
    fun restoresExistingDurableIdWithoutGeneratingAnother() {
        val provider = DurableDeviceIdProvider({ original }, { error("No write needed") }, { replacement })
        assertEquals(original, provider.getOrCreate())
    }

    @Test
    fun newlyGeneratedIdentitySurvivesProviderRestart() {
        var saved: String? = null
        val first = DurableDeviceIdProvider({ saved }, { saved = it }, { original })
        assertEquals(original, first.getOrCreate())
        val restarted = DurableDeviceIdProvider({ saved }, { saved = it }, { replacement })
        assertEquals(original, restarted.getOrCreate())
    }

    @Test
    fun unavailableStorageStillKeepsOneIdentityForThisProcess() {
        var generated = 0
        val provider = DurableDeviceIdProvider(
            { error("storage unavailable") }, { error("storage unavailable") },
            { if (generated++ == 0) original else replacement },
        )
        assertEquals(original, provider.getOrCreate())
        assertEquals(original, provider.getOrCreate())
        assertEquals(1, generated)
    }

    @Test
    fun malformedStoredIdIsReplacedWithAValidRandomIdentity() {
        var saved: String? = "not-a-uuid"
        val provider = DurableDeviceIdProvider({ saved }, { saved = it }, { original })
        assertEquals(original, provider.getOrCreate())
        assertEquals(original, saved)
    }

    private inner class Fixture {
        var durableId: String? = original
        var binding: OwnedDeviceBinding? = null
        val requests = mutableListOf<String>()
        val provider = DurableDeviceIdProvider({ durableId }, { durableId = it }, { replacement })
        fun registrar(register: suspend (String) -> Unit = { requests += it }) = OwnedDeviceRegistrar(
            provider, { binding }, { binding = it }, register,
        )
    }

    @Test
    fun registrationBindsDurableDeviceToUidAndReusesThePair() = runTest {
        val fixture = Fixture()
        val registrar = fixture.registrar()
        assertEquals(original, registrar.ensureRegistered("uid-a"))
        assertEquals(original, registrar.ensureRegistered("uid-a"))
        assertEquals(listOf(original), fixture.requests)
        assertEquals(OwnedDeviceBinding(original, "uid-a"), fixture.binding)
    }

    @Test
    fun sameUidAfterRestartRevalidatesTheSameDeviceWithServer() = runTest {
        val fixture = Fixture()
        fixture.binding = OwnedDeviceBinding(original, "uid-a")
        assertEquals(original, fixture.registrar().ensureRegistered("uid-a"))
        assertEquals(listOf(original), fixture.requests)
    }

    @Test
    fun anotherUidGetsANewDeviceInsteadOfClaimingOldData() = runTest {
        val fixture = Fixture()
        fixture.binding = OwnedDeviceBinding(original, "uid-a")
        assertEquals(replacement, fixture.registrar().ensureRegistered("uid-b"))
        assertEquals(listOf(replacement), fixture.requests)
        assertEquals(OwnedDeviceBinding(replacement, "uid-b"), fixture.binding)
        assertEquals(replacement, fixture.durableId)
    }

    @Test
    fun ownershipConflictRotatesAndRetriesOnce() = runTest {
        val fixture = Fixture()
        val registrar = fixture.registrar {
            fixture.requests += it
            if (it == original) throw DeviceRegistrationConflictException()
        }
        assertEquals(replacement, registrar.ensureRegistered("uid-a"))
        assertEquals(listOf(original, replacement), fixture.requests)
        assertEquals(OwnedDeviceBinding(replacement, "uid-a"), fixture.binding)
    }

    @Test
    fun repeatedConflictDoesNotLoopOrMarkTheDeviceRegistered() = runTest {
        val fixture = Fixture()
        val registrar = fixture.registrar { fixture.requests += it; throw DeviceRegistrationConflictException() }
        assertFailsWith<DeviceRegistrationConflictException> { registrar.ensureRegistered("uid-a") }
        assertEquals(2, fixture.requests.size)
        assertNull(fixture.binding)
    }

    @Test
    fun networkFailureDoesNotRotateOrAuthorizeAStateWrite() = runTest {
        val fixture = Fixture()
        var fail = true
        val registrar = fixture.registrar {
            fixture.requests += it
            if (fail) error("offline")
        }
        assertFailsWith<IllegalStateException> { registrar.ensureRegistered("uid-a") }
        assertNull(fixture.binding)
        assertEquals(original, fixture.durableId)
        fail = false
        assertEquals(original, registrar.ensureRegistered("uid-a"))
        assertEquals(listOf(original, original), fixture.requests)
    }

    @Test
    fun concurrentWritesWaitForOneRegistration() = runTest {
        val fixture = Fixture()
        val registrar = fixture.registrar { fixture.requests += it; delay(10) }
        val ids = List(8) { async { registrar.ensureRegistered("uid-a") } }.awaitAll()
        assertEquals(List(8) { original }, ids)
        assertEquals(1, fixture.requests.size)
    }

    @Test
    fun cancellationDoesNotMarkRegistrationSuccessful() = runTest {
        val fixture = Fixture()
        val registrar = fixture.registrar { throw CancellationException() }
        assertFailsWith<CancellationException> { registrar.ensureRegistered("uid-a") }
        assertNull(fixture.binding)
    }
}
