@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.keno.rxplayer.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.keno.rxplayer.*
import com.keno.rxplayer.audio.PlaybackState
import com.keno.rxplayer.source.*

internal enum class WorkspaceDestination(val label: String, val icon: ImageVector) {
    Search("搜索", Icons.Outlined.Search), Library("音乐库", Icons.Outlined.LibraryMusic), Settings("设置", Icons.Outlined.Settings),
}
internal class WorkspaceState {
    var destination by mutableStateOf(WorkspaceDestination.Search)
    val searchScroll = LazyListState()
    val libraryScroll = LazyListState()
    val settingsScroll = LazyListState()
}

/** Shared information architecture; Windows keeps the approved rail and three floating islands. */
@Composable
internal fun SourceWorkspace(
    workspace: WorkspaceState, browse: BilibiliBrowseState, playback: PlaybackState, favorites: Map<String, ContentItem>,
    desktop: Boolean, dark: Boolean, followArtwork: Boolean, queueOpen: Boolean, onQueueClose: () -> Unit,
    onSelect: (PlaybackTrack) -> Unit, onFavorite: (ContentItem) -> Unit, onExpand: () -> Unit,
    onQueryFocus: (Boolean) -> Unit, onDarkChange: (Boolean) -> Unit, onArtworkChange: (Boolean) -> Unit,
    queueContent: @Composable () -> Unit, transport: @Composable () -> Unit,
) {
    val destination = workspace.destination
    val list = when(destination) { WorkspaceDestination.Search -> workspace.searchScroll; WorkspaceDestination.Library -> workspace.libraryScroll; WorkspaceDestination.Settings -> workspace.settingsScroll }
    LaunchedEffect(list, browse.results.size, destination) {
        if (destination == WorkspaceDestination.Search) snapshotFlow { list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { last ->
            if (last >= list.layoutInfo.totalItemsCount - 3 && browse.results.isNotEmpty()) browse.more()
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().then(if (desktop) Modifier else Modifier.safeDrawingPadding())) {
        val narrow = maxWidth <= 700.dp
        val dockQueue = desktop && queueOpen && maxWidth > 1100.dp
        Row(Modifier.fillMaxSize()) {
            if (desktop) Surface(Modifier.width(if (narrow) 72.dp else 96.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Rx.", color = MaterialTheme.colorScheme.primary, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 20.dp))
                    WorkspaceDestination.entries.forEach { item ->
                        val active = item == destination
                        TextButton({ workspace.destination = item; onQueryFocus(false) }, Modifier.fillMaxWidth().height(72.dp).semantics { selected = active }, contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(16.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(if (narrow) 48.dp else 64.dp, 36.dp).background(if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                                    Icon(item.icon, null, tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(item.label, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(Modifier.weight(1f).fillMaxHeight(), state = list, contentPadding = PaddingValues(horizontal = if (narrow) 20.dp else 32.dp, vertical = if (narrow) 20.dp else 28.dp)) {
                        item { PageHead(destination.label, if (destination == WorkspaceDestination.Search) "歌曲、视频，从这里开始听。" else null, playback.track?.let { onExpand }) }
                        when(destination) {
                            WorkspaceDestination.Search -> {
                                item {
                                    SearchField(browse.query, { browse.query = it }, { browse.submit() }, onQueryFocus)
                                    Spacer(Modifier.height(16.dp))
                                    Row(Modifier.widthIn(max = 480.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        listOf(MusicSource.Bilibili, MusicSource.Netease, MusicSource.YouTube).forEachIndexed { index, item ->
                                            val active = item == MusicSource.Bilibili
                                            Surface(onClick = {}, enabled = active, modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { selected = active; role = Role.RadioButton },
                                                shape = if (active) RoundedCornerShape(24.dp) else RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = if (index == 2) 24.dp else 8.dp, bottomEnd = if (index == 2) 24.dp else 8.dp),
                                                color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (active) 1f else .38f)) {
                                                Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                                                    if (active) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp))
                                                    Text(item.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                                }
                                            }
                                        }
                                    }
                                    SectionHead("搜索结果", if (browse.results.isEmpty()) null else "${browse.results.size} 个视频")
                                    if (browse.readingDetails || browse.searching) LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp))
                                }
                                when {
                                    browse.error != null -> item { EmptyContent("暂时没有找到声音", browse.error!!) { browse.submit() } }
                                    browse.submitted.isEmpty() -> item { EmptyContent("想听点什么？", "搜索关键词，或粘贴 Bilibili 视频链接、BV/AV 号。") }
                                    browse.results.isEmpty() && !browse.searching -> item { EmptyContent("没有匹配的结果", "换个关键词，再发现一些好声音。") }
                                    browse.results.isNotEmpty() -> {
                                        item { FeaturedContent(browse.results.first(), narrow, browse.results.first().stableKey in favorites, browse.selection,
                                            { browse.request(browse.results.first(), it) }, { onFavorite(browse.results.first()) }) }
                                        if (browse.results.size > 1) item { SectionHead("更多匹配") }
                                        itemsIndexed(browse.results.drop(1), key = { _, item -> item.stableKey }) { index, item ->
                                            ContentRow(item, playback, item.stableKey in favorites, index == 0, index == browse.results.size - 2, narrow,
                                                browse.partCounts[item.id], browse.selection, { action -> browse.request(item, action) }, { onFavorite(item) })
                                        }
                                        item { Text("来自 Bilibili · 保留原标题与版本信息", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp)) }
                                    }
                                }
                            }
                            WorkspaceDestination.Library -> {
                                item { SectionHead("喜欢的声音", "${favorites.size} 个视频") }
                                if (favorites.isEmpty()) item { EmptyContent("为喜欢的声音留个位置", "收藏的视频会出现在这里。") }
                                itemsIndexed(favorites.values.toList(), key = { _, item -> item.stableKey }) { index, item ->
                                    ContentRow(item, playback, true, index == 0, index == favorites.size - 1, narrow, browse.partCounts[item.id], browse.selection, { browse.request(item, it) }, { onFavorite(item) })
                                }
                            }
                            WorkspaceDestination.Settings -> {
                                item {
                                    SectionHead("外观")
                                    SettingRow("深色主题", "选择舒服的明暗") { Switch(dark, onDarkChange) }
                                    Spacer(Modifier.height(2.dp))
                                    SettingRow("封面舞台底色", "从当前封面提取播放配色") { Switch(followArtwork, onArtworkChange) }
                                    SectionHead("音源账号", "尚未接入")
                                    listOf(MusicSource.Bilibili, MusicSource.Netease, MusicSource.YouTube).forEach { item -> SettingRow(item.label, "未连接 · 账号接入尚未实现") { Text("未连接", style = MaterialTheme.typography.labelMedium) }; Spacer(Modifier.height(2.dp)) }
                                }
                            }
                        }
                    }
                    if (dockQueue) { VerticalDivider(); Box(Modifier.width(320.dp).fillMaxHeight()) { queueContent() } }
                }
                transport()
                if (!desktop) NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
                    WorkspaceDestination.entries.forEach { item -> NavigationBarItem(destination == item, { workspace.destination = item; onQueryFocus(false) }, { Icon(item.icon, null) }, label = { Text(item.label) }) }
                }
            }
        }
        if (queueOpen && !dockQueue) QueueModal(playback, desktop, onQueueClose, onSelect, playback::removeFromQueue)
    }
}

@Composable private fun PageHead(title: String, intro: String?, expand: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("RxPlayer", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            Text(title, fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Medium, modifier = Modifier.semantics { heading() })
            if (intro != null) Text(intro, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
        if (expand != null) IconButton(expand, Modifier.size(48.dp)) { Icon(Icons.Outlined.Headphones, "展开播放器") }
    }
}
@Composable private fun SearchField(query: String, change: (String) -> Unit, submit: () -> Unit, focus: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, null)
            BasicTextField(query, change, Modifier.weight(1f).padding(start = 8.dp).heightIn(min = 48.dp).onFocusChanged { focus(it.isFocused) }.semantics { contentDescription = "搜索歌曲、视频或内容链接" },
                singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { submit() }), decorationBox = { field -> Box(contentAlignment = Alignment.CenterStart) { if (query.isEmpty()) Text("歌曲、视频，或粘贴链接", color = MaterialTheme.colorScheme.onSurfaceVariant); field() } })
            if (query.isNotEmpty()) IconButton({ change("") }, Modifier.size(48.dp)) { Icon(Icons.Outlined.Close, "清空搜索") }
            IconButton(submit, Modifier.size(48.dp)) { Icon(Icons.Outlined.Search, "执行搜索") }
        }
    }
}
@Composable private fun SectionHead(title: String, detail: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).semantics { heading() })
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable private fun FeaturedContent(item: ContentItem, narrow: Boolean, favorite: Boolean, selection: PartSelection?, action: (ContentAction) -> Unit, toggleFavorite: () -> Unit) {
    val focus = remember { FocusRequester() }
    var restore by remember { mutableStateOf(false) }
    var sawSelection by remember { mutableStateOf(false) }
    LaunchedEffect(selection) { if (restore && selection?.details?.item?.id == item.id) sawSelection = true; if (restore && sawSelection && selection == null) { focus.requestFocus(); restore = false; sawSelection = false } }
    fun request(next: ContentAction) { restore = true; action(next) }
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (narrow) 12.dp else 20.dp)) {
            RemoteArtwork(item.artworkUrl, Modifier.size(if (narrow) 64.dp else 104.dp).clickable(onClickLabel = "播放 ${item.title}", onClick = { request(ContentAction.Play) }), RoundedCornerShape(8.dp))
            Column(Modifier.weight(1f)) {
                Text("最佳匹配 · Bilibili", style = MaterialTheme.typography.labelMedium)
                Text(item.title, fontSize = if (narrow) 22.sp else 28.sp, lineHeight = if (narrow) 28.sp else 36.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 8.dp))
                Text("${item.author} · 视频 · ${formatTime(item.durationSeconds * 1000)}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                FilledIconButton({ request(ContentAction.Play) }, Modifier.size(56.dp).focusRequester(focus), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Outlined.PlayArrow, "播放 ${item.title}", Modifier.size(28.dp)) }
                ContentMenu(item, favorite, ::request, toggleFavorite)
            }
        }
    }
}
@Composable private fun ContentRow(item: ContentItem, state: PlaybackState, favorite: Boolean, first: Boolean, last: Boolean, narrow: Boolean, partCount: Int?, selection: PartSelection?, action: (ContentAction) -> Unit, toggleFavorite: () -> Unit) {
    val focus = remember { FocusRequester() }
    var restore by remember { mutableStateOf(false) }
    var sawSelection by remember { mutableStateOf(false) }
    LaunchedEffect(selection) { if (restore && selection?.details?.item?.id == item.id) sawSelection = true; if (restore && sawSelection && selection == null) { focus.requestFocus(); restore = false; sawSelection = false } }
    fun request(next: ContentAction) { restore = true; action(next) }
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val selected = (state.track as? BilibiliTrack)?.item?.stableKey == item.stableKey
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(topStart = if (first) 20.dp else 0.dp, topEnd = if (first) 20.dp else 0.dp, bottomStart = if (last) 20.dp else 0.dp, bottomEnd = if (last) 20.dp else 0.dp)
    Surface(shape = shape, color = if (selected) colors.secondaryContainer else colors.surfaceContainerLow) {
        Column {
            if (!first) HorizontalDivider(color = colors.outlineVariant)
            Row(Modifier.fillMaxWidth().hoverable(interactions).background(if (hovered) colors.onSurface.copy(alpha = .06f) else Color.Transparent).focusRequester(focus).clickable(onClickLabel = "播放 ${item.title}", onClick = { request(ContentAction.Play) }).padding(start = 12.dp, end = 8.dp).heightIn(min = 80.dp), verticalAlignment = Alignment.CenterVertically) {
                RemoteArtwork(item.artworkUrl, Modifier.size(48.dp), RoundedCornerShape(8.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${item.author} · 视频${if (partCount != null && partCount > 1) " · $partCount 个分P" else ""}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = colors.onSurfaceVariant)
                }
                if (!narrow) { Text(formatTime(item.durationSeconds * 1000), style = MaterialTheme.typography.bodySmall); IconToggleButton(favorite, { toggleFavorite() }, Modifier.size(48.dp)) { Icon(if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, "收藏 ${item.title}") } }
                ContentMenu(item, favorite, ::request, toggleFavorite)
            }
        }
    }
}
@Composable private fun ContentMenu(item: ContentItem, favorite: Boolean, action: (ContentAction) -> Unit, toggleFavorite: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton({ menu = true }, Modifier.size(48.dp)) { Icon(Icons.Outlined.MoreHoriz, "${item.title} 的更多操作") }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem({ Text("立即播放") }, { menu = false; action(ContentAction.Play) })
            DropdownMenuItem({ Text("加入队列") }, { menu = false; action(ContentAction.Enqueue) })
            DropdownMenuItem({ Text("下一首播放") }, { menu = false; action(ContentAction.Next) })
            DropdownMenuItem({ Text(if (favorite) "取消收藏" else "收藏") }, { menu = false; toggleFavorite() })
        }
    }
}
@Composable private fun EmptyContent(title: String, copy: String, retry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().heightIn(min = 240.dp).padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
        Icon(Icons.Outlined.Search, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(copy, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (retry != null) Button(retry) { Text("重试") }
    }
}
@Composable private fun SettingRow(title: String, description: String, control: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; control()
        }
    }
}
@Composable internal fun DesktopQueuePane(state: PlaybackState, close: () -> Unit, select: (PlaybackTrack) -> Unit) = QueuePane(state, close, select, state::removeFromQueue)

@Composable
internal fun PartsDialog(selection: PartSelection, currentId: String?, choose: (VideoPart) -> Unit, cancel: () -> Unit) {
    val focus = remember { FocusRequester() }
    Dialog(cancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("选择分P", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
                    IconButton(cancel, Modifier.size(48.dp)) { Icon(Icons.Outlined.Close, "关闭分P选择") }
                }
                Text(selection.details.item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(vertical = 8.dp))
                Text(when(selection.action) { ContentAction.Play -> "选择要播放的分P"; ContentAction.Enqueue -> "选择要加入队列的分P"; ContentAction.Next -> "选择下一首播放的分P" }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp).padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    itemsIndexed(selection.details.parts, key = { _, part -> part.cid }) { index, part ->
                        val current = currentId == "${selection.details.item.stableKey}:${part.cid}"
                        Surface(onClick = { choose(part) }, modifier = Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(focus) else Modifier).heightIn(min = 72.dp), shape = RoundedCornerShape(12.dp), color = if (current) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("P${part.number}", style = MaterialTheme.typography.labelLarge)
                                Column(Modifier.weight(1f)) { Text(part.title, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(formatTime(part.durationSeconds * 1000), style = MaterialTheme.typography.labelMedium) }
                                Icon(if (current) Icons.Outlined.Check else if (selection.action == ContentAction.Play) Icons.Outlined.PlayArrow else Icons.Outlined.Add, null)
                            }
                        }
                    }
                }
                TextButton(cancel, Modifier.align(Alignment.End).heightIn(min = 48.dp)) { Text("取消") }
            }
        }
        LaunchedEffect(Unit) { focus.requestFocus() }
    }
}
