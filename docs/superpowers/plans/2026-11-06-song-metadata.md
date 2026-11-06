# 歌曲信息补全实现计划

> 使用 writing-plans / test-driven-development；按已确认范围执行，保持同一分支与完整原型。纯技术与共享实现直接推进，新 UI 不偏离确认稿。

目标：Bilibili 视频 / 具体 cid 身份保持不变，自动明确匹配或人工选歌后补歌曲信息、专辑封面和同步歌词，支持恢复原信息并持久保存。

确认：用户于 2026-11-06 回复「确认，按此方案接入双端」，基线 `dc7316e`；范围见 `docs/superpowers/specs/2026-11-06-song-metadata-design.md`。

## 任务与文件

- [x] 共享模型与识别：新建 `metadata/SongMetadata.kt`，测试 `commonTest/.../metadata/SongMetadataTest.kt`。以录音棚、语言版、翻唱、多 P 与元信息 LRC 为用例，先运行失败测试，再实现标题 / 简介线索、唯一候选及 2 秒边界、版本兼容和毫秒解析。
- [x] 网络：新建 `metadata/SongMetadataSource.kt` 与同名定向测试。网易云匿名搜索 / 详情 / 歌词，有界响应与图片来源校验；LRCLIB 顺序、客户端标识、限流冷却，歌词失败与封面独立。MockEngine 检查准确参数、无效 / 缺失数据和 fallback；真实请求仅按显式探针执行，不持久化真实歌词正文到证据。
- [x] 状态与运行时：新建 `metadata/SongMetadataState.kt` 及测试；Bilibili 详情补简介。当前项变化触发一次自动匹配，人工打开 / 搜索 / 应用 / 恢复独立 generation，取消或旧请求不得覆盖；自动失败不打扰播放。`PlaybackRuntime` 拥有状态与网络生命周期，展示覆盖不重新加载引擎。
- [x] 缓存：`LibrarySnapshot` 加歌曲记录，`LibraryStore` schema 3 → 4 新增独立歌曲信息表。保存用户选择 / 自动结果 / 禁止自动覆盖，原视频记录不变。测试旧版本迁移、重新打开、恢复原信息及事务回滚。
- [x] UI 与系统媒体：`App.kt`、`Player.kt`、`DesktopWorkspace.kt` 和新 `ui/SongMetadataDialog.kt` 接确认的入口与选择面板；歌词 / 封面复用现有组件，有界图片失败回退视频图。AndroidSessionPlayer / WindowsMediaSession 使用同一展示元信息，实际音频 / 时长 / 队列保持来源数据。新增 more_horiz 从原型字体生成，避免图标再次不一致。
- [x] 检查与交付：定向 `:composeApp:desktopTest --tests '*SongMetadata*' --tests '*LibraryStoreTest' --tests '*PlaybackRuntimeTest' --tests '*WindowsMediaSessionTest'`，并带受影响来源 / 播放检查；按本轮用户指令不额外构建 APK / 安装包，不安装测试包。若能直接运行 Windows 已编译产物则启动供用户审核。更新完整原型确认状态、进度与验证边界，定向审查后提交推送并合并 main。

## 关键契约

```kotlin
data class SongCandidate(val id: Long, val title: String, val artists: List<String>,
    val album: String, val durationMs: Int, val artworkUrl: String? = null)
data class SongMatch(val song: SongCandidate, val lyrics: List<LyricLine> = emptyList(),
    val lyricsSource: String? = null)
data class SongMetadataRecord(val trackId: String, val match: SongMatch? = null,
    val automaticDisabled: Boolean = false)
```

展示时复制 BilibiliTrack 附加匹配结果，`item`、`part`、`id` 与真实 duration 不变；引擎与队列持有原始对象。缓存独立于来源项，恢复原信息写入 automaticDisabled。所有网络结果先检查取消与目标 ticket，再写 Compose 状态。

验收边界：单元 / 本机匿名探针与构建任务中的编译不能代替真机、实际歌词同步、网络覆盖率或完整视觉验收。未执行项保持未验收，不重跑全流程。

执行结果：6 项实现 / 检查工作已完成，99 项定向测试通过，Windows 已使用编译产物启动并连接托盘 / SMTC；源码规格及质量审查问题已修复复核。Android 本轮未平台编译 / 打包安装。详细记录见 `docs/technical/2026-11-06-song-metadata-wiring.md`；提交推送 / 合并是本轮最后交付步骤，结果以 Git 历史及最终回复为准，不另作构建或测试重复。
