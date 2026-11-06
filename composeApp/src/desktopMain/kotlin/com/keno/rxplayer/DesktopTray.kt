package com.keno.rxplayer

import java.awt.AWTException
import java.awt.Color
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.awt.image.BufferedImage

internal data class DesktopTrayState(val tooltip: String, val pauseAction: Boolean, val canPlay: Boolean, val canSkip: Boolean) {
    val playLabel: String get() = if (pauseAction) "暂停" else "播放"
}

internal fun desktopTrayState(title: String, playing: Boolean, waiting: Boolean, playWhenReady: Boolean,
    hasTrack: Boolean, queueSize: Int, closing: Boolean) = DesktopTrayState(
    if (title.isBlank()) "RxPlayer" else "RxPlayer · $title".take(120),
    playing || waiting && playWhenReady,
    !closing && (hasTrack || queueSize > 0),
    !closing && queueSize > 1,
)

/** Created, updated and removed on the AWT event thread used by Compose Desktop. */
internal class DesktopTray private constructor(
    private val system: SystemTray,
    private val icon: TrayIcon,
    private val menuListener: MouseListener,
    private val popup: WindowsTrayMenu,
) : AutoCloseable {
    private var state = desktopTrayState("", false, false, false, false, 0, false)
    private var closing = false

    fun update(state: DesktopTrayState, closing: Boolean) {
        this.state = state
        this.closing = closing
        icon.toolTip = state.tooltip
    }

    override fun close() {
        system.remove(icon)
        icon.actionListeners.forEach(icon::removeActionListener)
        icon.removeMouseListener(menuListener)
        popup.close()
    }

    companion object {
        fun install(onOpen: () -> Unit, onPlaybackAction: (pause: Boolean) -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onQuit: () -> Unit): DesktopTray? {
            try {
                if (!System.getProperty("os.name").startsWith("Windows") || !SystemTray.isSupported()) return null
                WindowsTrayMenu.api // Load the native library before offering close-to-tray.
                val system = SystemTray.getSystemTray()
                val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
                image.createGraphics().apply {
                    setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    color = Color(0x80, 0xd5, 0xcf)
                    fillRoundRect(0, 0, 32, 32, 12, 12)
                    color = Color(0x00, 0x37, 0x33)
                    listOf(10, 20, 26, 16, 8).forEachIndexed { index, height -> fillRoundRect(5 + index * 5, (32 - height) / 2, 3, height, 3, 3) }
                    dispose()
                }
                val icon = TrayIcon(image, "RxPlayer").apply {
                    isImageAutoSize = true
                    addActionListener { onOpen() }
                }
                lateinit var tray: DesktopTray
                val popup = WindowsTrayMenu()
                val listener = object : MouseAdapter() {
                    override fun mouseReleased(event: MouseEvent) {
                        if (event.button != MouseEvent.BUTTON3 || tray.closing) return
                        val snapshot = tray.state
                        popup.show(snapshot, tray.closing) { command ->
                            when (command) {
                                1 -> onOpen()
                                2 -> onPlaybackAction(snapshot.pauseAction)
                                3 -> onPrevious()
                                4 -> onNext()
                                5 -> onQuit()
                            }
                        }
                    }
                }
                tray = DesktopTray(system, icon, listener, popup)
                icon.addMouseListener(listener)
                try { system.add(icon) } catch (error: Throwable) { tray.close(); throw error }
                println("RxPlayer: Windows tray connected (system Unicode menu)")
                return tray
            } catch (error: AWTException) {
                System.err.println("RxPlayer: tray unavailable; closing will exit (${error.javaClass.simpleName})")
            } catch (error: UnsupportedOperationException) {
                System.err.println("RxPlayer: tray unavailable; closing will exit (${error.javaClass.simpleName})")
            } catch (error: SecurityException) {
                System.err.println("RxPlayer: tray unavailable; closing will exit (${error.javaClass.simpleName})")
            } catch (error: LinkageError) {
                System.err.println("RxPlayer: tray unavailable; closing will exit (${error.javaClass.simpleName})")
            }
            return null
        }
    }
}
