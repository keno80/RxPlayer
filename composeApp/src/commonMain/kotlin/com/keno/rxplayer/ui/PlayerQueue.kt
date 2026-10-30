@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.keno.rxplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.keno.rxplayer.PlaybackTrack
import com.keno.rxplayer.audio.PlaybackState

@Composable
internal fun QueueButton(count: Int, opened: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, iconOnly: Boolean = false) {
    val label = "${if (opened) "收起" else "展开"}播放队列，$count 首"
    if (iconOnly) PlayerIcon(PlayerSymbols.QueueMusic, label, onClick, modifier.size(48.dp), corner = 16.dp)
    else FilledTonalButton(onClick, modifier.heightIn(min = 48.dp).semantics { contentDescription = label }, colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface)) {
        Icon(PlayerSymbols.QueueMusic, null)
        Spacer(Modifier.width(8.dp))
        Text("队列 · $count")
    }
}

@Composable
internal fun QueuePane(state: PlaybackState, onClose: () -> Unit, onSelect: (PlaybackTrack) -> Unit, onRemove: (String) -> Unit, mobile: Boolean = false) {
    Column(Modifier.fillMaxSize().padding(start = if (mobile) 16.dp else 20.dp, top = 20.dp, end = if (mobile) 16.dp else 20.dp, bottom = if (mobile) 32.dp else 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("播放队列", style = MaterialTheme.typography.titleLarge, modifier = (if (mobile) Modifier else Modifier.weight(1f)).semantics { heading() })
            if (mobile) {
                Spacer(Modifier.width(12.dp))
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) { Text("${state.queue.size} 首", Modifier.padding(8.dp, 4.dp), fontSize = 12.sp, lineHeight = 16.sp) }
                Spacer(Modifier.weight(1f))
            } else Text("${state.queue.size} 首", style = MaterialTheme.typography.labelMedium)
            PlayerIcon(PlayerSymbols.Close, "收起播放队列", onClose, container = if (mobile) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer)
        }
        if (state.queue.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("队列为空", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (mobile) 0.dp else 8.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            itemsIndexed(state.queue, key = { _, track -> track.id }) { index, track ->
                val selected = track.id == state.track?.id
                Surface(shape = RoundedCornerShape(if (mobile) 12.dp else 16.dp), color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent) {
                    Row(Modifier.fillMaxWidth().padding(if (mobile) PaddingValues(vertical = 8.dp) else PaddingValues(4.dp)), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onSelect(track) }, modifier = Modifier.weight(1f).heightIn(min = if (mobile) 48.dp else 64.dp).semantics { this.selected = selected; contentDescription = "播放 ${track.title}" }, shape = RoundedCornerShape(if (mobile) 8.dp else 48.dp), contentPadding = PaddingValues(if (mobile) 0.dp else 4.dp)) {
                            Text("${index + 1}", modifier = Modifier.width(24.dp), style = if (mobile) MaterialTheme.typography.labelMedium else LocalTextStyle.current, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Artwork(track, Modifier.size(if (mobile) 40.dp else 44.dp), RoundedCornerShape(8.dp))
                            Column(Modifier.weight(1f).padding(start = if (mobile) 8.dp else 12.dp), verticalArrangement = Arrangement.spacedBy(if (mobile) 4.dp else 0.dp)) {
                                Text(track.title, maxLines = if (mobile) 1 else 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface, style = if (mobile) MaterialTheme.typography.titleSmall else LocalTextStyle.current)
                                Text("${track.source.label} · ${formatTime(track.durationMs)}", style = if (mobile) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { onRemove(track.id) }, modifier = Modifier.size(48.dp)) { Icon(PlayerSymbols.Close, "移除 ${track.title}") }
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
        ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), dragHandle = null, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
            BoxWithConstraints {
                Box(Modifier.fillMaxWidth().height(minOf(560.dp, maxHeight * .75f)).navigationBarsPadding()) { QueuePane(state, onClose, onSelect, onRemove, mobile = true) }
            }
        }
    }
}
