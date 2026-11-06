# 多来源同步歌词接线

- 日期：2026-11-06
- 分支：`codex/lyrics-sources`
- 用户要求：QQ 音乐 → 酷狗 → 网易云 → LRCLIB。
- 范围：共享同步歌词获取；沿用已确认的匹配入口、显示及恢复行为，不新增播放音源或 UI。

## 实现

`SongMetadataSource.load` 仍先补全网易云候选详情 / 封面，再依序请求 QQ、酷狗、网易云、LRCLIB 的同步歌词。详情请求不属于歌词优先级。当前来源返回可解析的时间戳记录后停止；空结果、纯文本、非法响应、接口异常或无可靠匹配时继续下一个来源，取消则立即终止整条链路。

- QQ：`u.y.qq.com/cgi-bin/musicu.fcg` 的 `music.search.SearchCgiService / DoSearchForQQMusicMobile` 搜索，使用 `ct=11`、`v=20080508` 而不传 `cv`；从 `body.item_song` 获取候选。唯一匹配后通过 `c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg` 的 `songmid` 和 `nobase64=1` 获取 LRC。必要的 QQ Referer 仅发给 QQ。
- 酷狗：`lyrics.kugou.com/search`，关键词为“歌手 - 歌名”，时长单位毫秒；从 `candidates` 选唯一匹配，使用其 `id / accesskey` 请求 `/download?fmt=lrc`，Base64 解码并严格校验 UTF-8。
- 原有网易云 LRC、LRCLIB 串行限速和 429 冷却继续保留。
- 标题仅规范化标点 / 空格并去掉 QQ 的 `<em>` 高亮，不删除括号或未知录音限定。核对完整歌手集合、已知版本及 ±2 秒时长；QQ 另检查 `subtitle` 中的版本标记。多个符合项仍视为歧义，不选第一项。
- 所有请求匿名，仍使用 1 MiB 响应上限、现有超时和脱敏异常；不部署第三方代理，不读取或保存登录凭据。

歌曲候选和专辑封面仍来自既有网易云资料链路。本批不改变资料匹配缓存或用户选择：已有缓存继续显示，重新应用歌曲版本时按新优先级获取歌词；不强制清空用户手选版本。视频 / cid、音频解析、队列及播放位置保持原身份。

## 验证

先增加测试，旧实现下新来源优先级 / 取消用例失败；接线后定向验证通过。最终实际运行：

```powershell
$env:JAVA_HOME = "$env:USERPROFILE/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2"
$env:RXPLAYER_METADATA_LIVE_TEST = '1'
./gradlew.bat :composeApp:desktopTest --tests '*LyricsSources*' --tests '*SongMetadata*' --console=plain
```

38 项通过、0 失败、0 跳过：新来源行为 7 项、新真实网络 2 项、既有资料网络 / 识别 / 状态 / 集成 29 项。覆盖优先级及提前停止、版本与完整歌手、边界时长和歧义、异常 / 无同步歌词回退、非法 Base64 / UTF-8 及取消。

真实样本结果仅记录数量，不保存歌词正文：

- 《晴天》：完整 Kotlin 生产链路优先取得 QQ 同步歌词，63 条时间戳记录，网易云歌曲身份保持不变。
- 相同样本注入“QQ 无匹配”，酷狗搜索 / 下载均为真实网络请求，共 2 次，取得 64 条时间戳记录。此项仅 QQ 空结果及网易云详情由测试替代，不称为全真实四源链路。
- 原 Bilibili → 选歌 → 封面 / 歌词集成样本仍取得 62 条记录和可解码封面，原视频身份保持不变。
- 首次后续 QQ 请求曾返回 `2001` 限流，应用正确回退酷狗；冷却后最终测试通过。没有在应用中加入即时重试或规避限流。接口可用性取决于上游，单一样本不代表全曲库覆盖。

已按代码审查技能做只读定向复核，无 Critical / Important 问题；`git diff --check` 通过。未额外构建 / 安装或启动原生双端，未安装独立测试 APK，Android 平台及实际听感同步留给集中验收。

## 技术资料

后续用户明确要求构建并安装双端，已在 `cad7960` 执行 `:composeApp:desktopJar :androidApp:assembleDebug`，构建通过；Windows `:composeApp:run` 重新启动且托盘 / SMTC 连接，Android `adb install -r` 返回 Success，Mi 10 启动 MainActivity 返回 Status: ok，进程存在。未安装独立测试包。Windows 使用更新后的开发运行产物，未打包 MSI / EXE；原生视觉与听感同步仍由用户审核。

本次匿名探测发现 QQ 旧 Desktop 搜索返回空、酷狗 `/v1/search` 返回非 JSON，因此不把这两个入口接入当前实现。移动搜索及经典歌词入口在本次样本可用。

- [Lyricify QQ 接口维护源码](https://github.com/WXRIW/Lyricify-Lyrics-Helper/blob/master/Lyricify.Lyrics.Helper/Providers/Web/QQMusic/Api.cs)：移动搜索协议和返回结构。
- [Lyricify 酷狗接口维护源码](https://github.com/WXRIW/Lyricify-Lyrics-Helper/blob/master/Lyricify.Lyrics.Helper/Providers/Web/Kugou/Api.cs)：经典歌词搜索入口。
- [酷狗 LRC 下载维护源码](https://github.com/MakcRe/KuGouMusicApi/blob/main/module/lyric.js)：下载参数与 Base64 LRC 格式。
- [QQ 歌词接口参考](https://github.com/copws/qq-music-api)：Web LRC 入口。

这些是技术协议参考，不是官方稳定性承诺，也未用于 UI 设计。
