package com.keno.rxplayer

import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
fun App(isForeground: Boolean = true, isDesktop: Boolean = false,
    onPersistenceReady: (suspend () -> Unit) -> Unit = {},
    runtime: PlaybackRuntime? = rememberPlaybackRuntime(),
    onThemeChanged: (Boolean, Boolean) -> Unit = { _, _ -> }) {
    if (runtime == null) {
        RxTheme(isSystemInDarkTheme()) { Surface(Modifier.fillMaxSize()) {} }
    } else AppContent(runtime, isForeground, isDesktop, onPersistenceReady, onThemeChanged)
}

@Composable
private fun AppContent(runtime: PlaybackRuntime, isForeground: Boolean, isDesktop: Boolean,
    onPersistenceReady: (suspend () -> Unit) -> Unit,
    onThemeChanged: (Boolean, Boolean) -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = runtime.themeOverride ?: systemDark
    val followArtwork = runtime.followArtwork
    var expanded by rememberSaveable { mutableStateOf(false) }
    var queueOpen by rememberSaveable { mutableStateOf(false) }
    var queryFocused by remember { mutableStateOf(false) }
    val favorites = runtime.favorites
    val recent = runtime.recent
    val covers = remember { ArtworkStore() }
    DisposableEffect(covers) { onDispose { covers.close() } }
    val playback = runtime.playback
    val foreground by rememberUpdatedState(isForeground)
    fun select(track: PlaybackTrack) { runtime.select(track, autoPlay = foreground) }
    val browse = runtime.browse
    val workspace = rememberSaveable(saver = WorkspaceState.Saver) { WorkspaceState() }
    val snackbar = remember { SnackbarHostState() }
    val miniExpandFocus = remember { FocusRequester() }
    val miniQueueFocus = remember { FocusRequester() }
    val track = playback.track
    val displayTrack = track ?: playback.queue.firstOrNull()
    fun toggle() { if (foreground) runtime.toggle() }
    fun step(offset: Int) { if (foreground) runtime.step(offset) }
    fun favorite(item: ContentItem) = runtime.favorite(item)
    val playlistOverlay = workspace.playlistDialog != null || runtime.playlistCandidate != null
    fun close() {
        when {
            playlistOverlay -> { workspace.playlistDialog = null; workspace.editingPlaylistId = null; runtime.cancelPlaylistCandidate() }
            queueOpen -> queueOpen = false
            else -> expanded = false
        }
    }
    PlayerBackHandler(expanded || queueOpen || browse.selection != null || playlistOverlay) { if (browse.selection != null) browse.cancelSelection() else close() }
    SideEffect { onThemeChanged(dark || expanded && !isDesktop && track?.lyrics?.isNotEmpty() == true, dark) }
    suspend fun showMessage(text: String) = coroutineScope {
        snackbar.currentSnackbarData?.dismiss()
        val showing = launch { snackbar.showSnackbar(text, duration = SnackbarDuration.Indefinite) }
        delay(3_000); snackbar.currentSnackbarData?.dismiss(); showing.join()
    }
    SideEffect { onPersistenceReady { runtime.flush(pause = isDesktop) } }
    LaunchedEffect(runtime.message?.id) { runtime.message?.let { showMessage(it.text) } }
    LaunchedEffect(playback.message?.id) { playback.message?.let { showMessage(it.text) } }
    LaunchedEffect(browse.feedback?.first) { browse.feedback?.let { showMessage(it.second) } }
    CompositionLocalProvider(LocalArtworkStore provides covers) {
        RxTheme(dark) {
            Surface(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().onKeyEvent { event ->
                    when {
                        event.type == KeyEventType.KeyDown && event.key == Key.Escape && (expanded || queueOpen || playlistOverlay) -> { close(); true }
                        event.type == KeyEventType.KeyUp && event.key == Key.Spacebar && !queryFocused && !queueOpen && !playlistOverlay && browse.selection == null && !event.isCtrlPressed && !event.isAltPressed -> { toggle(); true }
                        else -> false
                    }
                }.focusable()) {
                    if (!expanded || track == null) SourceWorkspace(
                        workspace, browse, playback, favorites, recent, isDesktop, dark, followArtwork, queueOpen,
                        { queueOpen = false }, ::select, ::favorite, { queryFocused = false; expanded = true }, { queryFocused = it },
                        { runtime.themeOverride = it }, { runtime.followArtwork = it },
                        playlists = runtime.playlists, onPlaylistPlay = { runtime.playPlaylist(it) },
                        onPlaylistAppend = runtime::appendPlaylist, onAddTrack = runtime::preparePlaylistCandidate,
                        queueContent = { PlayerTheme(dark, track, followArtwork) { Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) { DesktopQueuePane(playback, { queueOpen = false }, ::select) } } },
                        transport = {
                            if (displayTrack != null) PlayerTheme(dark, displayTrack, followArtwork) {
                                if (isDesktop) DesktopMiniPlayer(playback, displayTrack, queueOpen, ::toggle, { step(-1) }, { step(1) }, { if (track != null) expanded = true else queueOpen = true }, { queueOpen = !queueOpen }, miniExpandFocus, miniQueueFocus)
                                else MiniPlayer(playback, displayTrack, ::toggle, { if (track != null) expanded = true else queueOpen = true }, { queueOpen = !queueOpen }, miniExpandFocus, miniQueueFocus)
                            }
                        },
                    ) else PlayerTheme(dark, track, followArtwork) {
                        PlayerScreen(playback, track, (track as? BilibiliTrack)?.item?.stableKey in favorites, isDesktop, dark,
                            queueOpen, { queueOpen = !queueOpen }, { queueOpen = false }, { queueOpen = false; expanded = false },
                            { (track as? BilibiliTrack)?.item?.let(::favorite) }, ::toggle, { step(-1) }, { step(1) }, ::select, playback::removeFromQueue)
                    }
                    browse.selection?.let { PartsDialog(it, playback.track?.id, browse::choose, browse::cancelSelection, mobile = !isDesktop) }
                    PlaylistDialogs(runtime, workspace)
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(horizontal = 16.dp).padding(bottom = if (expanded) 112.dp else if (isDesktop) 88.dp else 164.dp).widthIn(max = 560.dp))
                }
            }
        }
    }
}
