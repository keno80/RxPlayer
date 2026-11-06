package com.keno.rxplayer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.keno.rxplayer.audio.PlaybackRuntime
import com.keno.rxplayer.library.LocalPlaylist

internal data class PlaylistRowActions(val index: Int, val count: Int, val move: (Int) -> Unit, val remove: () -> Unit)

@Composable
internal fun PlaylistDetailHead(list: LocalPlaylist, back: () -> Unit, edit: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconButton(back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回音乐库") }
        Column(Modifier.weight(1f)) {
            Text("本地歌单 · ${list.tracks.size} 首", fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(list.name, Modifier.padding(top = 8.dp).semantics { heading() }, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Medium)
        }
        IconButton({ edit("manage") }) { Icon(Icons.Outlined.MoreHoriz, "管理歌单") }
    }
}

@Composable
internal fun PlaylistGrid(lists: List<LocalPlaylist>, open: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        lists.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { list ->
                    Surface(onClick = { open(list.id) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.padding(12.dp)) {
                            val artwork = list.tracks.firstOrNull()?.item?.artworkUrl
                            if (artwork != null) RemoteArtwork(artwork, Modifier.fillMaxWidth().aspectRatio(1.4f), RoundedCornerShape(12.dp))
                            else Surface(Modifier.fillMaxWidth().aspectRatio(1.4f), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.LibraryMusic, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                            }
                            Text(list.name, Modifier.padding(top = 12.dp), fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
                            Text("${list.tracks.size} 首 · 本地歌单", Modifier.padding(top = 4.dp), fontSize = 12.sp, lineHeight = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
internal fun PlaylistBatchControls(list: LocalPlaylist, play: () -> Unit, append: () -> Unit) {
    Text("按分 P 收藏，按自己的顺序听。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(Modifier.padding(top = 24.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(play, enabled = list.tracks.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("播放全部") }
        TextButton(append, enabled = list.tracks.isNotEmpty(), modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Outlined.QueueMusic, null); Spacer(Modifier.width(8.dp)); Text("加入队列") }
    }
}

@Composable
internal fun PlaylistDialogs(runtime: PlaybackRuntime, workspace: WorkspaceState) {
    val candidate = runtime.playlistCandidate
    val mode = workspace.playlistDialog ?: if (candidate != null) "pick" else return
    val editing = runtime.playlists.lists.firstOrNull { it.id == workspace.editingPlaylistId }
    fun dismiss() { workspace.playlistDialog = null; workspace.editingPlaylistId = null; runtime.cancelPlaylistCandidate() }
    if ((mode == "manage" || mode == "rename" || mode == "delete") && editing == null || mode == "create-add" && candidate == null) {
        LaunchedEffect(mode) { dismiss() }
        return
    }
    val title = when (mode) { "pick" -> "添加到歌单"; "manage" -> editing!!.name; "rename" -> "重命名歌单"; "delete" -> "删除歌单？"; else -> "新建歌单" }
    PlaylistDialog(title, ::dismiss) {
        when (mode) {
            "pick" -> {
                Text("${candidate!!.title} · 相同分 P 不会重复添加。", Modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                runtime.playlists.lists.forEach { list ->
                    val added = list.tracks.any { it.id == candidate.id }
                    TextButton({ runtime.addCandidateToPlaylist(list.id) }, Modifier.fillMaxWidth().heightIn(min = 56.dp), contentPadding = PaddingValues(12.dp), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                        Icon(if (added) Icons.Outlined.Check else Icons.Outlined.Album, null)
                        Text("${list.name}${if (added) " · 已添加" else ""}", Modifier.weight(1f).padding(start = 16.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                TextButton({ workspace.playlistDialog = "create-add" }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Icon(Icons.Outlined.Add, null); Text("新建歌单并添加", Modifier.weight(1f).padding(start = 16.dp)) }
            }
            "manage" -> {
                TextButton({ workspace.playlistDialog = "rename" }, Modifier.fillMaxWidth().heightIn(min = 56.dp), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Icon(Icons.Outlined.Edit, null); Text("重命名", Modifier.weight(1f).padding(start = 16.dp))
                }
                TextButton({ workspace.playlistDialog = "delete" }, Modifier.fillMaxWidth().heightIn(min = 56.dp), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Icon(Icons.Outlined.Delete, null); Text("删除歌单", Modifier.weight(1f).padding(start = 16.dp))
                }
            }
            "delete" -> {
                Text("将删除「${editing!!.name}」及其中的收纳记录，当前播放队列和收藏会保留。", Modifier.padding(vertical = 16.dp))
                Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(::dismiss) { Text("取消") }
                    Button({ runtime.playlists.delete(editing.id); workspace.playlistId = null; dismiss(); runtime.warn("歌单已删除，当前播放保持不变") }) { Text("删除") }
                }
            }
            else -> {
                var name by rememberSaveable(mode, workspace.editingPlaylistId) { mutableStateOf(if (mode == "rename") editing!!.name else "") }
                var invalid by rememberSaveable(mode, workspace.editingPlaylistId) { mutableStateOf(false) }
                fun confirm() {
                    val value = name.trim()
                    if (value.length !in 1..40) { invalid = true; return }
                    if (mode == "rename") { runtime.playlists.rename(editing!!.id, value); runtime.warn("歌单名称已更新") }
                    else {
                        runtime.createPlaylist(value, addCandidate = mode == "create-add")
                        workspace.destination = WorkspaceDestination.Library; workspace.libraryTab = 1; workspace.playlistId = null
                        runtime.warn(if (mode == "create-add") "歌单已创建，曲目已添加" else "歌单已创建")
                    }
                    dismiss()
                }
                TextField(name, { name = it; invalid = false }, Modifier.fillMaxWidth().padding(top = 16.dp), label = { Text("歌单名称") }, singleLine = true, isError = invalid,
                    supportingText = { Text(if (invalid) "请输入 1～40 个字符的歌单名称" else "最多 40 个字符，保存在本机。") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { confirm() }), shape = RoundedCornerShape(12.dp))
                Row(Modifier.align(Alignment.End).padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(::dismiss) { Text("取消") }
                    Button(::confirm) { Text(when (mode) { "rename" -> "保存"; "create-add" -> "创建并添加"; else -> "创建" }) }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDialog(title: String, dismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(24.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 480.dp).fillMaxWidth().heightIn(max = maxHeight), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp)) {
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f).semantics { heading() }, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
                        IconButton(dismiss) { Icon(Icons.Outlined.Close, "关闭歌单操作") }
                    }
                    content()
                }
            }
        }
    }
}
