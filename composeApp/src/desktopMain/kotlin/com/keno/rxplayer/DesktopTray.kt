package com.keno.rxplayer

import java.awt.AWTException
import java.awt.Color
import java.awt.Font
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.Toolkit
import java.awt.TrayIcon
import java.awt.image.BufferedImage

internal data class DesktopTrayState(val tooltip: String, val playLabel: String, val canPlay: Boolean, val canSkip: Boolean)

internal fun desktopTrayState(title: String, playing: Boolean, waiting: Boolean, playWhenReady: Boolean,
    hasTrack: Boolean, queueSize: Int, closing: Boolean) = DesktopTrayState(
    if (title.isBlank()) "RxPlayer" else "RxPlayer · $title".take(120),
    if (playing || waiting && playWhenReady) "暂停" else "播放",
    !closing && (hasTrack || queueSize > 0),
    !closing && queueSize > 1,
)

/** Created, updated and removed on the AWT event thread used by Compose Desktop. */
internal class DesktopTray private constructor(
    private val system: SystemTray,
    private val icon: TrayIcon,
    private val open: MenuItem,
    private val play: MenuItem,
    private val previous: MenuItem,
    private val next: MenuItem,
    private val quit: MenuItem,
) : AutoCloseable {
    fun update(state: DesktopTrayState, closing: Boolean) {
        icon.toolTip = state.tooltip
        play.label = state.playLabel
        play.isEnabled = state.canPlay
        previous.isEnabled = state.canSkip
        next.isEnabled = state.canSkip
        open.isEnabled = !closing
        quit.isEnabled = !closing
    }

    override fun close() {
        system.remove(icon)
        icon.actionListeners.forEach(icon::removeActionListener)
        listOf(open, play, previous, next, quit).forEach { item -> item.actionListeners.forEach(item::removeActionListener) }
    }

    companion object {
        fun install(onOpen: () -> Unit, onPlay: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit, onQuit: () -> Unit): DesktopTray? {
            try {
                if (!SystemTray.isSupported()) return null
                val system = SystemTray.getSystemTray()
                // AWT's default menu font does not reliably fall back for CJK on Windows.
                val menu = PopupMenu().apply {
                    font = Toolkit.getDefaultToolkit().getDesktopProperty("win.menu.font") as? Font
                        ?: Font(Font.DIALOG, Font.PLAIN, 12)
                }
                fun item(label: String, action: () -> Unit) = MenuItem(label).also { it.addActionListener { action() }; menu.add(it) }
                val open = item("打开 RxPlayer", onOpen)
                menu.addSeparator()
                val play = item("播放", onPlay).apply { isEnabled = false }
                val previous = item("上一曲", onPrevious).apply { isEnabled = false }
                val next = item("下一曲", onNext).apply { isEnabled = false }
                menu.addSeparator()
                val quit = item("退出 RxPlayer", onQuit)
                val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB)
                image.createGraphics().apply {
                    setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    color = Color(0x80, 0xd5, 0xcf)
                    fillRoundRect(0, 0, 32, 32, 12, 12)
                    color = Color(0x00, 0x37, 0x33)
                    listOf(10, 20, 26, 16, 8).forEachIndexed { index, height -> fillRoundRect(5 + index * 5, (32 - height) / 2, 3, height, 3, 3) }
                    dispose()
                }
                val icon = TrayIcon(image, "RxPlayer", menu).apply {
                    isImageAutoSize = true
                    addActionListener { onOpen() }
                }
                system.add(icon)
                println("RxPlayer: Windows tray connected")
                return DesktopTray(system, icon, open, play, previous, next, quit)
            } catch (error: AWTException) {
                System.err.println("RxPlayer: tray unavailable; closing will exit (${error.javaClass.simpleName})")
            } catch (error: UnsupportedOperationException) {
                System.err.println("RxPlayer: tray unavailable; closing will exit (${error.javaClass.simpleName})")
            } catch (error: SecurityException) {
                System.err.println("RxPlayer: tray unavailable; closing will exit (${error.javaClass.simpleName})")
            }
            return null
        }
    }
}
