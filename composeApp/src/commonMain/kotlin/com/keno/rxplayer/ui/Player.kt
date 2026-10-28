@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.keno.rxplayer.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keno.rxplayer.DemoTrack
import com.keno.rxplayer.audio.PlaybackState

@Composable
fun MiniPlayer(
    state: PlaybackState,
    displayTrack: DemoTrack,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f).clickable(onClick = onExpand),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(displayTrack.artwork, Modifier.size(52.dp))
                    Column(Modifier.padding(horizontal = 12.dp)) {
                        Text(displayTrack.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            "RxPlayer Studio · 本地演示",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                PlaybackButton(state, onToggle, compact = true)
                IconButton(onClick = onNext, enabled = !state.isLoading) {
                    Icon(Icons.Rounded.SkipNext, "下一首")
                }
                IconButton(onClick = onExpand) {
                    Icon(Icons.Rounded.ExpandLess, "展开播放器")
                }
            }
            LinearProgressIndicator(
                progress = { if (state.durationMs == 0) 0f else state.positionMs.toFloat() / state.durationMs },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
        }
    }
}

@Composable
fun PlayerPanel(
    state: PlaybackState,
    displayTrack: DemoTrack,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("正在播放", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (onClose != null) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.ExpandMore, "收起播放器") }
            } else {
                Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Artwork(displayTrack.artwork, Modifier.widthIn(max = 300.dp).fillMaxWidth().aspectRatio(1f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(displayTrack.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("RxPlayer Studio", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconToggleButton(checked = favorite, onCheckedChange = { onFavorite() }) {
                Icon(if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "收藏当前曲目")
            }
        }
        Text(
            "原创合成音频 · 来源标签仅作界面演示",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SeekBar(state)
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious, enabled = !state.isLoading) {
                Icon(Icons.Rounded.SkipPrevious, "上一首", Modifier.size(30.dp))
            }
            PlaybackButton(state, onToggle)
            IconButton(onClick = onNext, enabled = !state.isLoading) {
                Icon(Icons.Rounded.SkipNext, "下一首", Modifier.size(30.dp))
            }
        }
        if (state.error != null) {
            Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SeekBar(state: PlaybackState) {
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    val position = if (dragging) dragPosition else state.positionMs.toFloat()
    Column {
        Slider(
            value = position,
            onValueChange = { dragging = true; dragPosition = it },
            onValueChangeFinished = { state.seek(dragPosition.toInt()); dragging = false },
            valueRange = 0f..state.durationMs.coerceAtLeast(1).toFloat(),
            enabled = state.durationMs > 0 && !state.isLoading && state.error == null,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position.toInt()), style = MaterialTheme.typography.labelMedium)
            Text(formatTime(state.durationMs), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun PlaybackButton(state: PlaybackState, onClick: () -> Unit, compact: Boolean = false) {
    Button(
        onClick = onClick,
        enabled = !state.isLoading,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.height(if (compact) 48.dp else 72.dp)
            .width(if (compact) 60.dp else 112.dp)
            .animateContentSize(MaterialTheme.motionScheme.fastSpatialSpec()),
        contentPadding = PaddingValues(0.dp),
    ) {
        if (state.isLoading) {
            LoadingIndicator(Modifier.size(32.dp), color = MaterialTheme.colorScheme.onPrimary)
        } else {
            Icon(
                if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (state.isPlaying) "暂停" else "播放",
                Modifier.size(if (compact) 28.dp else 36.dp),
            )
        }
    }
}

private fun formatTime(milliseconds: Int): String {
    val seconds = milliseconds / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
