# 播放队列重排实现计划

**目标：** 在双端队列末尾常驻拖动手柄，移动不重选曲，重开保留队列及本轮随机顺序。

**架构：** 共用 PlaybackState 按稳定 ID 移动有效播放序列；PlaybackCheckpoint 保存本轮顺序，SQLite schema 3 增加顺序表，v1 / v2 事务升级。QueuePane 两端共用，指针手柄拖动只在放下时提交，取消不写入；点按手柄和键盘使用同一移动方法。

**技术栈：** 现有 Kotlin / Compose、BundledSQLiteDriver；不新增依赖。

- [x] 在唯一完整原型实现交互，按用户要求去掉排序模式；桌面 / Android 拖动、按钮 / 键盘、取消、首尾及模式保持定向检查。
- [x] 在 PlaybackStateTest / LibraryStoreTest 添加有效顺序、当前项 / 位置、解析中重排、非法移动、随机恢复及 v1 / v2 升级测试；先运行观察缺少 moveQueue / playOrder 失败。
- [x] PlaybackState 增加 `moveQueue(id: String, target: Int): Boolean`；只变队列及当前轮次顺序，checkpoint 增加 `playOrder: List<String> = emptyList()`，恢复验证为队列完整排列后使用。
- [x] LibraryStore schema 3 加 `queue_play_order(position, track_id)`，统一快照事务写入；测试迁移后原收藏 / 歌单 / 当前项保留及重开顺序一致。
- [x] 共用 QueuePane 显示有效顺序，添加官方同源手柄、点按移动工具、键盘语义及手柄区域指针拖动 / 预览 / 取消 / 边缘滚动。原型与正式 UI 不增加独立排序模式。
- [x] 42 项定向桌面单测（包含共享代码编译）、静态审查通过；Windows 已启动。按用户最新约定不额外构建安装包、不安装测试 APK，Android 源码更新不等于实机验收。
- [x] 更新进度与技术记录；本批按已确认范围交付，不因提交 / 合并重复测试或构建，最终提交推送结果以 Git 记录为准。
