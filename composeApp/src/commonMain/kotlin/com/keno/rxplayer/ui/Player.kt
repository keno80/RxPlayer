@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.keno.rxplayer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keno.rxplayer.DemoTrack
import com.keno.rxplayer.audio.PlaybackPhase
import com.keno.rxplayer.audio.PlaybackState

@Composable
fun MiniPlayer(
    state: PlaybackState, displayTrack: DemoTrack,
    onToggle: () -> Unit, onNext: () -> Unit, onExpand: () -> Unit, onQueue: () -> Unit,
    expandFocus: FocusRequester, queueFocus: FocusRequester,
) {
    Surface(Modifier.fillMaxWidth().padding(12.dp, 8.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onExpand, modifier = Modifier.weight(1f).focusRequester(expandFocus), contentPadding = PaddingValues(0.dp)) {
                Artwork(displayTrack.artwork, Modifier.size(48.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(displayTrack.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                    Text(phaseLabel(state.phase), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            PlaybackButton(state, onToggle, Modifier.size(56.dp, 48.dp))
            PlayerIcon(Icons.Rounded.SkipNext, "下一首", onNext)
            PlayerIcon(Icons.Rounded.QueueMusic, "播放队列", onQueue, Modifier.size(48.dp).focusRequester(queueFocus))
        }
    }
}

@Composable
fun PlayerScreen(
    state: PlaybackState, track: DemoTrack, favorite: Boolean, isDesktop: Boolean, dark: Boolean,
    queueOpen: Boolean, onQueue: () -> Unit, onCloseQueue: () -> Unit, onClose: () -> Unit,
    onFavorite: () -> Unit, onToggle: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
    onSelect: (DemoTrack) -> Unit, onRemove: (String) -> Unit,
) {
    val queueFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { backFocus.requestFocus() }
    var wasQueueOpen by remember { mutableStateOf(false) }
    LaunchedEffect(queueOpen) {
        if (!queueOpen && wasQueueOpen) queueFocus.requestFocus()
        wasQueueOpen = queueOpen
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val availableWidth = maxWidth
            val dockQueue = isDesktop && availableWidth > 1100.dp
            if (isDesktop) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(if (availableWidth < 1000.dp) 24.dp else 32.dp)) {
                    PlayerHeader(onClose, { QueueButton(state.queue.size, queueOpen, onQueue, Modifier.focusRequester(queueFocus)) }, Modifier.focusRequester(backFocus))
                    Spacer(Modifier.height(24.dp))
                    if (availableWidth > 760.dp) {
                        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (availableWidth < 1000.dp) 32.dp else 48.dp)) {
                            BoxWithConstraints(Modifier.width(if (availableWidth < 1000.dp) 300.dp else 352.dp).fillMaxHeight()) {
                                val coverSize = (maxHeight - 392.dp).coerceIn(120.dp, 280.dp)
                                Column(Modifier.fillMaxSize()) {
                                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                                        CoverCard(track, Modifier.width(coverSize).align(Alignment.CenterHorizontally))
                                        Spacer(Modifier.height(20.dp))
                                        TrackInformation(track, favorite, onFavorite)
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    SplitControls(state, onToggle, onPrevious, onNext)
                                    PlaybackModes(state, desktop = true)
                                }
                            }
                            Surface(Modifier.weight(1f).fillMaxHeight(), shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                                if (dockQueue && queueOpen) QueuePane(state, onCloseQueue, onSelect, onRemove)
                                else LyricsPane(state, track)
                            }
                        }
                    } else {
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                            Column(Modifier.widthIn(max = 352.dp).fillMaxWidth()) {
                                CoverCard(track, Modifier.width(248.dp).align(Alignment.CenterHorizontally))
                                Spacer(Modifier.height(24.dp))
                                TrackInformation(track, favorite, onFavorite)
                                Spacer(Modifier.height(24.dp))
                                SplitControls(state, onToggle, onPrevious, onNext)
                                PlaybackModes(state, desktop = true)
                            }
                            LyricsPane(state, track, Modifier.fillMaxWidth().height(480.dp))
                        }
                    }
                }
            } else if (track.lyrics.isNotEmpty()) {
                val stageHeight = (maxHeight - 260.dp).coerceAtLeast(380.dp)
                val topInset = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
                val surface = MaterialTheme.colorScheme.surface
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Box(Modifier.fillMaxWidth().heightIn(min = stageHeight)) {
                        Artwork(track.artwork, Modifier.matchParentSize().blur(2.dp), RectangleShape)
                        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .72f), Color.Black.copy(alpha = .86f)))))
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(96.dp).background(Brush.verticalGradient(listOf(Color.Transparent, surface))))
                        // The image fills the status area; foreground consumes real system insets.
                        PlayerTheme(dark = true, artwork = track.artwork) {
                            Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).padding(horizontal = 24.dp)) {
                                PlayerHeader(onClose, backModifier = Modifier.focusRequester(backFocus))
                                Box(Modifier.fillMaxWidth().heightIn(min = (stageHeight - 64.dp - topInset).coerceAtLeast(0.dp)).padding(bottom = 96.dp), contentAlignment = Alignment.Center) {
                                    LyricsLines(state, track, windowed = true)
                                }
                            }
                        }
                    }
                    Column(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)).padding(horizontal = 24.dp)) {
                        TrackInformation(track, favorite, onFavorite, compact = true) {
                            QueueButton(state.queue.size, queueOpen, onQueue, Modifier.focusRequester(queueFocus), iconOnly = true)
                        }
                        Spacer(Modifier.height(20.dp))
                        SeekBar(state)
                        Spacer(Modifier.height(20.dp))
                        ConnectedControls(state, onToggle, onPrevious, onNext)
                        Spacer(Modifier.height(24.dp))
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PlayerHeader(onClose, { QueueButton(state.queue.size, queueOpen, onQueue, Modifier.focusRequester(queueFocus)) }, Modifier.focusRequester(backFocus))
                    Spacer(Modifier.height(24.dp))
                    CoverCard(track, Modifier.widthIn(max = 280.dp).fillMaxWidth())
                    Spacer(Modifier.height(24.dp))
                    TrackInformation(track, favorite, onFavorite)
                    Spacer(Modifier.height(24.dp))
                    SplitControls(state, onToggle, onPrevious, onNext)
                    PlaybackModes(state, desktop = false)
                }
            }
            if (queueOpen && !dockQueue) QueueModal(state, isDesktop, onCloseQueue, onSelect, onRemove)
        }
    }
}

@Composable
private fun PlayerHeader(onClose: () -> Unit, trailing: (@Composable () -> Unit)? = null, backModifier: Modifier = Modifier) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        PlayerIcon(Icons.AutoMirrored.Rounded.ArrowBack, "返回", onClose, backModifier.size(48.dp), Color.Transparent)
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun CoverCard(track: DemoTrack, modifier: Modifier) {
    Surface(modifier.aspectRatio(1f), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Artwork(track.artwork, Modifier.padding(16.dp).fillMaxSize())
    }
}

@Composable
private fun TrackInformation(track: DemoTrack, favorite: Boolean, onFavorite: () -> Unit, compact: Boolean = false, extra: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            if (!compact) Text("${track.source.label} · 本地演示", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(track.title, style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            Text(if (compact) "${track.artist} · ${track.source.label} · 演示" else track.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        extra?.invoke()
        FilledIconToggleButton(
            checked = favorite, onCheckedChange = { onFavorite() }, modifier = Modifier.size(48.dp),
            shape = RoundedCornerShape(16.dp),
            colors = IconButtonDefaults.filledIconToggleButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurfaceVariant, checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer, checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer),
        ) { Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (favorite) "取消收藏 ${track.title}" else "收藏 ${track.title}") }
    }
}

@Composable
private fun SplitControls(state: PlaybackState, onToggle: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val size = if (maxWidth < 304.dp) 56.dp else 64.dp
        val gap = if (maxWidth < 304.dp) 12.dp else 16.dp
        Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().padding(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                    PlaybackButton(state, onToggle, Modifier.weight(1f).height(size))
                    PlayerIcon(Icons.Rounded.SkipNext, "下一首", onNext, Modifier.size(size), MaterialTheme.colorScheme.secondaryContainer)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                    PlayerIcon(Icons.Rounded.SkipPrevious, "上一首", onPrevious, Modifier.size(size), MaterialTheme.colorScheme.secondaryContainer)
                    SeekBar(state, Modifier.weight(1f), compact = true)
                }
            }
        }
    }
}

@Composable
private fun ConnectedControls(state: PlaybackState, onToggle: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit) {
    Surface(shape = RoundedCornerShape(48.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayerIcon(Icons.Rounded.SkipPrevious, "上一首", onPrevious, container = MaterialTheme.colorScheme.tertiaryContainer)
            ModeButton(state.shuffle, Icons.Rounded.Shuffle, "随机播放", state::toggleShuffle)
            PlaybackButton(state, onToggle, Modifier.weight(1f).height(48.dp))
            ModeButton(state.repeat, Icons.Rounded.Repeat, if (state.repeat) "列表循环" else "顺序播放", state::toggleRepeat)
            PlayerIcon(Icons.Rounded.SkipNext, "下一首", onNext, container = MaterialTheme.colorScheme.tertiaryContainer)
        }
    }
}

@Composable
private fun PlaybackModes(state: PlaybackState, desktop: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, if (desktop) Alignment.Start else Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        ModeButton(state.shuffle, Icons.Rounded.Shuffle, "随机播放", state::toggleShuffle)
        ModeButton(state.repeat, Icons.Rounded.Repeat, if (state.repeat) "列表循环" else "顺序播放", state::toggleRepeat)
        if (desktop) {
            Spacer(Modifier.weight(1f))
            Icon(Icons.Rounded.VolumeUp, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(value = state.volume, onValueChange = state::changeVolume, enabled = state.canChangeVolume, modifier = Modifier.width(88.dp).onPreviewKeyEvent { it.key == Key.Spacebar }.semantics { contentDescription = "音量" })
        }
    }
}

@Composable
private fun ModeButton(selected: Boolean, image: ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp)) {
        TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState(), modifier = Modifier.fillMaxSize()) {
            FilledIconToggleButton(checked = selected, onCheckedChange = { onClick() }, modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(if (selected) 16.dp else 48.dp), colors = IconButtonDefaults.filledIconToggleButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurfaceVariant, checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer, checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
                Icon(image, label)
            }
        }
    }
}

@Composable
internal fun PlayerIcon(image: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier.size(48.dp), container: Color = MaterialTheme.colorScheme.surfaceContainer) {
    Box(modifier) {
        TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState(), modifier = Modifier.fillMaxSize()) {
            FilledIconButton(onClick, Modifier.fillMaxSize(), shape = RoundedCornerShape(48.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = container, contentColor = contentColorFor(container))) {
                Icon(image, label)
            }
        }
    }
}

@Composable
private fun PlaybackButton(state: PlaybackState, onClick: () -> Unit, modifier: Modifier) {
    val label = when (state.phase) {
        PlaybackPhase.Preparing -> "取消准备"
        PlaybackPhase.Refreshing -> "取消获取地址"
        PlaybackPhase.Buffering -> "暂停等待"
        PlaybackPhase.Failed -> "重试播放"
        PlaybackPhase.Expired -> "重新获取播放地址"
        PlaybackPhase.Unavailable -> "当前内容不可播放，查看提示"
        else -> if (state.isPlaying) "暂停" else "播放"
    }
    val icon = when (state.phase) {
        PlaybackPhase.Preparing, PlaybackPhase.Refreshing -> Icons.Rounded.Close
        PlaybackPhase.Buffering, PlaybackPhase.Playing -> Icons.Rounded.Pause
        PlaybackPhase.Failed, PlaybackPhase.Expired -> Icons.Rounded.Refresh
        else -> Icons.Rounded.PlayArrow
    }
    val corner by animateDpAsState(if (state.isPlaying) 16.dp else 48.dp, if (LocalPlayerReducedMotion.current) snap() else MaterialTheme.motionScheme.fastSpatialSpec())
    val recovering = state.phase == PlaybackPhase.Failed || state.phase == PlaybackPhase.Expired
    // Tooltip's modifier is applied to its inner anchor in the locked Material3 version.
    // Keep Row weight and requested size on a direct layout child.
    Box(modifier) {
        TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState(), modifier = Modifier.fillMaxSize()) {
            Button(onClick, modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(corner), contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = if (recovering) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primary, contentColor = if (recovering) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary)) {
                Icon(icon, label, Modifier.size(32.dp))
            }
        }
    }
}

@Composable
private fun SeekBar(state: PlaybackState, modifier: Modifier = Modifier, compact: Boolean = false) {
    var dragging by remember(state.track?.id, state.phase) { mutableStateOf(false) }
    var dragPosition by remember(state.track?.id) { mutableFloatStateOf(0f) }
    val position = if (dragging) dragPosition else state.positionMs.toFloat()
    val known = state.durationMs > 0
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
            if (state.isWaiting) {
                val trackModifier = Modifier.fillMaxWidth().height(if (compact) 8.dp else 16.dp).semantics { contentDescription = phaseLabel(state.phase); progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate }
                if (LocalPlayerReducedMotion.current) {
                    Box(trackModifier.background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Box(Modifier.fillMaxWidth(.4f).fillMaxHeight().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)))
                    }
                } else LinearProgressIndicator(trackModifier, color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
            } else {
                Slider(
                    value = position.coerceIn(0f, state.durationMs.coerceAtLeast(1).toFloat()),
                    onValueChange = { dragging = true; dragPosition = it },
                    onValueChangeFinished = { state.seek(dragPosition.toInt()); dragging = false },
                    valueRange = 0f..state.durationMs.coerceAtLeast(1).toFloat(), enabled = state.canSeek,
                    modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { it.key == Key.Spacebar }.semantics { contentDescription = "播放进度"; stateDescription = if (known) "${formatTime(position.toInt())} / ${formatTime(state.durationMs)}" else "总时长未知" },
                    thumb = { Box(Modifier.size(4.dp, if (compact) 28.dp else 44.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))) },
                    track = { SliderDefaults.Track(it, modifier = Modifier.height(if (compact) 8.dp else 16.dp), enabled = state.canSeek) },
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (known) formatTime(position.toInt()) else "—:—", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (known) formatTime(state.durationMs) else "—:—", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LyricsPane(state: PlaybackState, track: DemoTrack, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("歌词 · 本地演示", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth().heightIn(min = 400.dp).padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
            if (track.lyrics.isEmpty()) Text("暂无歌词", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else LyricsLines(state, track, windowed = false)
        }
    }
}

@Composable
private fun LyricsLines(state: PlaybackState, track: DemoTrack, windowed: Boolean) {
    val active = if (state.durationMs > 0) track.lyrics.indexOfLast { state.positionMs >= it.atMs } else -1
    val indices = if (windowed) (active.coerceAtLeast(0) - 2..active.coerceAtLeast(0) + 2).toList() else track.lyrics.indices.toList()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (windowed) 8.dp else 12.dp)) {
        indices.forEach { index -> key(index) {
            val line = track.lyrics.getOrNull(index)
            if (line == null) Spacer(Modifier.height(56.dp)) else {
                val current = index == active
                val bringIntoView = remember { BringIntoViewRequester() }
                LaunchedEffect(current) { if (current && !windowed) bringIntoView.bringIntoView() }
                val color by animateColorAsState(if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, if (LocalPlayerReducedMotion.current) snap() else MaterialTheme.motionScheme.fastEffectsSpec())
                TextButton(
                    onClick = { state.seek(line.atMs) }, enabled = state.canSeek,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).bringIntoViewRequester(bringIntoView).semantics { if (current) stateDescription = "当前歌词" },
                    colors = ButtonDefaults.textButtonColors(contentColor = color, disabledContentColor = color),
                ) {
                    Text(line.text, fontSize = (if (windowed) if (current) 22 else 18 else if (current) 28 else 24).sp,
                        lineHeight = (if (windowed) if (current) 28 else 24 else if (current) 36 else 32).sp,
                        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center)
                }
            }
        } }
    }
}

internal fun phaseLabel(phase: PlaybackPhase): String = when (phase) {
    PlaybackPhase.Idle -> "尚未播放"
    PlaybackPhase.Preparing -> "正在准备音频"
    PlaybackPhase.Buffering -> "正在缓冲"
    PlaybackPhase.Playing -> "正在播放"
    PlaybackPhase.Paused -> "已暂停"
    PlaybackPhase.Refreshing -> "正在重新获取地址"
    PlaybackPhase.Failed -> "播放失败"
    PlaybackPhase.Unavailable -> "内容不可用"
    PlaybackPhase.Expired -> "播放地址已失效"
    PlaybackPhase.Ended -> "已停止"
}

internal fun formatTime(milliseconds: Int): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
