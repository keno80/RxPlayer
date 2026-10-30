@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.keno.rxplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.keno.rxplayer.PlaybackTrack
import com.keno.rxplayer.audio.PlaybackState

@Composable
internal fun QueueButton(count: Int, opened: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, iconOnly: Boolean = false) {
    val label = "${if (opened) "收起" else "展开"}播放队列，$count 首"
    if (iconOnly) PlayerIcon(Icons.Rounded.QueueMusic, label, onClick, modifier.size(48.dp))
    else FilledTonalButton(onClick, modifier.heightIn(min = 48.dp).semantics { contentDescription = label }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface)) {
        Icon(Icons.Rounded.QueueMusic, null)
        Spacer(Modifier.width(8.dp))
        Text("队列 · $count")
    }
}

@Composable
internal fun QueuePane(state: PlaybackState, onClose: () -> Unit, onSelect: (PlaybackTrack) -> Unit, onRemove: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("播放队列", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
            Text("${state.queue.size} 首", style = MaterialTheme.typography.labelMedium)
            PlayerIcon(Icons.Rounded.Close, "收起播放队列", onClose)
        }
        if (state.queue.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("队列为空", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            itemsIndexed(state.queue, key = { _, track -> track.id }) { index, track ->
                val selected = track.id == state.track?.id
                Surface(shape = RoundedCornerShape(16.dp), color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent) {
                    Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onSelect(track) }, modifier = Modifier.weight(1f).heightIn(min = 64.dp).semantics { this.selected = selected; contentDescription = "播放 ${track.title}" }, contentPadding = PaddingValues(4.dp)) {
                            Text("${index + 1}", modifier = Modifier.width(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Artwork(track, Modifier.size(44.dp), RoundedCornerShape(8.dp))
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(track.title, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
                                Text("${track.source.label} · ${formatTime(track.durationMs)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { onRemove(track.id) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.Close, "移除 ${track.title}") }
                    }
                }
            }
        }
    }
}

@Composable
internal fun QueueModal(state: PlaybackState, desktop: Boolean, onClose: () -> Unit, onSelect: (PlaybackTrack) -> Unit, onRemove: (String) -> Unit) {
    if (desktop) {
        Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .4f)).clickable(onClick = onClose).semantics { contentDescription = "关闭播放队列" })
                Surface(Modifier.align(Alignment.CenterEnd).widthIn(max = 360.dp).fillMaxWidth().fillMaxHeight().safeDrawingPadding(), shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    QueuePane(state, onClose, onSelect, onRemove)
                }
            }
        }
    } else {
        ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(.8f).navigationBarsPadding()) { QueuePane(state, onClose, onSelect, onRemove) }
        }
    }
}
