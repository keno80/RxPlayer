# 队列手动排序接线

日期：2026-11-06；分支 `codex/queue-reordering`；基点 `840b0f3`。

## 确认与实现

用户先要求完成原型与正式实现，审阅第一稿后明确指定每行末尾常驻拖动 icon、不要“排序”入口，随后回复“按简化版继续”。已先原位更新唯一完整原型，再按此范围接入双端共用 QueuePane；未增加独立页面或排序模式。点按手柄展开上移 / 下移，保留移除、键盘操作与辅助功能动作。

PlaybackState.moveQueue 按当前有效顺序移动稳定 ID，不触碰选择 job、引擎、解析、seek 或播放意图。移动当前项也保留身份 / 进度 / 暂停状态。随机开启时调整本轮顺序，并把完整可见排序确认为手动基础队列，不关闭随机 / 循环；不是仅把一个条目移动到原洗牌前的基础队列。

PlaybackCheckpoint 增加 playOrder，schema 3 增加 queue_play_order；v1 / v2 顺序迁移，收藏、歌单、历史及当前项仍在统一事务保存。写入要求顺序为队列完整排列；旧数据无本轮顺序时才生成随机顺序，新版本重开保留本轮顺序、保持停止恢复。

QueuePane 只在手柄命中区域开始拖动，使用预览列表、Material Expressive 空间动效及边缘滚动，放下时一次提交；取消、越界释放、拖动期间实际队列变化均丢弃预览。当前播放底色保留。移动工具跟随稳定 ID；长队列滚动至目标，关闭工具后回到对应手柄焦点。

手柄为官方 Google Material Symbols Rounded SVG 同源矢量，许可沿用现有 Material Symbols Apache-2.0 文件。未改生成的 PlayerSymbols 字体路径。

## 定向验证

- 先运行新增用例，确认缺少 moveQueue / playOrder 失败，再实现转绿。
- Java 17 desktopTest 共 42 项通过：PlaybackStateTest 13、OnlinePlaybackStateTest 11、PlaybackRuntimeTest 8、LibraryStoreTest 8、WindowsMediaSessionTest 2，无失败 / 跳过。
- 新增 4 项状态用例覆盖移动不加载 / 不中断、自然下一首、解析期间移动、随机轮次 / 当前项 / 位置恢复、无效移动；新增 v2 迁移及顺序回读 / 非法保存保留旧数据用例，v1 迁移断言同步 schema 3。
- 浏览器定向检查通过：上下移、键盘、首尾限制、关闭重开保留顺序、播放身份 / 进度保持、随机模式保持与下一首对应可见顺序、Android 面板宽度及 48px 命中区、空 / 单曲禁用。手柄拖动使用浏览器真实 drag 工具；Esc 取消为事件处理路径模拟检查，不混称原生触屏验收。
- 审查修复越界 Release 坐标滞后问题；补充长列表焦点恢复，复查无剩余 Critical / Important。
- 测试任务包含共享 / 桌面必要编译；按用户最新约定不额外构建安装包，未重打 Android APK、未安装正式或测试包。原生双端拖动 / 触屏 / 长队列验收仍由用户审核，不用单测或浏览器替代。
- Windows 最终版本已启动，日志确认 `Windows tray connected (system Unicode menu)` 及 `Windows media session connected`；未继续逐页 GUI 操作。

本批不包含 Bilibili 异常流程收口，不将 M2 整体标为验收完成。
