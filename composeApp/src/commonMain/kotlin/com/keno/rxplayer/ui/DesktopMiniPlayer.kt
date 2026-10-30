@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.keno.rxplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.keno.rxplayer.DemoTrack
import com.keno.rxplayer.audio.PlaybackState
import kotlin.math.roundToInt

@Composable
internal fun DesktopMiniPlayer(
    state: PlaybackState, track: DemoTrack, queueOpen: Boolean,
    onToggle: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
    onExpand: () -> Unit, onQueue: () -> Unit,
    expandFocus: FocusRequester, queueFocus: FocusRequester,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = maxWidth
        if (width >= 904.dp) {
            Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TrackIsland(track, onExpand, expandFocus, Modifier.weight(1f))
                ProgressIsland(state, Modifier.weight(1.6f))
                ControlIsland(state, queueOpen, onToggle, onPrevious, onNext, onQueue, queueFocus)
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(12.dp, 4.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (width >= 620.dp) {
                    Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TrackIsland(track, onExpand, expandFocus, Modifier.weight(1f), compact = true)
                        ControlIsland(state, queueOpen, onToggle, onPrevious, onNext, onQueue, queueFocus, compact = true)
                    }
                    ProgressIsland(state, Modifier.fillMaxWidth(), compact = true)
                } else {
                    TrackIsland(track, onExpand, expandFocus, Modifier.fillMaxWidth(), compact = true)
                    ProgressIsland(state, Modifier.fillMaxWidth(), compact = true)
                    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        ControlIsland(state, queueOpen, onToggle, onPrevious, onNext, onQueue, queueFocus, compact = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackIsland(track: DemoTrack, onExpand: () -> Unit, focus: FocusRequester, modifier: Modifier, compact: Boolean = false) {
    Surface(modifier.height(if (compact) 48.dp else 72.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        TextButton(onExpand, Modifier.fillMaxSize().focusRequester(focus), contentPadding = PaddingValues(if (compact) 4.dp else 12.dp)) {
            Artwork(track.artwork, Modifier.size(if (compact) 40.dp else 48.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProgressIsland(state: PlaybackState, modifier: Modifier, compact: Boolean = false) {
    var dragging by remember(state.track?.id, state.phase) { mutableStateOf(false) }
    var dragPosition by remember(state.track?.id) { mutableFloatStateOf(0f) }
    val position = if (dragging) dragPosition else state.positionMs.toFloat()
    val known = state.durationMs > 0
    Surface(modifier.height(if (compact) 48.dp else 72.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (known) formatTime(position.toInt()) else "—:—", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.weight(1f).height(48.dp), contentAlignment = Alignment.Center) {
                if (state.isWaiting) {
                    val waitingTrack = Modifier.fillMaxWidth().height(6.dp).semantics {
                        contentDescription = phaseLabel(state.phase)
                        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                    }
                    if (LocalPlayerReducedMotion.current) {
                        Box(waitingTrack.background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape)) {
                            Box(Modifier.fillMaxWidth(.4f).fillMaxHeight().background(MaterialTheme.colorScheme.primary, CircleShape))
                        }
                    } else LinearProgressIndicator(waitingTrack, color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                } else Slider(
                    value = position.coerceIn(0f, state.durationMs.coerceAtLeast(1).toFloat()),
                    onValueChange = { dragging = true; dragPosition = it },
                    onValueChangeFinished = { state.seek(dragPosition.toInt()); dragging = false },
                    valueRange = 0f..state.durationMs.coerceAtLeast(1).toFloat(), enabled = state.canSeek,
                    modifier = Modifier.fillMaxWidth().onPreviewKeyEvent { it.key == Key.Spacebar }.semantics { contentDescription = "播放进度"; stateDescription = if (known) "${formatTime(position.toInt())} / ${formatTime(state.durationMs)}" else "总时长未知" },
                    thumb = { Box(Modifier.size(4.dp, 24.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) },
                    track = { SliderDefaults.Track(it, Modifier.height(6.dp), enabled = state.canSeek) },
                )
            }
            Text(if (known) formatTime(state.durationMs) else "—:—", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ControlIsland(
    state: PlaybackState, queueOpen: Boolean, onToggle: () -> Unit, onPrevious: () -> Unit,
    onNext: () -> Unit, onQueue: () -> Unit, queueFocus: FocusRequester, compact: Boolean = false,
) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.height(if (compact) 56.dp else 72.dp).padding(if (compact) 4.dp else 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            ModeButton(state.shuffle, Icons.Rounded.Shuffle, "随机播放", state::toggleShuffle, Color.Transparent)
            PlayerIcon(Icons.Rounded.SkipPrevious, "上一首", onPrevious, container = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            PlaybackButton(state, onToggle, Modifier.size(if (compact) 56.dp else 64.dp, if (compact) 48.dp else 56.dp))
            PlayerIcon(Icons.Rounded.SkipNext, "下一首", onNext, container = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ModeButton(state.repeat, Icons.Rounded.Repeat, if (state.repeat) "列表循环" else "顺序播放", state::toggleRepeat, Color.Transparent)
            VerticalDivider(Modifier.height(32.dp).padding(horizontal = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
            VolumeControl(state)
            PlayerIcon(Icons.Rounded.QueueMusic, "播放队列，${state.queue.size} 首", onQueue,
                Modifier.size(48.dp).focusRequester(queueFocus).semantics { stateDescription = if (queueOpen) "已展开" else "已收起" },
                Color.Transparent, if (queueOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VolumeControl(state: PlaybackState) {
    var volumeOpen by remember { mutableStateOf(false) }
    var restoreFocus by remember { mutableStateOf(false) }
    val buttonFocus = remember { FocusRequester() }
    val sliderFocus = remember { FocusRequester() }
    val offset = with(LocalDensity.current) { IntOffset(0, -60.dp.roundToPx()) }
    LaunchedEffect(volumeOpen) { if (!volumeOpen && restoreFocus) { buttonFocus.requestFocus(); restoreFocus = false } }
    Box {
        PlayerIcon(if (state.volume == 0f) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, "音量", { volumeOpen = !volumeOpen },
            Modifier.size(48.dp).focusRequester(buttonFocus).semantics { stateDescription = "${(state.volume * 100).roundToInt()}%，${if (volumeOpen) "已展开" else "已收起"}" }, Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant)
        if (volumeOpen) {
            Popup(alignment = Alignment.BottomCenter, offset = offset, onDismissRequest = { volumeOpen = false }, properties = PopupProperties(focusable = true)) {
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 4.dp,
                    modifier = Modifier.onPreviewKeyEvent {
                        if (it.key == Key.Escape) { restoreFocus = true; volumeOpen = false; true } else false
                    }) {
                    Column(Modifier.width(64.dp).padding(8.dp, 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(48.dp, 128.dp), contentAlignment = Alignment.Center) {
                            Slider(state.volume, state::changeVolume, enabled = state.canChangeVolume,
                                modifier = Modifier.requiredSize(128.dp, 48.dp).rotate(-90f).focusRequester(sliderFocus).onPreviewKeyEvent {
                                    when (it.key) {
                                        Key.Spacebar -> true
                                        Key.DirectionUp, Key.DirectionDown -> {
                                            if (it.type == KeyEventType.KeyDown && state.canChangeVolume) state.changeVolume(state.volume + if (it.key == Key.DirectionUp) .01f else -.01f)
                                            true
                                        }
                                        else -> false
                                    }
                                }.semantics { contentDescription = "调整音量" },
                                thumb = { Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.primary, CircleShape)) },
                                track = { SliderDefaults.Track(it, Modifier.height(4.dp), enabled = state.canChangeVolume) },
                            )
                        }
                        Text("${(state.volume * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                LaunchedEffect(Unit) { if (state.canChangeVolume) sliderFocus.requestFocus() }
            }
        }
    }
}
