@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.keno.rxplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.snap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.keno.rxplayer.PlaybackTrack
import com.keno.rxplayer.audio.PlaybackState
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    val order = state.queueInPlaybackOrder
    val ids = order.map { it.id }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val gripSize = with(density) { 48.dp.toPx() }
    val endInset = with(density) { (if (mobile) 0.dp else 4.dp).toPx() }
    val edge = with(density) { 32.dp.toPx() }
    val scrollStep = with(density) { 8.dp.toPx() }
    var moveId by remember(state) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val handleFocus = remember(state) { mutableMapOf<String, FocusRequester>() }
    var draggedId by remember(state) { mutableStateOf<String?>(null) }
    var preview by remember(state) { mutableStateOf<List<PlaybackTrack>?>(null) }
    var originalIds by remember { mutableStateOf(emptyList<String>()) }
    var pointer by remember { mutableStateOf(Offset.Zero) }
    var grabOffset by remember { mutableFloatStateOf(0f) }
    var itemHeight by remember { mutableFloatStateOf(0f) }
    val placement = if (LocalPlayerReducedMotion.current) snap<IntOffset>() else MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()

    fun updatePreview(direction: Float) {
        val id = draggedId ?: return
        val items = preview ?: return
        val from = items.indexOfFirst { it.id == id }
        if (from < 0) return
        val center = pointer.y - grabOffset + itemHeight / 2
        val candidates = listState.layoutInfo.visibleItemsInfo.map { info -> info to items.indexOfFirst { it.id == info.key } }
        val target = if (direction > 0) candidates.filter { (info, index) -> index > from && center > info.offset + info.size / 2 }.maxByOrNull { it.second }
            else if (direction < 0) candidates.filter { (info, index) -> index in 0 until from && center < info.offset + info.size / 2 }.minByOrNull { it.second }
            else null
        target?.second?.let { to -> preview = items.toMutableList().apply { add(to, removeAt(from)) } }
    }

    LaunchedEffect(ids) {
        handleFocus.keys.retainAll(ids.toSet())
        if (moveId !in ids) moveId = null
        if (draggedId != null && ids != originalIds) { draggedId = null; preview = null }
    }
    LaunchedEffect(draggedId) {
        while (isActive && draggedId != null) {
            val layout = listState.layoutInfo
            val delta = when {
                pointer.y < layout.viewportStartOffset + edge -> -scrollStep
                pointer.y > layout.viewportEndOffset - edge -> scrollStep
                else -> 0f
            }
            if (delta != 0f) { listState.scrollBy(delta); updatePreview(delta) }
            withFrameNanos { }
        }
    }

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
        val moveIndex = order.indexOfFirst { it.id == moveId }
        if (moveIndex >= 0 && order.size > 1) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("移动第 ${moveIndex + 1} 首", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                    IconButton(onClick = { if (state.moveQueue(order[moveIndex].id, moveIndex - 1)) scope.launch { listState.animateScrollToItem(moveIndex - 1) } }, enabled = moveIndex > 0, modifier = Modifier.size(48.dp)) {
                        Icon(PlayerSymbols.ArrowBack, "将选中曲目上移", Modifier.rotate(90f))
                    }
                    IconButton(onClick = { if (state.moveQueue(order[moveIndex].id, moveIndex + 1)) scope.launch { listState.animateScrollToItem(moveIndex + 1) } }, enabled = moveIndex < order.lastIndex, modifier = Modifier.size(48.dp)) {
                        Icon(PlayerSymbols.ArrowBack, "将选中曲目下移", Modifier.rotate(-90f))
                    }
                    IconButton(onClick = {
                        val id = moveId
                        moveId = null
                        scope.launch {
                            val index = state.queueInPlaybackOrder.indexOfFirst { it.id == id }
                            if (index >= 0) {
                                listState.scrollToItem(index)
                                withFrameNanos { }
                                if (listState.layoutInfo.visibleItemsInfo.any { it.key == id }) handleFocus[id]?.requestFocus()
                            }
                        }
                    }, modifier = Modifier.size(48.dp)) { Icon(PlayerSymbols.Close, "收起移动操作") }
                }
            }
        }
        if (state.queue.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("队列为空", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else LazyColumn(Modifier.weight(1f).fillMaxWidth().pointerInput(state, density, mobile) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (state.queue.size < 2 || down.position.x < size.width - gripSize - endInset || down.position.x > size.width - endInset) return@awaitEachGesture
                if (down.type == PointerType.Mouse && !currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture
                val info = listState.layoutInfo.visibleItemsInfo.firstOrNull { down.position.y >= it.offset && down.position.y < it.offset + it.size } ?: return@awaitEachGesture
                val id = info.key as? String ?: return@awaitEachGesture
                var active = false
                var released = false
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.isConsumed) break
                        if (!change.pressed) { pointer = change.position; released = event.type == PointerEventType.Release; break }
                        if (!active && (change.position - down.position).getDistance() >= viewConfiguration.touchSlop) {
                            val actual = state.queueInPlaybackOrder
                            if (actual.none { it.id == id }) break
                            originalIds = actual.map { it.id }; preview = actual
                            draggedId = id; moveId = null; active = true
                            grabOffset = down.position.y - info.offset; itemHeight = info.size.toFloat()
                            pointer = down.position
                        }
                        if (active && draggedId == id) {
                            val delta = change.position.y - pointer.y
                            pointer = change.position; change.consume(); updatePreview(delta)
                        }
                    }
                    val inside = pointer.x in 0f..size.width.toFloat() && pointer.y in -edge..(size.height + edge)
                    if (active && released && inside && draggedId == id && state.queueInPlaybackOrder.map { it.id } == originalIds) {
                        preview?.indexOfFirst { it.id == id }?.let { state.moveQueue(id, it) }
                    }
                } finally { draggedId = null; preview = null }
            }
        }, state = listState, verticalArrangement = Arrangement.spacedBy(if (mobile) 0.dp else 8.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            itemsIndexed(preview ?: order, key = { _, track -> track.id }) { index, track ->
                val selected = track.id == state.track?.id
                val dragging = track.id == draggedId
                val focus = handleFocus.getOrPut(track.id) { FocusRequester() }
                Surface(modifier = Modifier.animateItem(fadeInSpec = null, placementSpec = if (dragging) null else placement, fadeOutSpec = null).zIndex(if (dragging) 1f else 0f).graphicsLayer {
                    translationY = if (dragging) pointer.y - grabOffset - (listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == track.id }?.offset ?: 0) else 0f
                }, shape = RoundedCornerShape(if (mobile) 12.dp else 16.dp), color = if (selected) MaterialTheme.colorScheme.secondaryContainer else if (dragging) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                    shadowElevation = if (dragging) 4.dp else 0.dp, border = if (moveId == track.id) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null) {
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
                        IconButton(onClick = { moveId = if (moveId == track.id) null else track.id }, enabled = order.size > 1,
                            modifier = Modifier.size(48.dp).focusRequester(focus).onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key in listOf(Key.DirectionUp, Key.DirectionDown)) {
                                    val from = state.queueInPlaybackOrder.indexOfFirst { it.id == track.id }
                                    state.moveQueue(track.id, from + if (event.key == Key.DirectionUp) -1 else 1); true
                                } else false
                            }.semantics {
                                stateDescription = "第 ${index + 1} 首，共 ${order.size} 首"
                                customActions = buildList {
                                    if (index > 0) add(CustomAccessibilityAction("上移") { state.moveQueue(track.id, index - 1) })
                                    if (index < order.lastIndex) add(CustomAccessibilityAction("下移") { state.moveQueue(track.id, index + 1) })
                                }
                            }) { Icon(QueueDragHandle, "拖动 ${track.title} 调整顺序，点按显示移动操作") }
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
