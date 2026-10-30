# M2 本地持久化与恢复实现计划

**授权范围：** 用户于 2026-10-30 对“保存收藏、最近播放、队列和设置；重启恢复暂停；播放时重新取址”回复“开始吧”。不新增歌单管理、账号、后台服务或界面布局。设置中的恢复说明沿用完整原型已有文案。

**方案：** 共用 AndroidX Bundled SQLite 驱动，Android 使用私有 files 目录，Windows 使用 LOCALAPPDATA/RxPlayer。与两套平台数据库代码相比，共享 SQL 和事务更容易验证；与 JSON 文件相比，数据库能事务提交并明确管理版本。采用版本 1 的内容、分 P、收藏、历史、队列和会话表；未知高版本只报错并保留文件，不降级重建。

**恢复契约：** 收藏保留内容标识，历史与队列保留具体 cid、顺序与元数据。恢复当前队列项、位置、随机 / 循环、音量、明暗及封面配色设置。恢复不解析、不加载引擎、不自动播放；用户操作播放时重新解析。普通播放每 5 秒保存位置，暂停 / 拖动后及正常退出保存精确位置，突然断电最多丢失最近一次未提交的进度。数据读写出错使用现有短提示，读取失败后不覆盖原库。

**官方依据：** [SQLite KMP](https://developer.android.com/kotlin/multiplatform/sqlite) 支持 Android 与 JVM Desktop，采用共用同步 API 并在 IO 调度器执行；锁定 sqlite-bundled 2.7.1。无账号凭据或临时 AudioStream URL 入库。

## 实现与检查

- [x] 播放恢复契约：在 `audio/PlaybackState.kt` 增加纯内存 checkpoint 恢复；先在 `commonTest/.../audio/OnlinePlaybackStateTest.kt` 验证恢复无引擎 / 网络调用、恢复播放重新解析及保留 cid / 位置。
- [x] 数据库：`persistence/LibrarySnapshot.kt` 定义持久化快照；`persistence/LibraryStore.kt` 管理版本、事务与读写。先在 `desktopTest/.../persistence/LibraryStoreTest.kt` 验证磁盘重开、顺序、去重身份、空数据覆盖、事务回滚及未知版本拒绝。
- [x] 平台路径：Android / desktop 的 `persistence/LibraryPath.kt` 仅负责路径，公共层管理连接与事务。依赖写入 `composeApp/build.gradle.kts`。
- [x] 接线：`App.kt` 在恢复完成后创建状态，监听收藏 / 历史 / 设置 / 队列 / 位置变化并串行保存；关闭时刷盘，读取失败不启用写入；Windows `Main.kt` 等待保存后退出。设置恢复说明与既有原型一致。
- [x] 定向运行 `:composeApp:desktopTest` 的持久化与播放状态测试；构建双端，安装 Android，启动双端。按用户要求不逐页操作 / 截图验收，界面由用户审核。更新进度，提交推送并合并主分支。

本轮验证不替代完整 M2 验收；歌单、后台 / 系统媒体服务以及 M1 发布门禁继续独立跟踪。
