@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.keno.rxplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keno.rxplayer.DemoTrack
import com.keno.rxplayer.MusicSource
import com.keno.rxplayer.audio.PlaybackState
import com.keno.rxplayer.demoTracks

internal enum class DesktopDestination(val label: String, val icon: ImageVector) {
    Search("搜索", Icons.Outlined.Search),
    Library("音乐库", Icons.Outlined.LibraryMusic),
    Settings("设置", Icons.Outlined.Settings),
}

// Owned by App so opening the full player doesn't reset the browsing context.
internal class DesktopWorkspaceState {
    var destination by mutableStateOf(DesktopDestination.Search)
    var source by mutableStateOf(MusicSource.Bilibili)
    var queries by mutableStateOf(MusicSource.entries.associateWith { "" })
    var submittedQueries by mutableStateOf(queries)
    val searchScroll = LazyListState()
    val libraryScroll = LazyListState()
    val settingsScroll = LazyListState()
}

// Geometry follows prototype 03; these are RxPlayer layout decisions, not M3 tokens.
@Composable
internal fun DesktopWorkspace(
    browsing: DesktopWorkspaceState,
    playback: PlaybackState, favorites: Set<String>, dark: Boolean, followArtwork: Boolean,
    queueOpen: Boolean, onQueueClose: () -> Unit, onSelect: (DemoTrack) -> Unit,
    onFavorite: (DemoTrack) -> Unit, onExpand: () -> Unit, onQueryFocus: (Boolean) -> Unit,
    onDarkChange: (Boolean) -> Unit, onArtworkChange: (Boolean) -> Unit,
    queueContent: @Composable () -> Unit,
    transport: @Composable () -> Unit,
) {
    val destination = browsing.destination
    val source = browsing.source
    val queries = browsing.queries
    val submittedQueries = browsing.submittedQueries
    val query = queries.getValue(source)
    val submitted = submittedQueries.getValue(source).trim()
    val results = demoTracks.filter {
        it.source == source && (submitted.isEmpty() || "${it.title} ${it.artist} ${it.subtitle}".contains(submitted, ignoreCase = true))
    }
    val listState = when (destination) {
        DesktopDestination.Search -> browsing.searchScroll
        DesktopDestination.Library -> browsing.libraryScroll
        DesktopDestination.Settings -> browsing.settingsScroll
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val narrow = maxWidth <= 700.dp
        val dockQueue = queueOpen && maxWidth > 1100.dp
        Row(Modifier.fillMaxSize()) {
            Surface(Modifier.width(if (narrow) 72.dp else 96.dp).fillMaxHeight(), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Rx.", color = MaterialTheme.colorScheme.primary, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 20.dp))
                    DesktopDestination.entries.forEach { item ->
                        val selected = item == destination
                        TextButton(onClick = { browsing.destination = item; onQueryFocus(false) }, modifier = Modifier.fillMaxWidth().height(72.dp).semantics { this.selected = selected },
                            contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(16.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(Modifier.size(if (narrow) 48.dp else 64.dp, 36.dp).background(
                                    if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                                    Icon(item.icon, null, tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(item.label, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(Modifier.weight(1f).fillMaxHeight(), state = listState,
                        contentPadding = PaddingValues(horizontal = if (narrow) 16.dp else 32.dp, vertical = if (narrow) 20.dp else 28.dp)) {
                        item {
                            DesktopPageHead(destination.label,
                                if (destination == DesktopDestination.Search) "歌曲、视频，从这里开始听。" else if (destination == DesktopDestination.Settings) "外观、音源与播放偏好。" else null,
                                if (destination == DesktopDestination.Library) "为喜欢的声音留个位置" else "RxPlayer",
                                if (destination == DesktopDestination.Search) onExpand else null)
                        }
                        when (destination) {
                            DesktopDestination.Search -> {
                                item {
                                    DesktopSearchField(query, { browsing.queries = queries + (source to it) }, {
                                        browsing.submittedQueries = submittedQueries + (source to query)
                                    }, { browsing.queries = queries + (source to ""); browsing.submittedQueries = submittedQueries + (source to "") }, onQueryFocus)
                                    Spacer(Modifier.height(16.dp))
                                    DesktopSources(source) { browsing.source = it }
                                    DesktopSectionHead("搜索结果", "${results.size} 首本地演示")
                                }
                                if (results.isEmpty()) item { DesktopEmpty("没有匹配的结果", "换个关键词，再发现一些好声音。", "重新搜索") {
                                    browsing.queries = queries + (source to "")
                                    browsing.submittedQueries = submittedQueries + (source to "")
                                } }
                                else {
                                    item { DesktopFeatured(results.first(), narrow) { onSelect(results.first()) } }
                                    if (results.size > 1) {
                                        item { DesktopSectionHead("更多匹配") }
                                        itemsIndexed(results.drop(1), key = { _, track -> track.id }) { index, track ->
                                            DesktopResultRow(track, playback, track.id in favorites, index == 0, index == results.size - 2,
                                                { onSelect(track) }, { onFavorite(track) }, narrow)
                                        }
                                    }
                                }
                                item { Text("来自 ${source.label} · 本地演示分类\n在线搜索尚未接入，当前仅搜索 3 首原创本地音频。", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp)) }
                            }
                            DesktopDestination.Library -> {
                                val saved = demoTracks.filter { it.id in favorites }
                                item {
                                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                        Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                            Icon(Icons.Outlined.Favorite, null, Modifier.size(32.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text("喜欢的声音", style = MaterialTheme.typography.headlineMedium)
                                                Text("${saved.size} 首收藏 · 跨音源收纳", style = MaterialTheme.typography.bodySmall)
                                            }
                                            FilledIconButton(onClick = { saved.firstOrNull()?.let(onSelect) }, enabled = saved.isNotEmpty(), modifier = Modifier.size(56.dp)) {
                                                Icon(Icons.Rounded.PlayArrow, "播放收藏")
                                            }
                                        }
                                    }
                                    DesktopSectionHead("喜欢的声音", "${saved.size} 首")
                                }
                                if (saved.isEmpty()) item { DesktopEmpty("把喜欢的声音留在这里", "在曲目旁点击收藏，就可以随时回来再听。", "去搜索") {
                                    browsing.destination = DesktopDestination.Search
                                } }
                                itemsIndexed(saved, key = { _, track -> track.id }) { index, track ->
                                    DesktopResultRow(track, playback, true, index == 0, index == saved.lastIndex,
                                        { onSelect(track) }, { onFavorite(track) }, narrow)
                                }
                                item { Text("收藏仅保存在本次运行中；歌单和最近播放将在后续阶段接入。", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 16.dp)) }
                            }
                            DesktopDestination.Settings -> {
                                item {
                                    DesktopSectionHead("外观")
                                    DesktopSettingRow(Icons.Outlined.DarkMode, "主题", "选择舒服的明暗", true, false) {
                                        DesktopThemePicker(dark, onDarkChange)
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    DesktopSettingRow(Icons.Outlined.Album, "封面舞台底色", "用封面取色衬托封面", false, true) {
                                        Switch(followArtwork, onArtworkChange, Modifier.semantics { contentDescription = "封面舞台底色" })
                                    }
                                    DesktopSectionHead("音源账号", "尚未接入")
                                    listOf(MusicSource.Bilibili, MusicSource.Netease, MusicSource.YouTube).forEachIndexed { index, item ->
                                        DesktopSettingRow(Icons.Outlined.MusicNote, item.label, "未连接 · 账号接入尚未实现", index == 0, index == 2) {
                                            Text("未连接", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (index < 2) Spacer(Modifier.height(2.dp))
                                    }
                                    Text("Rx.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 32.dp))
                                }
                            }
                        }
                    }
                    if (dockQueue) {
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Box(Modifier.width(320.dp).fillMaxHeight()) { queueContent() }
                    }
                }
                transport()
            }
        }
        if (queueOpen && !dockQueue) QueueModal(playback, true, onQueueClose, onSelect, playback::removeFromQueue)
    }
}

@Composable
private fun DesktopPageHead(title: String, intro: String?, eyebrow: String, onExpand: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(eyebrow, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            Text(title, fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Medium, modifier = Modifier.semantics { heading() })
            if (intro != null) Text(intro, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
        if (onExpand != null) IconButton(onExpand, Modifier.size(48.dp)) { Icon(Icons.Outlined.Headphones, "展开播放器") }
    }
}

@Composable
private fun DesktopSearchField(query: String, onChange: (String) -> Unit, onSearch: () -> Unit, onClear: () -> Unit, onFocus: (Boolean) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Search, null, tint = colors.onSurfaceVariant)
            BasicTextField(query, onChange, Modifier.weight(1f).padding(start = 8.dp).heightIn(min = 48.dp).onFocusChanged { onFocus(it.isFocused) }
                .semantics { contentDescription = "搜索歌曲、视频或内容链接" },
                singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface), cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                decorationBox = { field -> Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) Text("歌曲、视频，或粘贴链接", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                    field()
                } })
            IconButton(onClear, Modifier.size(48.dp)) { Icon(Icons.Outlined.Close, "清空搜索") }
            IconButton(onSearch, Modifier.size(48.dp)) { Icon(Icons.Outlined.Search, "执行搜索") }
        }
    }
}

@Composable
private fun DesktopSources(selected: MusicSource, onChange: (MusicSource) -> Unit) {
    Row(Modifier.widthIn(max = 480.dp).fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf(MusicSource.Bilibili, MusicSource.Netease, MusicSource.YouTube).forEachIndexed { index, source ->
            val active = selected == source
            val shape = if (active) RoundedCornerShape(24.dp) else RoundedCornerShape(
                topStart = if (index == 0) 24.dp else 8.dp, bottomStart = if (index == 0) 24.dp else 8.dp,
                topEnd = if (index == 2) 24.dp else 8.dp, bottomEnd = if (index == 2) 24.dp else 8.dp)
            Surface(onClick = { onChange(source) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { this.selected = active; role = Role.RadioButton },
                shape = shape, color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
                Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                    if (active) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp))
                    Text(source.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun DesktopSectionHead(title: String, detail: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f).semantics { heading() })
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DesktopFeatured(track: DemoTrack, narrow: Boolean, onPlay: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (narrow) 12.dp else 20.dp)) {
            Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "播放 ${track.title}", onClick = onPlay)) {
                Artwork(track.artwork, Modifier.size(if (narrow) 64.dp else 104.dp), RoundedCornerShape(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.MusicNote, null, Modifier.size(18.dp))
                    Text("最佳匹配 · ${track.source.label}", style = MaterialTheme.typography.labelMedium)
                }
                Text(track.title, fontSize = if (narrow) 22.sp else 28.sp, lineHeight = if (narrow) 28.sp else 36.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                Text("${track.artist} · 本地音频 · 0:16", style = MaterialTheme.typography.bodyMedium)
            }
            FilledIconButton(onPlay, Modifier.size(56.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Rounded.PlayArrow, "播放 ${track.title}", Modifier.size(28.dp)) }
        }
    }
}

@Composable
private fun DesktopResultRow(track: DemoTrack, playback: PlaybackState, favorite: Boolean, first: Boolean, last: Boolean,
    onPlay: () -> Unit, onFavorite: () -> Unit, narrow: Boolean) {
    var menu by remember { mutableStateOf(false) }
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    val selected = playback.track?.id == track.id
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(topStart = if (first) 20.dp else 0.dp, topEnd = if (first) 20.dp else 0.dp,
        bottomStart = if (last) 20.dp else 0.dp, bottomEnd = if (last) 20.dp else 0.dp)
    Surface(shape = shape, color = if (selected) colors.secondaryContainer else colors.surfaceContainerLow) {
        Column {
            if (!first) HorizontalDivider(color = colors.outlineVariant)
            Row(Modifier.fillMaxWidth().hoverable(interactions).background(if (hovered) colors.onSurface.copy(alpha = .06f) else Color.Transparent)
                .clickable(onClickLabel = "播放 ${track.title}", onClick = onPlay).padding(start = 12.dp, end = 8.dp).heightIn(min = 80.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(track.artwork, Modifier.size(48.dp), RoundedCornerShape(8.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${track.artist} · 本地音频", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!narrow) {
                    Text(track.source.label, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
                    if (selected && playback.isPlaying) Icon(Icons.Outlined.GraphicEq, "正在播放", tint = colors.primary)
                    Text("0:16", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
                    IconToggleButton(favorite, { onFavorite() }, Modifier.size(48.dp)) { Icon(Icons.Outlined.FavoriteBorder.takeUnless { favorite } ?: Icons.Outlined.Favorite, "收藏 ${track.title}") }
                }
                Box {
                    IconButton({ menu = true }, Modifier.size(48.dp)) { Icon(Icons.Outlined.MoreHoriz, "${track.title} 的更多操作") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(if (favorite) "取消收藏" else "收藏") }, onClick = { menu = false; onFavorite() })
                        DropdownMenuItem(text = { Text("立即播放") }, onClick = { menu = false; onPlay() })
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopSettingRow(icon: ImageVector, title: String, description: String, first: Boolean, last: Boolean, control: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(topStart = if (first) 20.dp else 0.dp, topEnd = if (first) 20.dp else 0.dp,
        bottomStart = if (last) 20.dp else 0.dp, bottomEnd = if (last) 20.dp else 0.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            control()
        }
    }
}

@Composable
private fun DesktopEmpty(title: String, copy: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().heightIn(min = 300.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
        Icon(Icons.Outlined.Search, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(copy, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onAction, Modifier.heightIn(min = 48.dp)) { Text(action) }
    }
}

@Composable
private fun DesktopThemePicker(dark: Boolean, onChange: (Boolean) -> Unit) {
    var themeOpen by remember { mutableStateOf(false) }
    Box {
        FilledTonalButton({ themeOpen = true }, Modifier.heightIn(min = 48.dp).semantics { contentDescription = "应用主题" }, shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface)) {
            Text(if (dark) "深色" else "浅色")
            Icon(Icons.Outlined.ArrowDropDown, null)
        }
        DropdownMenu(themeOpen, { themeOpen = false }) {
            DropdownMenuItem(text = { Text("浅色") }, onClick = { onChange(false); themeOpen = false })
            DropdownMenuItem(text = { Text("深色") }, onClick = { onChange(true); themeOpen = false })
        }
    }
}

@Composable
internal fun DesktopQueuePane(playback: PlaybackState, onClose: () -> Unit, onSelect: (DemoTrack) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("播放队列", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
                Text("${playback.queue.size} 首", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClose, Modifier.size(48.dp)) { Icon(Icons.Outlined.Close, "收起播放队列") }
        }
        if (playback.queue.isEmpty()) Text("队列还空着，把想听的内容加入进来。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        else LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(playback.queue, key = { _, track -> track.id }) { index, track ->
                val current = playback.track?.id == track.id
                Surface(shape = RoundedCornerShape(12.dp), color = if (current) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp))
                            .clickable(onClickLabel = "播放 ${track.title}", onClick = { onSelect(track) }).semantics { selected = current },
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) {
                                if (current) Icon(if (playback.isPlaying) Icons.Outlined.GraphicEq else Icons.Outlined.MusicNote, "当前曲目", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                else Text("${index + 1}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Artwork(track.artwork, Modifier.size(40.dp), RoundedCornerShape(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${track.source.label} · 0:16", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton({ playback.removeFromQueue(track.id) }, Modifier.size(48.dp)) { Icon(Icons.Outlined.Close, "移除 ${track.title}") }
                    }
                }
            }
        }
    }
}
