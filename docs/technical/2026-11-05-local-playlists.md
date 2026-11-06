# 本地歌单共享逻辑与原型提案

日期：2026-11-05；分支：`codex/local-playlists`；基点：`cabebdb`。

后续状态（2026-11-06）：用户已确认本记录的歌单原型范围，正式双端接线、构建和 Windows 启动已完成，详见 [后续接线记录](2026-11-06-playlists-ui.md)。下文“待审核”描述为 11 月 5 日历史阶段，不再表示当前设计状态。

## 已实现范围

- commonMain `library/PlaylistState.kt`：有稳定 UUID 的有序本地歌单；新建、重命名、删除、添加、移除、移动。名称 trim 后 1～40 个 UTF-16 单元，允许同名。以具体 `BilibiliTrack.id` 去重，同视频不同 cid 可独立收纳。
- `PlaybackState.playAll` 从独立列表快照替换队列、关闭随机并从首项开始；先取消旧的解析 / 准备，避免晚返回覆盖新曲目。空单不改变当前队列。`enqueueAll` 保留当前播放及已有顺序并去重。
- `PlaybackRuntime` 持有歌单编辑状态，接入已有周期保存与最终 flush。改名、删除、排序等不会直接修改已生成的播放队列；首批仍仅支持已接入的 Bilibili 具体分 P，不提前开放网易云 / YouTube。
- `LibraryStore` schema 1 → 2 事务迁移只新增 playlists / playlist_tracks；旧收藏、历史、队列和设置保留。保存将仅被歌单引用的曲目纳入元数据表，成员表限制同单重复和位置冲突；失败整体回滚。未知高版本仍拒绝访问，不存临时流地址。

## 原型与审核边界

仅更新 `design/prototype-v3/index.html` 及同目录 JS / CSS，不复制原型。定位：`?device=desktop&theme=dark&view=playlists`；通过顶部切换 Android。刷新重置模拟数据。

新建 / 创建并添加、歌单详情、整单播放 / 追加、重命名、删除确认、曲目上移 / 下移 / 移除，以及多分 P 先选 P 再选歌单均可操作。删除只删除歌单收纳，不清空收藏或当前队列；排序提供菜单操作，避免只允许拖拽。

设计依据：m3-expressive 的 containment / feedback 参考中关于卡片、列表、菜单、对话框及可访问排序替代的指导。沿用原型既有颜色角色、字体、尺寸和形状，不重新发明一套组件。歌单信息 / 操作组合与 40 字名称上限为 RxPlayer 产品决策，不称作 Google 强制规则。

**本批提案尚未获用户确认，正式 Compose 歌单入口仍未接线。** 现有完整原型固定入口显示“歌单修订待审核”，不得根据共享逻辑通过推断正式界面可用或设计获批。分支推送供审核，确认前不合并本批原型到 main。

## 实际验证

- 新 API 的回归测试先运行，因生产实现尚不存在而编译失败；实现后修正 UUID opt-in，并使相关检查通过。
- desktopTest 定向 16 项全部通过：PlaylistStateTest 2、PlaybackRuntimeTest 7、LibraryStoreTest 7；无跳过。覆盖歌单编辑 / 分 P 去重 / 不可变列表快照、空单不换曲、追加不打断、正在解析时替换、数据库仅歌单曲目重开、v1 迁移保持旧数据、保存失败回滚、未知版本拒绝覆盖。
- `:androidApp:assembleDebug` 和 `desktopJar` 构建通过。Android 无真机验证、不安装测试 APK；不把编译写成真机验收。
- 原型 JS 语法、Git 空白检查通过；实际操作了桌面改名、P2 上移、整单追加，以及搜索多分 P → 选 P2 → 创建并添加；观察到新歌单 1 首、原当前曲目 / 位置保持不变。
- 定向只读审查未发现 Critical / Important，建议补充的旧解析晚返回回归已补入并通过。未重走无关播放 / 双端全流程。

下一步：审核完整原型中的歌单交互，确认后接入正式双端 UI；再安排 Windows 真实交互验收，Android 连接恢复后集中安装审核。
