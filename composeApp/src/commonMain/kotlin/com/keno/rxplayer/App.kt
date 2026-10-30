package com.keno.rxplayer

import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import com.keno.rxplayer.audio.*
import com.keno.rxplayer.source.*
import com.keno.rxplayer.ui.*
import kotlinx.coroutines.*

@Composable
fun App(isForeground: Boolean = true, isDesktop: Boolean = false, onThemeChanged: (Boolean, Boolean) -> Unit = { _, _ -> }) {
    val systemDark = isSystemInDarkTheme()
    var dark by remember { mutableStateOf(systemDark) }
    var followArtwork by remember { mutableStateOf(true) }
    var expanded by remember { mutableStateOf(false) }
    var queueOpen by remember { mutableStateOf(false) }
    var queryFocused by remember { mutableStateOf(false) }
    val favorites = remember { mutableStateMapOf<String, ContentItem>() }
    val scope = rememberCoroutineScope()
    val source = remember { BilibiliSource() }
    val covers = remember { ArtworkStore() }
    DisposableEffect(source, covers) { onDispose { source.close(); covers.close() } }
    val player = rememberAudioPlayer()
    val playback = remember(player, source) { PlaybackState(player, resolveStream = { source.resolve(it.item.id, it.part.cid) }, initialQueue = emptyList()) }
    val foreground by rememberUpdatedState(isForeground)
    fun select(track: PlaybackTrack) { scope.launch { playback.select(track, autoPlay = foreground) } }
    val browse = remember(source, playback) { BilibiliBrowseState(source, scope) { track, action ->
        when (action) {
            ContentAction.Play -> select(track)
            ContentAction.Enqueue -> playback.enqueue(track)
            ContentAction.Next -> playback.enqueue(track, nextInQueue = true)
        }
    } }
    val workspace = remember { WorkspaceState() }
    val snackbar = remember { SnackbarHostState() }
    val miniExpandFocus = remember { FocusRequester() }
    val miniQueueFocus = remember { FocusRequester() }
    val track = playback.track
    val displayTrack = track ?: playback.queue.firstOrNull()
    fun toggle() { scope.launch { if (foreground) { if (playback.track == null) playback.queue.firstOrNull()?.let { playback.select(it) } else playback.activate() } } }
    fun step(offset: Int) { scope.launch { if (foreground) playback.step(offset) } }
    fun favorite(item: ContentItem) { if (favorites.remove(item.stableKey) == null) favorites[item.stableKey] = item }
    fun close() { if (queueOpen) queueOpen = false else expanded = false }
    PlayerBackHandler(expanded || queueOpen || browse.selection != null) { if (browse.selection != null) browse.cancelSelection() else close() }
    SideEffect { onThemeChanged(dark, dark) }
    LaunchedEffect(player) {
        while (isActive) {
            playback.sync()?.let { if (foreground) select(it) }
            if (foreground && playback.phase == PlaybackPhase.Expired) scope.launch { playback.recoverExpiredIfPlaying() }
            delay(100)
        }
    }
    LaunchedEffect(isForeground) { if (!isForeground) playback.pause() }
    suspend fun showMessage(text: String) = coroutineScope {
        snackbar.currentSnackbarData?.dismiss()
        val showing = launch { snackbar.showSnackbar(text, duration = SnackbarDuration.Indefinite) }
        delay(3_000); snackbar.currentSnackbarData?.dismiss(); showing.join()
    }
    LaunchedEffect(playback.message?.id) { playback.message?.let { showMessage(it.text) } }
    LaunchedEffect(browse.feedback?.first) { browse.feedback?.let { showMessage(it.second) } }
    CompositionLocalProvider(LocalArtworkStore provides covers) {
        RxTheme(dark) {
            Surface(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().onKeyEvent { event ->
                    when {
                        event.type == KeyEventType.KeyDown && event.key == Key.Escape && (expanded || queueOpen) -> { close(); true }
                        event.type == KeyEventType.KeyUp && event.key == Key.Spacebar && !queryFocused && !queueOpen && browse.selection == null && !event.isCtrlPressed && !event.isAltPressed -> { toggle(); true }
                        else -> false
                    }
                }.focusable()) {
                    if (!expanded || track == null) SourceWorkspace(
                        workspace, browse, playback, favorites, isDesktop, dark, followArtwork, queueOpen,
                        { queueOpen = false }, ::select, ::favorite, { queryFocused = false; expanded = true }, { queryFocused = it },
                        { dark = it }, { followArtwork = it },
                        queueContent = { PlayerTheme(dark, track, followArtwork) { Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) { DesktopQueuePane(playback, { queueOpen = false }, ::select) } } },
                        transport = {
                            if (displayTrack != null) PlayerTheme(dark, displayTrack, followArtwork) {
                                if (isDesktop) DesktopMiniPlayer(playback, displayTrack, queueOpen, ::toggle, { step(-1) }, { step(1) }, { if (track != null) expanded = true else queueOpen = true }, { queueOpen = !queueOpen }, miniExpandFocus, miniQueueFocus)
                                else MiniPlayer(playback, displayTrack, ::toggle, { step(1) }, { if (track != null) expanded = true else queueOpen = true }, { queueOpen = !queueOpen }, miniExpandFocus, miniQueueFocus)
                            }
                        },
                    ) else PlayerTheme(dark, track, followArtwork) {
                        PlayerScreen(playback, track, (track as? BilibiliTrack)?.item?.stableKey in favorites, isDesktop, dark,
                            queueOpen, { queueOpen = !queueOpen }, { queueOpen = false }, { queueOpen = false; expanded = false },
                            { (track as? BilibiliTrack)?.item?.let(::favorite) }, ::toggle, { step(-1) }, { step(1) }, ::select, playback::removeFromQueue)
                    }
                    browse.selection?.let { PartsDialog(it, playback.track?.id, browse::choose, browse::cancelSelection) }
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(horizontal = 16.dp).padding(bottom = if (expanded) 112.dp else if (isDesktop) 88.dp else 164.dp).widthIn(max = 560.dp))
                }
            }
        }
    }
}
