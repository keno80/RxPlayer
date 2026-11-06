package com.keno.rxplayer

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.win32.StdCallLibrary
import java.awt.EventQueue
import java.util.concurrent.Executors

/** Unicode, system-drawn menus; AWT owner-drawing can lose CJK despite Font.canDisplay succeeding. */
internal class WindowsTrayMenu : AutoCloseable {
    internal interface Api : StdCallLibrary {
        fun CreatePopupMenu(): Pointer?
        fun AppendMenuW(menu: Pointer, flags: Int, id: Long, label: WString?): Boolean
        fun DestroyMenu(menu: Pointer): Boolean
        fun GetCursorPos(point: IntArray): Boolean
        fun SetForegroundWindow(window: Pointer): Boolean
        fun TrackPopupMenu(menu: Pointer, flags: Int, x: Int, y: Int, reserved: Int, window: Pointer, rect: Pointer?): Int
        fun PostMessageW(window: Pointer, message: Int, wParam: Long, lParam: Long): Boolean
        fun GetMenuStringW(menu: Pointer, item: Int, label: CharArray, capacity: Int, flags: Int): Int
        fun GetMenuState(menu: Pointer, item: Int, flags: Int): Int
        fun CreateWindowExW(exStyle: Int, className: WString, title: WString, style: Int,
            x: Int, y: Int, width: Int, height: Int, parent: Pointer?, menu: Pointer?, instance: Pointer?, parameter: Pointer?): Pointer?
        fun DestroyWindow(window: Pointer): Boolean
    }

    companion object {
        val api: Api by lazy { Native.load("user32", Api::class.java) }

        fun create(state: DesktopTrayState, closing: Boolean): Pointer {
            val menu = checkNotNull(api.CreatePopupMenu()) { "Unable to create tray menu" }
            try {
                fun item(id: Long, label: String, enabled: Boolean) {
                    check(api.AppendMenuW(menu, if (enabled) 0 else 1, id, WString(label))) { "Unable to append tray command" }
                }
                fun separator() { check(api.AppendMenuW(menu, 0x800, 0, null)) }
                item(1, "打开 RxPlayer", !closing)
                separator()
                item(2, state.playLabel, state.canPlay)
                item(3, "上一曲", state.canSkip)
                item(4, "下一曲", state.canSkip)
                separator()
                item(5, "退出 RxPlayer", !closing)
                return menu
            } catch (error: Throwable) {
                api.DestroyMenu(menu)
                throw error
            }
        }
    }

    private val worker = Executors.newSingleThreadExecutor { Thread(it, "RxPlayer-Tray").apply { isDaemon = true } }
    private val lock = Any()
    private var owner: Pointer? = null
    private var closed = false
    private var showing = false

    fun show(state: DesktopTrayState, closing: Boolean, onCommand: (Int) -> Unit) {
        synchronized(lock) {
            if (closed || showing) return
            showing = true
        }
        worker.execute {
            var command = 0
            try {
                val menu = create(state, closing)
                var window: Pointer? = null
                try {
                    // The hidden owner and its blocking menu loop belong to this worker, never the EDT.
                    window = checkNotNull(api.CreateWindowExW(0x80, WString("STATIC"), WString("RxPlayer tray"),
                        Int.MIN_VALUE, 0, 0, 0, 0, null, null, null, null))
                    val active = synchronized(lock) { if (closed) false else { owner = window; true } }
                    if (active) {
                        val point = IntArray(2)
                        check(api.GetCursorPos(point)) { "Unable to locate tray menu" }
                        api.SetForegroundWindow(window)
                        command = api.TrackPopupMenu(menu, 0x100 or 0x80 or 0x2, point[0], point[1], 0, window, null)
                        api.PostMessageW(window, 0, 0, 0)
                    }
                } finally {
                    synchronized(lock) { owner = null }
                    window?.let { api.DestroyWindow(it) }
                    api.DestroyMenu(menu)
                }
            } catch (error: Exception) {
                System.err.println("RxPlayer: tray menu unavailable (${error.javaClass.simpleName})")
            } finally {
                EventQueue.invokeLater {
                    val deliver = synchronized(lock) { showing = false; !closed }
                    if (deliver && command != 0) onCommand(command)
                }
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            owner?.let { api.PostMessageW(it, 0x1f, 0, 0) } // WM_CANCELMODE ends the worker's menu loop.
        }
        worker.shutdown()
    }
}
