package com.alpha.showcase.common.components

import java.util.ServiceConfigurationError
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopSleepInhibitorsTest {

    @Test
    fun factorySelectsThePlatformSpecificInhibitorWithoutLoadingAnotherOsApi() {
        assertIs<WindowsDesktopSleepInhibitor>(createDesktopSleepInhibitor("Windows 11"))
        assertIs<MacOsDesktopSleepInhibitor>(createDesktopSleepInhibitor("Mac OS X"))
        assertIs<MacOsDesktopSleepInhibitor>(createDesktopSleepInhibitor("Darwin"))
        assertIs<LinuxDesktopSleepInhibitor>(createDesktopSleepInhibitor("Linux"))
        assertIs<UnsupportedDesktopSleepInhibitor>(createDesktopSleepInhibitor("FreeBSD"))
    }

    @Test
    fun linuxHoldsSystemdInhibitorOnlyAfterTheChildSignalsReadiness() {
        val starter = RecordingLinuxInhibitorProcessStarter()
        val screenSaverInhibitor = RecordingDesktopSleepInhibitor()
        val inhibitor = LinuxDesktopSleepInhibitor(
            processStarter = starter,
            screenSaverInhibitor = screenSaverInhibitor,
        )

        inhibitor.enable()
        assertEquals(
            listOf(
                "systemd-inhibit",
                "--what=idle",
                "--mode=block",
                "--who=Showcase",
                "--why=Fullscreen photo playback",
                "/bin/sh",
                "-c",
                "printf '\\001'; read _",
            ),
            starter.startedCommands.single(),
        )
        assertEquals(1, starter.process.awaitReadyCalls)
        assertTrue(starter.process.isAlive)
        assertTrue(screenSaverInhibitor.isInhibiting)

        inhibitor.disable()
        assertFalse(starter.process.isAlive)
        assertFalse(screenSaverInhibitor.isInhibiting)
    }

    @Test
    fun linuxKeepsSystemdInhibitorWhenOptionalScreenSaverBackendFailsWithNonfatalError() {
        val starter = RecordingLinuxInhibitorProcessStarter()
        val failures = mutableListOf<Throwable>()
        val inhibitor = LinuxDesktopSleepInhibitor(
            processStarter = starter,
            screenSaverInhibitor = object : DesktopSleepInhibitor {
                override fun enable() {
                    throw ServiceConfigurationError("missing D-Bus transport")
                }

                override fun disable() = Unit
            },
            reportFailure = failures::add,
        )

        inhibitor.enable()

        assertTrue(starter.process.isAlive)
        assertIs<ServiceConfigurationError>(failures.single())

        inhibitor.disable()
        assertFalse(starter.process.isAlive)
    }

    @Test
    fun linuxReleasesAProcessThatExitsBeforeTheReadinessHandshake() {
        val starter = RecordingLinuxInhibitorProcessStarter().apply {
            nextProcessReady = false
        }
        val inhibitor = LinuxDesktopSleepInhibitor(starter)

        assertFailsWith<IllegalStateException> {
            inhibitor.enable()
        }

        assertEquals(1, starter.process.awaitReadyCalls)
        assertFalse(starter.process.isAlive)
    }

    @Test
    fun linuxRetainsProcessOwnershipUntilATransientReleaseFailureCanBeRetried() {
        val starter = RecordingLinuxInhibitorProcessStarter().apply {
            nextReleaseFailures = 1
        }
        val controller = DesktopKeepAwakeController(
            LinuxDesktopSleepInhibitor(processStarter = starter),
        )
        controller.setEnabled(true)

        controller.setEnabled(false)

        assertFalse(controller.isEnabled)
        assertFalse(starter.process.isAlive)
        assertEquals(2, starter.process.releaseCalls)
    }

    @Test
    fun linuxRetainsAPendingProcessWhenReadinessThrowsSoCleanupCanRetry() {
        val starter = RecordingLinuxInhibitorProcessStarter().apply {
            nextReadinessFailure = IllegalStateException("readiness failed")
            nextReleaseFailures = 1
        }
        val controller = DesktopKeepAwakeController(
            LinuxDesktopSleepInhibitor(processStarter = starter),
        )

        controller.setEnabled(true)

        assertFalse(controller.isEnabled)
        assertFalse(starter.process.isAlive)
        assertEquals(2, starter.process.releaseCalls)
    }

    @Test
    fun linuxReacquiresTheIdleInhibitorAfterItsProcessExitsUnexpectedly() {
        val starter = RecordingLinuxInhibitorProcessStarter()
        val recoveryScheduler = QueuedLinuxInhibitorRecoveryScheduler()
        val inhibitor = LinuxDesktopSleepInhibitor(
            processStarter = starter,
            recoveryScheduler = recoveryScheduler,
        )

        inhibitor.enable()
        starter.process.exitUnexpectedly()
        assertEquals(1, recoveryScheduler.pendingTaskCount)

        recoveryScheduler.runNext()
        assertEquals(2, starter.startedCommands.size)
        assertTrue(starter.process.isAlive)

        inhibitor.disable()
        assertEquals(0, recoveryScheduler.pendingTaskCount)
        assertFalse(starter.process.isAlive)
    }

    @Test
    fun freedesktopScreenSaverInhibitorOwnsItsLeaseForThePlaybackLifecycle() {
        val api = RecordingLinuxScreenSaverApi()
        val inhibitor = FreedesktopScreenSaverInhibitor(api)

        inhibitor.enable()
        assertEquals(
            listOf("Showcase" to "Fullscreen photo playback"),
            api.requests,
        )
        assertTrue(api.leaseActive)

        inhibitor.disable()
        assertFalse(api.leaseActive)
    }

    @Test
    fun dbusScreenSaverLeaseRetriesWhenUninhibitAndConnectionCloseBothFail() {
        var uninhibitCalls = 0
        var closeCalls = 0
        val lease = DbusLinuxScreenSaverLease(
            uninhibit = {
                uninhibitCalls += 1
                if (uninhibitCalls == 1) error("transient UnInhibit failure")
            },
            closeConnection = {
                closeCalls += 1
                if (closeCalls == 1) error("transient connection close failure")
            },
        )

        assertFailsWith<IllegalStateException> { lease.release() }
        lease.release()
        lease.release()

        assertEquals(2, uninhibitCalls)
        assertEquals(2, closeCalls)
    }

    @Test
    fun macOsCreatesDisplaySleepAssertionAndReleasesTheSameId() {
        val api = RecordingMacPowerAssertionApi()
        val inhibitor = MacOsDesktopSleepInhibitor(api)

        inhibitor.enable()
        assertEquals(
            listOf("PreventUserIdleDisplaySleep" to "Showcase fullscreen playback"),
            api.createdAssertions,
        )
        assertEquals(setOf(7), api.activeAssertionIds)

        inhibitor.disable()
        assertTrue(api.activeAssertionIds.isEmpty())
    }

    @Test
    fun windowsUsesContinuousDisplayAndSystemRequestsOnOneNativeThread() {
        val api = RecordingWindowsExecutionStateApi()
        val inhibitor = WindowsDesktopSleepInhibitor(api)

        inhibitor.enable()
        inhibitor.disable()

        assertEquals(
            listOf(0x80000003.toInt(), 0x80000000.toInt()),
            api.requestedStates,
        )
        assertEquals(1, api.threadIds.distinct().size)
    }

    @Test
    fun windowsWaitsForTheNativeCallWhenInterruptedAndRestoresTheInterruptFlag() {
        val api = BlockingFirstWindowsExecutionStateApi()
        val inhibitor = WindowsDesktopSleepInhibitor(api)
        val completed = CountDownLatch(1)
        val callerWasInterrupted = AtomicBoolean(false)
        val failure = AtomicReference<Throwable?>(null)
        val caller = Thread {
            try {
                inhibitor.enable()
                callerWasInterrupted.set(Thread.currentThread().isInterrupted)
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                completed.countDown()
            }
        }

        caller.start()
        assertTrue(api.nativeCallStarted.await(1, TimeUnit.SECONDS))
        caller.interrupt()
        assertFalse(completed.await(100, TimeUnit.MILLISECONDS))

        api.allowNativeCallToReturn.countDown()
        assertTrue(completed.await(1, TimeUnit.SECONDS))
        assertNull(failure.get())
        assertTrue(callerWasInterrupted.get())
    }

    private class RecordingWindowsExecutionStateApi : WindowsExecutionStateApi {
        val requestedStates = mutableListOf<Int>()
        val threadIds = mutableListOf<Long>()

        override fun setThreadExecutionState(state: Int): Int {
            requestedStates += state
            @Suppress("DEPRECATION")
            threadIds += Thread.currentThread().id
            return 1
        }
    }

    private class BlockingFirstWindowsExecutionStateApi : WindowsExecutionStateApi {
        val nativeCallStarted = CountDownLatch(1)
        val allowNativeCallToReturn = CountDownLatch(1)

        override fun setThreadExecutionState(state: Int): Int {
            nativeCallStarted.countDown()
            allowNativeCallToReturn.await()
            return 1
        }
    }

    private class RecordingMacPowerAssertionApi : MacPowerAssertionApi {
        val createdAssertions = mutableListOf<Pair<String, String>>()
        val activeAssertionIds = mutableSetOf<Int>()

        override fun createAssertion(type: String, name: String): Int {
            createdAssertions += type to name
            return 7.also(activeAssertionIds::add)
        }

        override fun releaseAssertion(id: Int) {
            check(activeAssertionIds.remove(id)) { "unknown assertion id: $id" }
        }
    }

    private class RecordingLinuxScreenSaverApi : LinuxScreenSaverApi {
        val requests = mutableListOf<Pair<String, String>>()
        var leaseActive = false

        override fun inhibit(applicationName: String, reason: String): LinuxScreenSaverLease {
            requests += applicationName to reason
            leaseActive = true
            return LinuxScreenSaverLease {
                leaseActive = false
            }
        }
    }

    private class RecordingDesktopSleepInhibitor : DesktopSleepInhibitor {
        var isInhibiting = false

        override fun enable() {
            isInhibiting = true
        }

        override fun disable() {
            isInhibiting = false
        }
    }

    private class RecordingLinuxInhibitorProcessStarter : LinuxInhibitorProcessStarter {
        val startedCommands = mutableListOf<List<String>>()
        private val processes = mutableListOf<RecordingLinuxInhibitorProcess>()
        var nextProcessReady: Boolean = true
        var nextReadinessFailure: Throwable? = null
        var nextReleaseFailures: Int = 0
        val process: RecordingLinuxInhibitorProcess
            get() = processes.last()

        override fun start(command: List<String>): LinuxInhibitorProcess {
            startedCommands += command
            return RecordingLinuxInhibitorProcess(
                isAlive = true,
                ready = nextProcessReady,
                readinessFailure = nextReadinessFailure,
                releaseFailuresRemaining = nextReleaseFailures,
            ).also(processes::add)
        }
    }

    private class RecordingLinuxInhibitorProcess(
        override var isAlive: Boolean,
        private val ready: Boolean,
        private val readinessFailure: Throwable?,
        private var releaseFailuresRemaining: Int,
    ) : LinuxInhibitorProcess {
        var awaitReadyCalls: Int = 0
        var releaseCalls: Int = 0
        private var onExit: (() -> Unit)? = null

        override fun awaitReady(timeoutMillis: Long): Boolean {
            awaitReadyCalls += 1
            readinessFailure?.let { throw it }
            return ready && isAlive
        }

        override fun onExit(callback: () -> Unit) {
            onExit = callback
            if (!isAlive) callback()
        }

        override fun release() {
            releaseCalls += 1
            if (releaseFailuresRemaining > 0) {
                releaseFailuresRemaining -= 1
                error("process release failed")
            }
            isAlive = false
            onExit?.invoke()
        }

        fun exitUnexpectedly() {
            isAlive = false
            onExit?.invoke()
        }
    }

    private class QueuedLinuxInhibitorRecoveryScheduler : LinuxInhibitorRecoveryScheduler {
        private val tasks = ArrayDeque<() -> Unit>()
        val pendingTaskCount: Int
            get() = tasks.size

        override fun schedule(task: () -> Unit) {
            tasks.addLast(task)
        }

        fun runNext() {
            tasks.removeFirst().invoke()
        }
    }
}
