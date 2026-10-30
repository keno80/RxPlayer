package com.keno.rxplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.keno.rxplayer.MusicSource
import com.keno.rxplayer.source.ContentAction
import com.keno.rxplayer.source.ContentItem

// Geometry mirrors the confirmed Android prototype. System insets stay platform supplied.
@Composable
internal fun MobileSearchWaiting(query: String, cancel: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(top = 24.dp), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(24.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.primary) }
                Column(Modifier.weight(1f)) {
                    Text("正在搜索 Bilibili", fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
                    Text("正在查找「$query」。当前播放不受影响。", Modifier.padding(top = 6.dp), fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 24.dp).height(4.dp))
            FilledTonalButton(cancel, Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) { Text("取消搜索") }
        }
    }
}

@Composable
internal fun MobileCollectionSummary(count: Int) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp, 64.dp).background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Favorite, null, Modifier.size(32.dp), MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("喜欢的声音", fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
                Text("$count 首收藏", fontSize = 12.sp, lineHeight = 16.sp)
            }
            FilledIconButton({}, Modifier.size(48.dp, 56.dp), enabled = false, shape = RoundedCornerShape(16.dp)) { Icon(Icons.Outlined.PlayArrow, "批量播放暂未开放", Modifier.size(32.dp)) }
        }
    }
}

@Composable
internal fun MobileLibraryTabs(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 20.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("收藏", "歌单", "最近播放").forEachIndexed { index, title ->
                Box(Modifier.weight(1f)) {
                    Tab(selected == index, { onSelect(index) }, Modifier.height(48.dp), text = { Text(title, fontSize = 14.sp, fontWeight = if (selected == index) FontWeight.Bold else FontWeight.Medium, maxLines = 1) })
                    if (selected == index) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth(.52f).height(3.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
internal fun MobileSettings(dark: Boolean, changeTheme: (Boolean) -> Unit, followArtwork: Boolean, changeArtwork: (Boolean) -> Unit, notify: (String) -> Unit) {
    MobileSectionTitle("外观")
    MobileSettingsGroup {
        MobileSetting(Icons.Outlined.DarkMode, "主题", "选择舒服的明暗") {
            var open by remember { mutableStateOf(false) }
            Box {
                FilledTonalButton({ open = true }, Modifier.width(84.dp).heightIn(min = 48.dp).semantics { contentDescription = "应用主题" }, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Text(if (dark) "深色" else "浅色", fontSize = 14.sp); Icon(Icons.Outlined.ArrowDropDown, null, Modifier.size(20.dp))
                }
                DropdownMenu(open, { open = false }) {
                    DropdownMenuItem({ Text("浅色") }, { changeTheme(false); open = false })
                    DropdownMenuItem({ Text("深色") }, { changeTheme(true); open = false })
                }
            }
        }
        MobileSetting(Icons.Outlined.Album, "封面舞台底色", "用封面取色衬托封面") { Switch(followArtwork, changeArtwork, Modifier.semantics { contentDescription = "封面舞台底色" }) }
    }
    MobileSectionTitle("播放")
    MobileSettingsGroup { MobileSetting(Icons.Outlined.History, "恢复播放位置", "打开时保持暂停，由你决定何时继续") { Icon(Icons.Outlined.Check, "已启用", tint = MaterialTheme.colorScheme.onSurfaceVariant) } }
    MobileSectionTitle("音源账号")
    MobileSettingsGroup {
        listOf(MusicSource.Bilibili, MusicSource.Netease, MusicSource.YouTube).forEach { source ->
            MobileSetting(Icons.Outlined.MusicNote, source.label, "未连接") {
                TextButton({ notify("音源账号暂未开放，当前使用匿名访问") }, Modifier.heightIn(min = 48.dp), contentPadding = PaddingValues(horizontal = 8.dp)) { Text("连接", fontSize = 14.sp) }
            }
        }
    }
    Text("账号功能暂未开放，未连接任何平台账号。", fontSize = 12.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.padding(top = 32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Rx.", color = MaterialTheme.colorScheme.primary, fontSize = 28.sp, lineHeight = 36.sp)
        Text("让每一段声音，都有自己的位置。", fontSize = 12.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun MobileSectionTitle(title: String) {
    Text(title, Modifier.padding(top = 24.dp, bottom = 12.dp).semantics { heading() }, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
}
@Composable private fun MobileSettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color.Transparent) { Column(verticalArrangement = Arrangement.spacedBy(2.dp), content = content) }
}
@Composable private fun MobileSetting(icon: ImageVector, title: String, subtitle: String, control: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).heightIn(min = 88.dp).padding(horizontal = 12.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        control()
    }
}

@Composable
internal fun MobileContentMenu(item: ContentItem, favorite: Boolean, close: () -> Unit, action: (ContentAction) -> Unit, toggleFavorite: () -> Unit, partCount: Int?, chooseParts: () -> Unit) {
    Dialog(close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 24.dp), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(item.title, Modifier.weight(1f).semantics { heading() }, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    IconButton(close, Modifier.size(48.dp)) { Icon(Icons.Outlined.Close, "关闭内容操作") }
                }
                Text("${item.author} · ${item.source.label}", Modifier.padding(top = 20.dp, bottom = 12.dp), fontSize = 12.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (partCount != null && partCount > 1) MobileMenuAction(Icons.Outlined.ViewList, "选择分P", chooseParts)
                MobileMenuAction(Icons.Outlined.SkipNext, "下一首播放") { action(ContentAction.Next) }
                MobileMenuAction(Icons.Outlined.QueueMusic, "加入播放队列") { action(ContentAction.Enqueue) }
                MobileMenuAction(if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder, if (favorite) "取消收藏" else "收藏", toggleFavorite)
                TextButton({}, Modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = false, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Outlined.PlaylistAdd, null); Spacer(Modifier.width(16.dp)); Text("添加到歌单（暂未开放）", Modifier.weight(1f), fontSize = 14.sp)
                }
            }
        }
    }
}
@Composable private fun MobileMenuAction(icon: ImageVector, text: String, click: () -> Unit) {
    TextButton(click, Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(12.dp), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
        Icon(icon, null); Spacer(Modifier.width(16.dp)); Text(text, Modifier.weight(1f), fontSize = 14.sp, lineHeight = 21.sp)
    }
}
