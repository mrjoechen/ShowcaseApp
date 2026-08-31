package com.alpha.showcase.common.components

import org.freedesktop.dbus.annotations.DBusInterfaceName
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.UInt32

internal fun interface LinuxScreenSaverLease {
    fun release()
}

internal fun interface LinuxScreenSaverApi {
    fun inhibit(applicationName: String, reason: String): LinuxScreenSaverLease
}

internal class FreedesktopScreenSaverInhibitor(
    private val api: LinuxScreenSaverApi = DbusLinuxScreenSaverApi,
) : DesktopSleepInhibitor {
    private var lease: LinuxScreenSaverLease? = null

    override fun enable() {
        check(lease == null) { "Linux screen saver inhibitor is already active" }
        lease = api.inhibit(
            applicationName = LINUX_INHIBITOR_APPLICATION_NAME,
            reason = LINUX_INHIBITOR_REASON,
        )
    }

    override fun disable() {
        val activeLease = lease ?: return
        activeLease.release()
        lease = null
    }
}

private object DbusLinuxScreenSaverApi : LinuxScreenSaverApi {
    override fun inhibit(applicationName: String, reason: String): LinuxScreenSaverLease {
        val connection = DBusConnectionBuilder.forSessionBus()
            .withShared(false)
            .build()

        return try {
            val screenSaver = connection.getRemoteObject(
                LINUX_SCREEN_SAVER_BUS_NAME,
                LINUX_SCREEN_SAVER_OBJECT_PATH,
                FreedesktopScreenSaverRemote::class.java,
                true,
            )
            val cookie = screenSaver.Inhibit(applicationName, reason)
            DbusLinuxScreenSaverLease(
                uninhibit = { screenSaver.UnInhibit(cookie) },
                closeConnection = connection::close,
            )
        } catch (error: Throwable) {
            runCatching { connection.close() }
            throw error
        }
    }
}

internal class DbusLinuxScreenSaverLease(
    private val uninhibit: () -> Unit,
    private val closeConnection: () -> Unit,
) : LinuxScreenSaverLease {
    private var released = false

    @Synchronized
    override fun release() {
        if (released) return

        var releaseFailure: Throwable? = null
        var uninhibitSucceeded = false
        try {
            uninhibit()
            uninhibitSucceeded = true
        } catch (error: Throwable) {
            releaseFailure = error
        }

        var closeSucceeded = false
        try {
            closeConnection()
            closeSucceeded = true
        } catch (error: Throwable) {
            releaseFailure?.addSuppressed(error) ?: run { releaseFailure = error }
        } finally {
            released = uninhibitSucceeded || closeSucceeded
        }

        releaseFailure?.let { throw it }
    }
}

@Suppress("FunctionName")
@DBusInterfaceName(LINUX_SCREEN_SAVER_BUS_NAME)
internal interface FreedesktopScreenSaverRemote : DBusInterface {
    fun Inhibit(applicationName: String, reason: String): UInt32
    fun UnInhibit(cookie: UInt32)
}

internal const val LINUX_INHIBITOR_APPLICATION_NAME = "Showcase"
internal const val LINUX_INHIBITOR_REASON = "Fullscreen photo playback"
private const val LINUX_SCREEN_SAVER_BUS_NAME = "org.freedesktop.ScreenSaver"
private const val LINUX_SCREEN_SAVER_OBJECT_PATH = "/org/freedesktop/ScreenSaver"
