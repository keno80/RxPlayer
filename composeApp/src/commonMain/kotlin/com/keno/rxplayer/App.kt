@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.keno.rxplayer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keno.rxplayer.audio.PlaybackState
import com.keno.rxplayer.audio.rememberAudioPlayer
import com.keno.rxplayer.ui.Artwork
import com.keno.rxplayer.ui.MiniPlayer
import com.keno.rxplayer.ui.PlayerPanel
import com.keno.rxplayer.ui.RxTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun App(isForeground: Boolean = true, onThemeChanged: (Boolean) -> Unit = {}) {
    val systemDark = isSystemInDarkTheme()
    var dark by remember { mutableStateOf(systemDark) }
    var followArtwork by remember { mutableStateOf(true) }
    var library by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf<MusicSource?>(null) }
    var query by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf(setOf<String>()) }
    var expandedPlayer by remember { mutableStateOf(false) }
    var selectionJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val player = rememberAudioPlayer()
    val playback = remember(player) { PlaybackState(player) }
    val currentTrack = playback.track ?: demoTracks.first()

    SideEffect { onThemeChanged(dark) }

    fun select(track: DemoTrack) {
        selectionJob?.cancel()
        selectionJob = scope.launch { playback.select(track) }
    }

    fun toggle() {
        if (playback.track == null || playback.durationMs == 0 || playback.error != null) {
            select(currentTrack)
        } else {
            playback.toggle()
        }
    }

    fun step(offset: Int) {
        val index = demoTracks.indexOf(currentTrack)
        select(demoTracks[(index + offset + demoTracks.size) % demoTracks.size])
    }

    fun toggleFavorite(track: DemoTrack) {
        favorites = if (track.id in favorites) favorites - track.id else favorites + track.id
    }

    LaunchedEffect(player) {
        while (isActive) {
            playback.sync()
            delay(100)
        }
    }
    LaunchedEffect(isForeground) {
        if (!isForeground) {
            selectionJob?.cancelAndJoin()
            playback.pause()
        }
    }

    RxTheme(dark, if (followArtwork) currentTrack.artwork else 0) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val desktop = maxWidth >= 720.dp
                val showPanel = maxWidth >= 1100.dp
                Row(Modifier.fillMaxSize()) {
                    if (desktop) {
                        NavigationRail(
                            modifier = Modifier.fillMaxHeight().width(100.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            header = {
                                Text(
                                    "Rx", modifier = Modifier.padding(vertical = 24.dp),
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary,
                                )
                            },
                        ) {
                            NavigationRailItem(
                                selected = !library, onClick = { library = false },
                                icon = { Icon(Icons.Rounded.Explore, null) }, label = { Text("发现") },
                            )
                            Spacer(Modifier.height(12.dp))
                            NavigationRailItem(
                                selected = library, onClick = { library = true },
                                icon = { Icon(Icons.Rounded.LibraryMusic, null) }, label = { Text("音乐库") },
                            )
                        }
                    }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        Header(dark, onThemeChange = { dark = !dark })
                        val visibleTracks = demoTracks.filter { track ->
                            (source == null || source == track.source) &&
                                (!library || track.id in favorites) &&
                                (query.isBlank() || "${track.title} ${track.subtitle} ${track.source.label} RxPlayer Studio"
                                    .contains(query.trim(), ignoreCase = true))
                        }
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = if (desktop) 28.dp else 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            item {
                                Hero(library, currentTrack, onPlay = { select(currentTrack) })
                            }
                            item {
                                OutlinedTextField(
                                    value = query, onValueChange = { query = it },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                                    label = { Text("搜索演示曲目") },
                                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                                    trailingIcon = {
                                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                                            Icon(Icons.Rounded.Close, "清空搜索")
                                        }
                                    },
                                    shape = RoundedCornerShape(24.dp),
                                )
                            }
                            item { SourceSelector(source, onChange = { source = it }) }
                            item {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (library) "我的收藏" else "此刻，听点什么",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text("${visibleTracks.size} 首", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (visibleTracks.isEmpty()) item {
                                EmptyLibrary(library, onReset = { query = ""; source = null; library = false })
                            }
                            items(visibleTracks, key = { it.id }) { track ->
                                TrackRow(
                                    track, selected = playback.track?.id == track.id,
                                    playing = playback.track?.id == track.id && playback.isPlaying,
                                    favorite = track.id in favorites,
                                    onPlay = { select(track) }, onFavorite = { toggleFavorite(track) },
                                )
                            }
                            item {
                                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text("跟随曲目配色", style = MaterialTheme.typography.titleSmall)
                                            Text("切换曲目，换一种心情", style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Switch(checked = followArtwork, onCheckedChange = { followArtwork = it })
                                    }
                                }
                            }
                            item {
                                Text(
                                    "界面验证版 · 3 首原创本地音频\n平台标签为演示分类，在线搜索与账号尚未接入。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 12.dp),
                                )
                            }
                        }
                        AnimatedVisibility(playback.error != null) {
                            Text(
                                playback.error.orEmpty(), color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            )
                        }
                        if (!showPanel) {
                            MiniPlayer(playback, currentTrack, ::toggle, { step(1) }, { expandedPlayer = true })
                        }
                        if (!desktop) {
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, windowInsets = WindowInsets(0, 0, 0, 0)) {
                                NavigationBarItem(
                                    selected = !library, onClick = { library = false },
                                    icon = { Icon(Icons.Rounded.Explore, null) }, label = { Text("发现") },
                                )
                                NavigationBarItem(
                                    selected = library, onClick = { library = true },
                                    icon = { Icon(Icons.Rounded.LibraryMusic, null) }, label = { Text("音乐库") },
                                )
                            }
                        }
                    }
                    if (showPanel) {
                        Surface(
                            modifier = Modifier.width(336.dp).fillMaxHeight().padding(16.dp, 12.dp, 16.dp, 16.dp),
                            shape = RoundedCornerShape(32.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                        ) {
                            PlayerPanel(
                                playback, currentTrack, currentTrack.id in favorites,
                                { toggleFavorite(currentTrack) }, ::toggle, { step(-1) }, { step(1) },
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
                if (expandedPlayer && !showPanel) {
                    ModalBottomSheet(
                        onDismissRequest = { expandedPlayer = false },
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        PlayerPanel(
                            playback, currentTrack, currentTrack.id in favorites,
                            { toggleFavorite(currentTrack) }, ::toggle, { step(-1) }, { step(1) },
                            onClose = { expandedPlayer = false },
                            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(dark: Boolean, onThemeChange: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("RxPlayer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text("YOUR SOUND, YOUR SPACE", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onThemeChange) {
            Icon(if (dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                if (dark) "切换浅色主题" else "切换深色主题")
        }
    }
}

@Composable
private fun Hero(library: Boolean, track: DemoTrack, onPlay: () -> Unit) {
    Surface(shape = RoundedCornerShape(36.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(24.dp)) {
            val roomy = maxWidth >= 480.dp
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (library) "留住喜欢的声音" else "今天也有好声音", style = MaterialTheme.typography.labelLarge)
                    AnimatedContent(library) { inLibrary ->
                        Text(
                            if (inLibrary) "喜欢的，\n值得再听。" else "让声音，\n自由发生。",
                            style = if (roomy) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text("从一段旋律，开始今天。", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onPlay, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("听听 ${track.title}")
                    }
                }
                if (roomy) Artwork(track.artwork, Modifier.size(156.dp))
            }
        }
    }
}

@Composable
private fun SourceSelector(selected: MusicSource?, onChange: (MusicSource?) -> Unit) {
    val sources = listOf(null) + MusicSource.entries
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        sources.forEachIndexed { index, source ->
            ToggleButton(
                checked = selected == source, onCheckedChange = { onChange(source) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    sources.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(source?.label ?: "全部", style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: DemoTrack, selected: Boolean, playing: Boolean, favorite: Boolean,
    onPlay: () -> Unit, onFavorite: () -> Unit,
) {
    Surface(
        onClick = onPlay, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (selected) 28.dp else 20.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(track.artwork, Modifier.size(60.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(track.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(track.subtitle, style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${track.source.label} · 演示分类", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            if (playing) Icon(Icons.Rounded.GraphicEq, "正在播放", tint = MaterialTheme.colorScheme.primary)
            IconToggleButton(checked = favorite, onCheckedChange = { onFavorite() }) {
                Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "收藏 ${track.title}")
            }
        }
    }
}

@Composable
private fun EmptyLibrary(library: Boolean, onReset: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.Headphones, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Text(if (library) "还没有匹配的收藏" else "没有找到匹配的曲目", style = MaterialTheme.typography.titleMedium)
        Text("试试其他关键词，或者发现新的声音。", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onReset) { Text("查看全部演示曲目") }
    }
}
