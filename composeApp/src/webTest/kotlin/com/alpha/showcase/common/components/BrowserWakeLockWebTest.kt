@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.alpha.showcase.common.components

import getScreenFeature
import kotlinx.browser.window
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.js.JsAny
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowserWakeLockWebTest {
    private val features = mutableListOf<ScreenFeature>()

    @BeforeTest
    fun setUp() {
        installWakeLockBrowser()
    }

    @AfterTest
    fun tearDown() {
        try {
            features.forEach { it.keepScreenOn(false) }
        } finally {
            features.clear()
            restoreWakeLockBrowser()
        }
    }

    @Test
    fun fixtureStateChangesReachTheBrowserSynchronously() {
        assertEquals("visible:false", wakeLockBrowserState())

        setBrowserVisible(false)
        setBrowserFullscreen(true)
        assertEquals("hidden:true", wakeLockBrowserState())

        setBrowserVisible(true)
        setBrowserFullscreen(false)
        assertEquals("visible:false", wakeLockBrowserState())
    }

    @Test
    fun onlyVisibleFullscreenPlaybackRequestsAScreenLock() = runTest {
        val screen = screenFeature()
        screen.keepScreenOn(true)
        assertEquals(0, wakeLockRequestCount())

        setBrowserVisible(false)
        setBrowserFullscreen(true)
        assertEquals(0, wakeLockRequestCount())

        setBrowserVisible(true)
        assertEquals(1, wakeLockRequestCount())
        assertEquals("screen", wakeLockRequestType(0))
        resolveWakeLockRequest(0)
        flushBrowserPromises()
    }

    @Test
    fun repeatedEnablingDoesNotDuplicatePendingOrHeldLocks() = runTest {
        setBrowserFullscreen(true)
        val screen = screenFeature()
        screen.keepScreenOn(true)
        screen.keepScreenOn(true)
        assertEquals(1, wakeLockRequestCount())

        resolveWakeLockRequest(0)
        flushBrowserPromises()
        screen.keepScreenOn(true)
        assertEquals(1, wakeLockRequestCount())
        assertEquals(0, wakeLockReleaseCount(0))
    }

    @Test
    fun disablingReleasesTheHeldLockOnlyOnce() = runTest {
        val screen = enableFullscreenPlayback()
        resolveWakeLockRequest(0)
        flushBrowserPromises()

        screen.keepScreenOn(false)
        screen.keepScreenOn(false)
        flushBrowserPromises()

        assertEquals(1, wakeLockReleaseCount(0))
        assertEquals(0, wakeLockBrowserListenerCount())
    }

    @Test
    fun hidingTheTabReleasesAndReturningRequestsANewLock() = runTest {
        enableFullscreenPlayback()
        resolveWakeLockRequest(0)
        flushBrowserPromises()

        setBrowserVisible(false)
        assertEquals(1, wakeLockReleaseCount(0))
        setBrowserVisible(true)
        assertEquals(2, wakeLockRequestCount())
        resolveWakeLockRequest(1)
        flushBrowserPromises()
        assertEquals(0, wakeLockReleaseCount(1))
    }

    @Test
    fun leavingFullscreenReleasesAndReenteringRequestsANewLock() = runTest {
        enableFullscreenPlayback()
        resolveWakeLockRequest(0)
        flushBrowserPromises()

        setBrowserFullscreen(false)
        assertEquals(1, wakeLockReleaseCount(0))
        setBrowserFullscreen(true)
        assertEquals(2, wakeLockRequestCount())
        resolveWakeLockRequest(1)
        flushBrowserPromises()
    }

    @Test
    fun pageHideReleasesUntilThePageIsShownAgain() = runTest {
        enableFullscreenPlayback()
        resolveWakeLockRequest(0)
        flushBrowserPromises()

        emitBrowserPageEvent("pagehide")
        assertEquals(1, wakeLockReleaseCount(0))
        setBrowserVisible(true)
        setBrowserFullscreen(true)
        assertEquals(1, wakeLockRequestCount())

        emitBrowserPageEvent("pageshow")
        assertEquals(2, wakeLockRequestCount())
        resolveWakeLockRequest(1)
        flushBrowserPromises()
    }

    @Test
    fun aLockGrantedAfterDisablingIsImmediatelyReleased() = runTest {
        val screen = enableFullscreenPlayback()
        screen.keepScreenOn(false)
        resolveWakeLockRequest(0)
        flushBrowserPromises()

        assertEquals(1, wakeLockReleaseCount(0))
        assertEquals(1, wakeLockRequestCount())
        assertEquals(0, wakeLockBrowserListenerCount())
    }

    @Test
    fun aLockGrantedAfterLeavingFullscreenIsImmediatelyReleased() = runTest {
        enableFullscreenPlayback()
        setBrowserFullscreen(false)
        resolveWakeLockRequest(0)
        flushBrowserPromises()

        assertEquals(1, wakeLockReleaseCount(0))
        assertEquals(1, wakeLockRequestCount())
    }

    @Test
    fun aLockGrantedAfterHidingTheTabIsImmediatelyReleased() = runTest {
        enableFullscreenPlayback()
        setBrowserVisible(false)
        resolveWakeLockRequest(0)
        flushBrowserPromises()

        assertEquals(1, wakeLockReleaseCount(0))
        setBrowserVisible(true)
        assertEquals(2, wakeLockRequestCount())
        resolveWakeLockRequest(1)
        flushBrowserPromises()
    }

    @Test
    fun aLockGrantedAfterPageHideIsImmediatelyReleased() = runTest {
        enableFullscreenPlayback()
        emitBrowserPageEvent("pagehide")
        resolveWakeLockRequest(0)
        flushBrowserPromises()

        assertEquals(1, wakeLockReleaseCount(0))
        assertEquals(1, wakeLockRequestCount())
        emitBrowserPageEvent("pageshow")
        assertEquals(2, wakeLockRequestCount())
        resolveWakeLockRequest(1)
        flushBrowserPromises()
    }

    @Test
    fun reenablingBeforeAnOlderRequestFinishesDoesNotRetainTheOldLock() = runTest {
        val screen = enableFullscreenPlayback()
        screen.keepScreenOn(false)
        screen.keepScreenOn(true)
        assertEquals(1, wakeLockRequestCount())

        resolveWakeLockRequest(0)
        flushBrowserPromises()
        assertEquals(1, wakeLockReleaseCount(0))
        assertEquals(2, wakeLockRequestCount())

        resolveWakeLockRequest(1)
        flushBrowserPromises()
        assertEquals(0, wakeLockReleaseCount(1))

        screen.keepScreenOn(false)
        assertEquals(1, wakeLockReleaseCount(1))
    }

    @Test
    fun anOlderRejectedRequestAllowsTheNewPlaybackRequestToProceed() = runTest {
        val screen = enableFullscreenPlayback()
        screen.keepScreenOn(false)
        screen.keepScreenOn(true)
        assertEquals(1, wakeLockRequestCount())

        rejectWakeLockRequest(0)
        flushBrowserPromises()
        assertEquals(2, wakeLockRequestCount())
        assertEquals(0, wakeLockUnhandledRejectionCount())

        resolveWakeLockRequest(1)
        flushBrowserPromises()
        screen.keepScreenOn(false)
        assertEquals(1, wakeLockReleaseCount(1))
    }

    @Test
    fun browsersWithoutWakeLockSupportCanEnterAndLeavePlaybackSafely() = runTest {
        setWakeLockRequestMode("unsupported")
        setBrowserFullscreen(true)
        val screen = screenFeature()

        assertNull(runCatching { screen.keepScreenOn(true) }.exceptionOrNull())
        setBrowserVisible(false)
        setBrowserVisible(true)
        assertNull(runCatching { screen.keepScreenOn(false) }.exceptionOrNull())
        flushBrowserPromises()

        assertEquals(0, wakeLockRequestCount())
        assertEquals(0, wakeLockBrowserListenerCount())
        assertEquals(0, wakeLockUnhandledRejectionCount())
    }

    @Test
    fun synchronousRequestFailuresAreSafeAndDoNotLoop() = runTest {
        setWakeLockRequestMode("throw")
        setBrowserFullscreen(true)
        val screen = screenFeature()

        assertNull(runCatching { screen.keepScreenOn(true) }.exceptionOrNull())
        flushBrowserPromises()
        assertEquals(1, wakeLockRequestCount())
        assertEquals(0, wakeLockUnhandledRejectionCount())

        screen.keepScreenOn(false)
        setWakeLockRequestMode("supported")
        screen.keepScreenOn(true)
        assertEquals(2, wakeLockRequestCount())
        resolveWakeLockRequest(1)
        flushBrowserPromises()
    }

    @Test
    fun rejectedRequestsAreHandledWithoutAnAutomaticRetryLoop() = runTest {
        enableFullscreenPlayback()
        rejectWakeLockRequest(0)
        flushBrowserPromises()

        assertEquals(1, wakeLockRequestCount())
        assertEquals(0, wakeLockUnhandledRejectionCount())

        setBrowserVisible(false)
        setBrowserVisible(true)
        assertEquals(2, wakeLockRequestCount())
        resolveWakeLockRequest(1)
        flushBrowserPromises()
    }

    @Test
    fun systemReleaseDoesNotImmediatelyFightTheBrowserWithAnotherRequest() = runTest {
        enableFullscreenPlayback()
        resolveWakeLockRequest(0)
        flushBrowserPromises()

        releaseWakeLockFromBrowser(0)
        flushBrowserPromises()
        assertEquals(1, wakeLockRequestCount())

        setBrowserVisible(false)
        setBrowserVisible(true)
        assertEquals(2, wakeLockRequestCount())
        resolveWakeLockRequest(1)
        flushBrowserPromises()
    }

    @Test
    fun disablingRemovesListenersAndCannotBeUndoneByBrowserEvents() = runTest {
        val screen = enableFullscreenPlayback()
        resolveWakeLockRequest(0)
        flushBrowserPromises()
        assertTrue(wakeLockBrowserListenerCount() > 0)

        screen.keepScreenOn(false)
        assertEquals(0, wakeLockBrowserListenerCount())
        setBrowserVisible(false)
        setBrowserVisible(true)
        setBrowserFullscreen(false)
        setBrowserFullscreen(true)
        emitBrowserPageEvent("pagehide")
        emitBrowserPageEvent("pageshow")
        flushBrowserPromises()

        assertEquals(1, wakeLockRequestCount())
        assertEquals(1, wakeLockReleaseCount(0))
    }

    @Test
    fun separatePlaybackInstancesDoNotReleaseEachOthersLocks() = runTest {
        val first = enableFullscreenPlayback()
        val second = screenFeature()
        second.keepScreenOn(true)
        assertEquals(2, wakeLockRequestCount())
        resolveWakeLockRequest(0)
        resolveWakeLockRequest(1)
        flushBrowserPromises()

        first.keepScreenOn(false)
        assertEquals(1, wakeLockReleaseCount(0))
        assertEquals(0, wakeLockReleaseCount(1))
        assertTrue(wakeLockBrowserListenerCount() > 0)

        second.keepScreenOn(false)
        assertEquals(1, wakeLockReleaseCount(1))
        assertEquals(0, wakeLockBrowserListenerCount())
    }

    @Test
    fun rejectedReleasePromisesDoNotEscapePlaybackCleanup() = runTest {
        val screen = enableFullscreenPlayback()
        resolveWakeLockRequest(0)
        flushBrowserPromises()
        setWakeLockReleaseMode("reject")

        assertNull(runCatching { screen.keepScreenOn(false) }.exceptionOrNull())
        flushBrowserPromises()

        assertEquals(1, wakeLockReleaseCount(0))
        assertEquals(0, wakeLockUnhandledRejectionCount())
        assertEquals(0, wakeLockBrowserListenerCount())
    }

    @Test
    fun synchronousReleaseFailuresDoNotEscapePlaybackCleanup() = runTest {
        val screen = enableFullscreenPlayback()
        resolveWakeLockRequest(0)
        flushBrowserPromises()
        setWakeLockReleaseMode("throw")

        assertNull(runCatching { screen.keepScreenOn(false) }.exceptionOrNull())
        flushBrowserPromises()

        assertEquals(1, wakeLockReleaseCount(0))
        assertEquals(0, wakeLockBrowserListenerCount())
        assertEquals(0, wakeLockUnhandledRejectionCount())
    }

    private fun screenFeature(): ScreenFeature = getScreenFeature().also(features::add)

    private fun enableFullscreenPlayback(): ScreenFeature {
        setBrowserFullscreen(true)
        return screenFeature().also { it.keepScreenOn(true) }
    }
}

// runTest's virtual clock does not drain native JavaScript Promise jobs. A browser
// timer crosses a real event-loop turn, including unhandled-rejection delivery.
private suspend fun flushBrowserPromises() {
    repeat(2) {
        suspendCoroutine<Unit> { continuation ->
            window.setTimeout({
                continuation.resume(Unit)
                null
            }, 0)
        }
    }
}

// Keep interop arguments primitive so the same fixture runs under both JS and Wasm.
// Only browser API boundaries are replaced; every test enters through ScreenFeature.
// External methods avoid Kotlin/JS dropping parameterized Unit-returning js bodies.
private external interface WakeLockBrowserFixture : JsAny {
    fun setVisible(visible: Boolean)
    fun setFullscreen(fullscreen: Boolean)
    fun emitPageEvent(type: String)
    fun setRequestMode(mode: String)
    fun setReleaseMode(mode: String)
    fun resolve(index: Int)
    fun reject(index: Int)
    fun releaseFromBrowser(index: Int)
}

private fun wakeLockBrowserFixture(): WakeLockBrowserFixture = js("globalThis.__showcaseWakeLockBrowser")

private fun installWakeLockBrowser(): Unit = js(
    "void (() => { " +
        "const fixture = { visible: true, fullscreen: false, requestMode: 'supported', " +
        "releaseMode: 'supported', requests: [], listeners: [], originals: [], unhandled: 0 }; " +
        "globalThis.__showcaseWakeLockBrowser = fixture; " +
        "const replace = (target, key, descriptor) => { " +
        "fixture.originals.push([target, key, Object.getOwnPropertyDescriptor(target, key)]); " +
        "Object.defineProperty(target, key, Object.assign({ configurable: true }, descriptor)); }; " +
        "replace(document, 'visibilityState', { get: () => fixture.visible ? 'visible' : 'hidden' }); " +
        "replace(document, 'fullscreenElement', { get: () => fixture.fullscreen ? document.documentElement : null }); " +
        "const capture = options => typeof options === 'boolean' ? options : !!(options && options.capture); " +
        "const wrapListeners = (target, types) => { " +
        "const add = target.addEventListener; const remove = target.removeEventListener; " +
        "replace(target, 'addEventListener', { value: function(type, listener, options) { " +
        "if (!types.includes(type)) return add.call(this, type, listener, options); " +
        "if (!fixture.listeners.some(entry => entry.target === this && entry.type === type && " +
        "entry.listener === listener && entry.capture === capture(options))) " +
        "fixture.listeners.push({ target: this, type, listener, capture: capture(options) }); } }); " +
        "replace(target, 'removeEventListener', { value: function(type, listener, options) { " +
        "if (!types.includes(type)) return remove.call(this, type, listener, options); " +
        "fixture.listeners = fixture.listeners.filter(entry => !(entry.target === this && " +
        "entry.type === type && entry.listener === listener && entry.capture === capture(options))); } }); }; " +
        "wrapListeners(document, ['visibilitychange', 'fullscreenchange']); " +
        "wrapListeners(window, ['pagehide', 'pageshow']); " +
        "fixture.emit = (target, type) => { const event = new Event(type); " +
        "fixture.listeners.slice().filter(entry => entry.target === target && entry.type === type) " +
        ".forEach(entry => { if (typeof entry.listener === 'function') entry.listener.call(target, event); " +
        "else entry.listener.handleEvent(event); }); }; " +
        "const wakeLock = { request(type) { " +
        "const entry = { type, releaseCalls: 0, settled: false }; fixture.requests.push(entry); " +
        "if (fixture.requestMode === 'throw') throw new Error('Wake lock request failed synchronously'); " +
        "return new Promise((resolve, reject) => { entry.resolve = resolve; entry.reject = reject; }); } }; " +
        "replace(navigator, 'wakeLock', { get: () => fixture.requestMode === 'unsupported' ? undefined : wakeLock }); " +
        "fixture.resolve = index => { const entry = fixture.requests[index]; " +
        "if (entry.settled) throw new Error('Wake lock request already settled'); entry.settled = true; " +
        "const lock = new EventTarget(); lock.released = false; lock.type = 'screen'; " +
        "lock.release = () => { entry.releaseCalls++; " +
        "if (fixture.releaseMode === 'throw') throw new Error('Wake lock release failed synchronously'); " +
        "if (fixture.releaseMode === 'reject') return Promise.reject(new Error('Wake lock release rejected')); " +
        "if (!lock.released) { lock.released = true; lock.dispatchEvent(new Event('release')); } " +
        "return Promise.resolve(); }; " +
        "entry.lock = lock; entry.resolve(lock); }; " +
        "fixture.setVisible = visible => { fixture.visible = visible; fixture.emit(document, 'visibilitychange'); }; " +
        "fixture.setFullscreen = fullscreen => { fixture.fullscreen = fullscreen; fixture.emit(document, 'fullscreenchange'); }; " +
        "fixture.emitPageEvent = type => fixture.emit(window, type); " +
        "fixture.setRequestMode = mode => { fixture.requestMode = mode; }; " +
        "fixture.setReleaseMode = mode => { fixture.releaseMode = mode; }; " +
        "fixture.reject = index => { const entry = fixture.requests[index]; " +
        "if (entry.settled) throw new Error('Wake lock request already settled'); entry.settled = true; " +
        "entry.reject(new Error('Wake lock denied')); }; " +
        "fixture.releaseFromBrowser = index => { const lock = fixture.requests[index].lock; " +
        "lock.released = true; lock.dispatchEvent(new Event('release')); }; " +
        "fixture.onUnhandled = event => { fixture.unhandled++; event.preventDefault(); }; " +
        "window.addEventListener('unhandledrejection', fixture.onUnhandled); " +
        "})()"
)

private fun restoreWakeLockBrowser(): Unit = js(
    "void (() => { const fixture = globalThis.__showcaseWakeLockBrowser; " +
        "if (!fixture) return; window.removeEventListener('unhandledrejection', fixture.onUnhandled); " +
        "fixture.originals.reverse().forEach(([target, key, descriptor]) => { " +
        "if (descriptor) Object.defineProperty(target, key, descriptor); else delete target[key]; }); " +
        "delete globalThis.__showcaseWakeLockBrowser; })()"
)

private fun setBrowserVisible(visible: Boolean) = wakeLockBrowserFixture().setVisible(visible)

private fun setBrowserFullscreen(fullscreen: Boolean) = wakeLockBrowserFixture().setFullscreen(fullscreen)

private fun emitBrowserPageEvent(type: String) = wakeLockBrowserFixture().emitPageEvent(type)

private fun setWakeLockRequestMode(mode: String) = wakeLockBrowserFixture().setRequestMode(mode)

private fun setWakeLockReleaseMode(mode: String) = wakeLockBrowserFixture().setReleaseMode(mode)

private fun resolveWakeLockRequest(index: Int) = wakeLockBrowserFixture().resolve(index)

private fun rejectWakeLockRequest(index: Int) = wakeLockBrowserFixture().reject(index)

private fun releaseWakeLockFromBrowser(index: Int) = wakeLockBrowserFixture().releaseFromBrowser(index)

private fun wakeLockBrowserState(): String = js(
    "document.visibilityState + ':' + (document.fullscreenElement != null)"
)

private fun wakeLockRequestCount(): Int = js("globalThis.__showcaseWakeLockBrowser.requests.length")

private fun wakeLockRequestType(index: Int): String = js(
    "globalThis.__showcaseWakeLockBrowser.requests[index].type"
)

private fun wakeLockReleaseCount(index: Int): Int = js(
    "globalThis.__showcaseWakeLockBrowser.requests[index].releaseCalls"
)

private fun wakeLockBrowserListenerCount(): Int = js("globalThis.__showcaseWakeLockBrowser.listeners.length")

private fun wakeLockUnhandledRejectionCount(): Int = js("globalThis.__showcaseWakeLockBrowser.unhandled")
