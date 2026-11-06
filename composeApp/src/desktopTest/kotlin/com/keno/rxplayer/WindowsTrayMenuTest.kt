package com.keno.rxplayer

import kotlin.test.*

class WindowsTrayMenuTest {
    @Test fun nativeMenuKeepsUnicodeLabelsAndCommandAvailability() {
        val state = desktopTrayState("夜航", false, true, true, true, 1, false)
        val menu = WindowsTrayMenu.create(state, false)
        try {
            for ((id, expected) in mapOf(1 to "打开 RxPlayer", 2 to "暂停", 3 to "上一曲", 4 to "下一曲", 5 to "退出 RxPlayer")) {
                val label = CharArray(100)
                val length = WindowsTrayMenu.api.GetMenuStringW(menu, id, label, label.size, 0)
                assertEquals(expected, label.concatToString(0, length))
                val flags = WindowsTrayMenu.api.GetMenuState(menu, id, 0)
                assertEquals(id == 3 || id == 4, flags and 3 != 0)
                assertEquals(0, flags and 0x100, "Windows must draw text; no AWT owner-draw flag")
            }
        } finally { assertTrue(WindowsTrayMenu.api.DestroyMenu(menu)) }
    }
}
