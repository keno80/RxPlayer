# 双端歌单正式界面接线

日期：2026-11-06；分支：`codex/local-playlists`；确认基线：完整原型 `268802b`。

用户在查看歌单原型后以“可以，开始吧”确认并授权正式实现，允许在需要 Android 调试时联系。实现复用现有完整原型，不另建专项页；原型标题与说明同步为“歌单设计已确认”，不将设计确认等同于原生界面验收。

## 已接入

- 双端音乐库：收藏 / 歌单 / 最近播放分类、真实本地歌单卡片与空状态、详情、播放全部 / 追加队列。
- 管理歌单：同原型的管理弹层、重命名表单、删除确认；详情曲目菜单可上移 / 下移 / 移除，首尾禁用对应移动操作。编辑不会改变现有播放队列。
- 搜索 / 收藏条目“添加到歌单”经 `ContentAction.AddToPlaylist` 解析具体分 P，选择后打开会话内歌单选择器。最近播放和歌单已有条目直接沿用原 cid，不重新选择默认 P1。
- 选择已有歌单去重，或创建并添加；取消仅关闭上下文，不播放、不清空队列。回归验证添加过程中原曲目、3000ms 位置与播放状态保持。
- 复用上一批 SQLite schema 2 与批量播放基础；本批不新增数据库版本、不保存临时播放 URL。
- 页面 SavedState 保存歌单详情、管理上下文和详情滚动；识别旧版 8 字段格式并补默认值，避免升级恢复越界。缺失会话候选的创建弹层安全关闭，不创建空记录。
- 空格快捷键不穿透歌单弹层；返回 / Esc 关闭当前弹层，分 P 取消与歌单取消都不修改播放。

## 实际检查

- 新来源到歌单回归先运行，在生产入口未实现时因缺失 API 编译失败；接线后通过。
- 审查发现 SavedState 旧格式越界，新测试先复现 `ArrayIndexOutOfBoundsException`，兼容修复后通过。
- 最终定向 desktopTest 共 20 项全部通过，无跳过：PlaybackRuntimeTest 8、PlaylistStateTest 2、LibraryStoreTest 7、BilibiliBrowseStateTest 1、WorkspaceStateTest 2。
- 最终 `:androidApp:assembleDebug`、`desktopJar` 构建通过。Windows 已通过 `:composeApp:run` 启动新版本并核对 RxPlayer 窗口进程；未自行逐页操作 / 截图或扩展完整 UI 回归，界面由用户审核。
- ADB 当前无设备，正式 APK 已生成于 `androidApp/build/outputs/apk/debug/androidApp-debug.apk`；本批未安装 Android 包、未做真机后台 / 横屏 / 大字体验收，也未安装独立测试包。
- 定向只读审查指出的旧 SavedState 兼容问题已修复并复查，无剩余 Critical / Important。已有图标 API 弃用警告不影响本次构建。

## 后续

用户审核 Windows 新歌单；Android 手机连接恢复后安装正式包并集中审核。同名歌单允许，歌单数据仅保存在本机；账号、云端歌单、网易云 / YouTube 接入继续独立跟踪。
