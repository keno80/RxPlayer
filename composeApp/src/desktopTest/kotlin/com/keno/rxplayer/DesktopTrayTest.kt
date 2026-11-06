package com.keno.rxplayer

import kotlin.test.*

class DesktopTrayTest {
    @Test fun waitingPlaybackCanBePausedAndClosingDisablesCommands() {
        val loading = desktopTrayState("夜航", playing = false, waiting = true, playWhenReady = true, hasTrack = true, queueSize = 2, closing = false)
        assertEquals("暂停", loading.playLabel)
        assertTrue(loading.canPlay)
        assertTrue(loading.canSkip)
        val closing = desktopTrayState("夜航", false, false, false, true, 2, true)
        assertFalse(closing.canPlay)
        assertFalse(closing.canSkip)
    }

    @Test fun emptyOrSingleQueueDoesNotOfferUnusableCommands() {
        val empty = desktopTrayState("", false, false, false, false, 0, false)
        assertEquals("RxPlayer", empty.tooltip)
        assertFalse(empty.canPlay)
        assertFalse(empty.canSkip)
        val single = desktopTrayState("夜航", false, false, false, false, 1, false)
        assertEquals("播放", single.playLabel)
        assertTrue(single.canPlay)
        assertFalse(single.canSkip)
    }
}
