package com.alpha.showcase.common.components

import com.alpha.showcase.common.utils.Log
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.platform.mac.CoreFoundation
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.ptr.IntByReference
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

internal fun createDesktopSleepInhibitor(
    osName: String = System.getProperty("os.name").orEmpty(),
): DesktopSleepInhibitor {
    val normalizedOsName = osName.trim().lowercase(Locale.ROOT)
    return when {
        normalizedOsName.startsWith("windows") -> WindowsDesktopSleepInhibitor()
        "mac" in normalizedOsName || "darwin" in normalizedOsName -> MacOsDesktopSleepInhibitor()
        "linux" in normalizedOsName -> LinuxDesktopSleepInhibitor(
            screenSaverInhibitor = FreedesktopScreenSaverInhibitor(),
        )
        else -> UnsupportedDesktopSleepInhibitor(osName)
    }
}

internal class UnsupportedDesktopSleepInhibitor(
    private val osName: String,
) : DesktopSleepInhibitor {
    override fun enable(): Unit = error("Desktop keep-awake is unsupported on '$osName'")
    override fun disable() = Unit
}

internal fun interface WindowsExecutionStateApi {
    fun setThreadExecutionState(state: Int): Int
}

private object JnaWindowsExecutionStateApi : WindowsExecutionStateApi {
    override fun setThreadExecutionState(state: Int): Int =
        Kernel32.INSTANCE.SetThreadExecutionState(state)
}

internal class WindowsDesktopSleepInhibitor(
    private val api: WindowsExecutionStateApi = JnaWindowsExecutionStateApi,
    private val executionStateThread: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "showcase-windows-keep-awake").apply { isDaemon = true }
    },
) : DesktopSleepInhibitor {

    override fun enable() {
        setExecutionState(
            WinBase.ES_CONTINUOUS or
                WinBase.ES_DISPLAY_REQUIRED or
                WinBase.ES_SYSTEM_REQUIRED,
        )
    }

    override fun disable() {
        setExecutionState(WinBase.ES_CONTINUOUS)
    }

    private fun setExecutionState(state: Int) {
        val previousState = try {
            executionStateThread
                .submit<Int> { api.setThreadExecutionState(state) }
                .getUninterruptibly()
        } catch (error: ExecutionException) {
            throw error.cause ?: error
        }

        check(previousState != 0) {
            "SetThreadExecutionState failed for state 0x${state.toUInt().toString(16)}"
        }
    }
}

private fun <T> Future<T>.getUninterruptibly(): T {
    var interrupted = false
    try {
        while (true) {
            try {
                return get()
            } catch (_: InterruptedException) {
                interrupted = true
            }
        }
    } finally {
        if (interrupted) Thread.currentThread().interrupt()
    }
}

internal interface MacPowerAssertionApi {
    fun createAssertion(type: String, name: String): Int
    fun releaseAssertion(id: Int)
}

internal class MacOsDesktopSleepInhibitor(
    private val api: MacPowerAssertionApi = JnaMacPowerAssertionApi,
) : DesktopSleepInhibitor {
    private var assertionId: Int? = null

    override fun enable() {
        check(assertionId == null) { "macOS display sleep assertion is already active" }
        assertionId = api.createAssertion(
            type = MACOS_PREVENT_USER_IDLE_DISPLAY_SLEEP,
            name = KEEP_AWAKE_REASON,
        )
    }

    override fun disable() {
        val activeAssertionId = assertionId ?: return
        api.releaseAssertion(activeAssertionId)
        assertionId = null
    }
}

private object JnaMacPowerAssertionApi : MacPowerAssertionApi {
    private val powerManagement: MacPowerManagement by lazy {
        Native.load("IOKit", MacPowerManagement::class.java)
    }

    override fun createAssertion(type: String, name: String): Int {
        val typeRef = checkNotNull(CoreFoundation.CFStringRef.createCFString(type))
        val nameRef = checkNotNull(CoreFoundation.CFStringRef.createCFString(name))
        val assertionId = IntByReference()

        val result = try {
            powerManagement.IOPMAssertionCreateWithName(
                typeRef,
                MACOS_ASSERTION_LEVEL_ON,
                nameRef,
                assertionId,
            )
        } finally {
            typeRef.release()
            nameRef.release()
        }

        check(result == MACOS_IO_SUCCESS) {
            "IOPMAssertionCreateWithName failed with IOReturn 0x${result.toUInt().toString(16)}"
        }
        return assertionId.value
    }

    override fun releaseAssertion(id: Int) {
        val result = powerManagement.IOPMAssertionRelease(id)
        check(result == MACOS_IO_SUCCESS) {
            "IOPMAssertionRelease failed with IOReturn 0x${result.toUInt().toString(16)}"
        }
    }
}

internal interface MacPowerManagement : Library {
    fun IOPMAssertionCreateWithName(
        type: CoreFoundation.CFStringRef,
        level: Int,
        name: CoreFoundation.CFStringRef,
        assertionId: IntByReference,
    ): Int

    fun IOPMAssertionRelease(assertionId: Int): Int
}

private const val MACOS_PREVENT_USER_IDLE_DISPLAY_SLEEP = "PreventUserIdleDisplaySleep"
private const val MACOS_ASSERTION_LEVEL_ON = 255
private const val MACOS_IO_SUCCESS = 0
private const val KEEP_AWAKE_REASON = "Showcase fullscreen playback"

internal interface LinuxInhibitorProcess {
    val isAlive: Boolean
    fun awaitReady(timeoutMillis: Long): Boolean
    fun onExit(callback: () -> Unit)
    fun release()
}

internal fun interface LinuxInhibitorProcessStarter {
    fun start(command: List<String>): LinuxInhibitorProcess
}

internal fun interface LinuxInhibitorRecoveryScheduler {
    fun schedule(task: () -> Unit)
}

internal class LinuxDesktopSleepInhibitor(
    private val processStarter: LinuxInhibitorProcessStarter = JdkLinuxInhibitorProcessStarter,
    private val screenSaverInhibitor: DesktopSleepInhibitor? = null,
    private val recoveryScheduler: LinuxInhibitorRecoveryScheduler = JdkLinuxInhibitorRecoveryScheduler,
    private val reportFailure: (Throwable) -> Unit = { error ->
        Log.w(
            "DesktopKeepAwake",
            "Linux idle inhibitor recovery failed: ${error.message ?: error::class.simpleName}",
        )
    },
) : DesktopSleepInhibitor {
    private val stateLock = Any()
    private var requested = false
    private var process: LinuxInhibitorProcess? = null
    private var processReady = false
    private var acquisitionInProgress = false
    private var recoveryScheduled = false

    override fun enable() {
        synchronized(stateLock) {
            check(!requested) { "Linux idle inhibitor is already active" }
            requested = true
        }

        try {
            acquireAndInstallProcess()
            enableScreenSaverBestEffort()
        } catch (error: Throwable) {
            synchronized(stateLock) {
                requested = false
            }
            try {
                releaseCurrentProcess()
            } catch (cleanupError: Throwable) {
                error.addSuppressed(cleanupError)
            }
            scheduleRecoveryIfNeeded()
            throw error
        }
    }

    override fun disable() {
        synchronized(stateLock) {
            requested = false
        }

        var releaseFailure: Throwable? = null
        try {
            screenSaverInhibitor?.disable()
        } catch (error: Throwable) {
            releaseFailure = error
        }

        try {
            releaseCurrentProcess()
        } catch (error: Throwable) {
            releaseFailure?.addSuppressed(error) ?: run { releaseFailure = error }
        }

        if (releaseFailure != null) scheduleRecoveryIfNeeded()
        releaseFailure?.let { throw it }
    }

    private fun enableScreenSaverBestEffort() {
        try {
            screenSaverInhibitor?.enable()
        } catch (error: Throwable) {
            error.throwIfFatal()
            reportFailure(error)
        }
    }

    private fun acquireAndInstallProcess() {
        synchronized(stateLock) {
            check(process == null) { "A Linux inhibitor process is already owned" }
            check(!acquisitionInProgress) { "Linux inhibitor acquisition is already in progress" }
            acquisitionInProgress = true
        }

        try {
            val startedProcess = processStarter.start(LINUX_INHIBITOR_COMMAND)
            synchronized(stateLock) {
                process = startedProcess
                processReady = false
            }

            startedProcess.onExit {
                onProcessExit(startedProcess)
            }

            if (
                !startedProcess.awaitReady(LINUX_INHIBITOR_READY_TIMEOUT_MS) ||
                !startedProcess.isAlive
            ) {
                error("systemd-inhibit exited before acquiring an idle inhibitor")
            }

            val shouldKeepProcess = synchronized(stateLock) {
                if (process === startedProcess && requested) {
                    processReady = true
                    true
                } else {
                    false
                }
            }
            if (!shouldKeepProcess) {
                releaseOwnedProcess(startedProcess)
            }
        } finally {
            synchronized(stateLock) {
                acquisitionInProgress = false
            }
        }
    }

    private fun releaseCurrentProcess() {
        val ownedProcess = synchronized(stateLock) { process } ?: return
        releaseOwnedProcess(ownedProcess)
    }

    private fun releaseOwnedProcess(ownedProcess: LinuxInhibitorProcess) {
        try {
            ownedProcess.release()
        } catch (error: Throwable) {
            if (ownedProcess.isAlive) throw error
        }

        synchronized(stateLock) {
            if (process === ownedProcess) {
                process = null
                processReady = false
            }
        }
    }

    private fun onProcessExit(exitedProcess: LinuxInhibitorProcess) {
        synchronized(stateLock) {
            if (process !== exitedProcess) return
            process = null
            processReady = false
        }
        scheduleRecoveryIfNeeded()
    }

    private fun scheduleRecoveryIfNeeded() {
        val shouldSchedule = synchronized(stateLock) {
            val needsCleanup = process != null && (!requested || !processReady)
            val needsAcquisition = requested && process == null
            if (
                acquisitionInProgress ||
                recoveryScheduled ||
                (!needsCleanup && !needsAcquisition)
            ) {
                false
            } else {
                recoveryScheduled = true
                true
            }
        }
        if (!shouldSchedule) return

        recoveryScheduler.schedule {
            recoverAfterUnexpectedExit()
        }
    }

    private fun recoverAfterUnexpectedExit() {
        synchronized(stateLock) {
            recoveryScheduled = false
        }

        try {
            val processNeedingCleanup = synchronized(stateLock) {
                process?.takeIf { !requested || !processReady }
            }
            if (processNeedingCleanup != null) {
                releaseOwnedProcess(processNeedingCleanup)
            }

            val shouldAcquire = synchronized(stateLock) {
                requested && process == null && !acquisitionInProgress
            }
            if (shouldAcquire) acquireAndInstallProcess()
        } catch (error: Exception) {
            reportFailure(error)
        } finally {
            scheduleRecoveryIfNeeded()
        }
    }
}

private object JdkLinuxInhibitorRecoveryScheduler : LinuxInhibitorRecoveryScheduler {
    private val executor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "showcase-linux-keep-awake-recovery").apply { isDaemon = true }
    }

    override fun schedule(task: () -> Unit) {
        executor.schedule(task, LINUX_INHIBITOR_RECOVERY_DELAY_MS, TimeUnit.MILLISECONDS)
    }
}

private object JdkLinuxInhibitorProcessStarter : LinuxInhibitorProcessStarter {
    override fun start(command: List<String>): LinuxInhibitorProcess {
        val process = ProcessBuilder(command)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        return JdkLinuxInhibitorProcess(process)
    }
}

private class JdkLinuxInhibitorProcess(
    private val process: Process,
) : LinuxInhibitorProcess {
    private val readiness = CompletableFuture.supplyAsync {
        process.inputStream.read() == LINUX_INHIBITOR_READY_SIGNAL
    }

    override val isAlive: Boolean
        get() = process.isAlive

    override fun awaitReady(timeoutMillis: Long): Boolean = try {
        readiness.get(timeoutMillis, TimeUnit.MILLISECONDS)
    } catch (_: TimeoutException) {
        false
    } catch (error: InterruptedException) {
        Thread.currentThread().interrupt()
        throw error
    } catch (error: ExecutionException) {
        throw error.cause ?: error
    }

    override fun onExit(callback: () -> Unit) {
        process.onExit().thenRun(callback)
    }

    override fun release() {
        runCatching { process.outputStream.close() }
        if (process.waitFor(LINUX_PROCESS_EXIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) return

        destroyDescendants(force = false)
        process.destroy()
        if (process.waitFor(LINUX_PROCESS_EXIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) return

        destroyDescendants(force = true)
        process.destroyForcibly()
        check(process.waitFor(LINUX_PROCESS_EXIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            "systemd-inhibit did not exit after playback released it"
        }
    }

    private fun destroyDescendants(force: Boolean) {
        process.descendants().iterator().forEachRemaining { descendant ->
            if (force) {
                descendant.destroyForcibly()
            } else {
                descendant.destroy()
            }
        }
    }
}

private val LINUX_INHIBITOR_COMMAND = listOf(
    "systemd-inhibit",
    "--what=idle",
    "--mode=block",
    "--who=$LINUX_INHIBITOR_APPLICATION_NAME",
    "--why=$LINUX_INHIBITOR_REASON",
    "/bin/sh",
    "-c",
    "printf '\\001'; read _",
)

private const val LINUX_INHIBITOR_READY_SIGNAL = 1
private const val LINUX_INHIBITOR_READY_TIMEOUT_MS = 1_000L
private const val LINUX_INHIBITOR_RECOVERY_DELAY_MS = 1_000L
private const val LINUX_PROCESS_EXIT_TIMEOUT_MS = 250L
