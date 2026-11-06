package com.keno.rxplayer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.keno.rxplayer.metadata.SongMetadataState

@Composable
internal fun SongMetadataDialog(state: SongMetadataState) {
    val target = state.target ?: return
    val selected = state.selected(target.id)
    Dialog(state::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(24.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(max = maxHeight), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("匹配歌曲信息", Modifier.weight(1f).semantics { heading() }, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
                        IconButton(state::close) { Icon(PlayerSymbols.Close, "关闭歌曲匹配") }
                    }
                    Text("当前视频 · ${target.title}\nUP 主：${target.item.author} · ${formatTime(target.durationMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(selected?.song?.let { "已选：${it.title} · ${it.artists.joinToString(" / ")}" } ?: "选择对应的歌曲版本，补充封面和歌词。", style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(state.query, { state.query = it.take(200) }, Modifier.weight(1f), label = { Text("歌名 / 歌手") }, singleLine = true,
                            enabled = !state.applying, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { if (!state.searching && !state.applying) state.search() }), shape = RoundedCornerShape(8.dp))
                        Button({ state.search() }, enabled = !state.searching && !state.applying && state.query.isNotBlank()) { Text(if (state.searching) "搜索中…" else "搜索") }
                    }
                    Text("歌曲候选 · 网易云", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.candidates.forEach { song ->
                        TextButton({ state.apply(song) }, enabled = !state.applying && !state.searching, modifier = Modifier.fillMaxWidth().semantics { this.selected = selected?.song?.id == song.id },
                            shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(12.dp),
                            colors = ButtonDefaults.textButtonColors(containerColor = if (selected?.song?.id == song.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer, contentColor = MaterialTheme.colorScheme.onSurface)) {
                            RemoteArtwork(song.artworkUrl, Modifier.size(56.dp), RoundedCornerShape(8.dp))
                            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(song.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("${song.artists.joinToString(" / ")} · ${formatTime(song.durationMs)}", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(song.album, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (selected?.song?.id == song.id) if (selected.lyrics.isEmpty()) "暂无同步歌词" else "同步歌词 · ${selected.lyricsSource.orEmpty()}" else "歌词待查询", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (selected?.song?.id == song.id) Icon(PlayerSymbols.Check, "已选择")
                        }
                    }
                    if (state.searched && !state.searching && state.candidates.isEmpty()) Text("没有找到匹配歌曲，试试修改歌名或歌手。", style = MaterialTheme.typography.bodyMedium)
                    if (state.applying) Text("正在获取封面和歌词…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("现场、翻唱或语言版本不同，不自动套用原版时间轴。匹配不改变正在播放的视频。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (selected != null) TextButton(state::restoreOriginal) { Text("使用视频原信息") }
                        TextButton(state::close) { Text("取消") }
                    }
                }
            }
        }
    }
}
