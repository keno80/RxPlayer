# Bilibili 正式来源核心

本批完成共享来源代码与双端验证，不代表 M2 在线播放闭环完成。未改正式 UI，也未将真实内容伪装为 DemoTrack / 本地音频。

## 交付范围

- `source/ContentItem.kt`：来源与稳定 ID、标题、作者、封面、时长、分 P、瞬时音频输入；AudioStream 的字符串表示隐藏 URL，内容身份不包含签名地址。
- `source/BilibiliSource.kt`：匿名内存会话、搜索分页、BV / AV / HTTPS 视频链接识别、分 P 元数据、AAC DASH 解析；区分内容不可用、明确登录要求、风控、限流、网络与响应失败。HTTPS 响应上限 4 MiB，取消继续传播，无无限重试或请求日志。
- 两平台 `SourceHttpEngine`：OkHttp；Android 仅增加普通 INTERNET 权限。固定 Ktor 3.3.1，其[发布说明](https://ktor.io/docs/releases.html)明确对应 Kotlin 2.2.20；JSON 解析使用 kotlinx.serialization 1.9.0。

当前搜索路径为本批实测成功的网页内部 `/x/web-interface/search/type`，不是平台公共稳定 API 承诺。WBI、b23 短链接、真实登录 / 权限与恢复、封面加载等仍需后续实现，不能据匿名样本宣称全部内容可播放。

## 定向验证

| 层级 | 实际结果 |
|---|---|
| 模拟 / 边界 | 10 项通过；搜索参数和分页、标题高亮、AV / BV / 链接 / 分 P、错误分类、响应上限、非法媒体目标、HTTPS 8082 |
| Windows 正式来源真请求 | [1 项通过](../evidence/m1/2026-10-30-kotlin-bilibili-live.json)：17 条有效视频结果、36 分 P、前两 P 的 AAC / MP4 均取得 206 / 65536 字节与 ftyp 文件头 |
| Android API 37 正式来源真请求 | [1 项通过](../evidence/m1/2026-10-30-kotlin-bilibili-android.json)：相同来源类与手机当前网络下，17 条结果、36 分 P、两 P 音频字节读取通过；无 USB 代理转发 / 系统设置修改 |
| 构建 | `desktopTest` 定向 11 项 + `desktopJar` + `androidApp:assembleDebug` 成功，24 秒；Android instrumentation APK 也构建成功 |
| 启动 | Windows 新版本窗口确认；Android 正式 APK 重装并启动成功，临时测试包和私有测试报告已清理 |
| 独立源码审查 | 无已发现的 Critical / Important 问题；审查不替代上述实际运行 |

首次真请求在音频解析阶段失败，搜索与分 P 已成功，见 [失败证据](../evidence/m1/2026-10-30-kotlin-bilibili-cdn-port-failure.json)。定位到实际 MCDN 返回合法 HTTPS `:8082`，被我们的固定 443 规则误拒；修正为验证 HTTPS、无用户凭据和 CDN 域名后缀，并增加专门回归测试。未通过更换内容、账号或伪造成功规避问题。临时诊断输出已移除。

Android instrumentation 首次安装被手机安全确认拦截，实际执行 0 项；经用户允许后重试。Gradle 失败清理曾移除目标 APK，已恢复正式应用，之后直接运行 instrumentation，返回 `OK (1 test)`，再卸载测试包；不将安装失败阶段记为设备测试通过。

## 复现

Windows：设置 `RXPLAYER_BILIBILI_PROBE=1`，可用 `RXPLAYER_BILIBILI_REPORT` 指定脱敏报告路径；执行 `:composeApp:desktopTest --tests 'com.keno.rxplayer.source.*'`。未显式 opt-in 时真请求测试跳过，模拟测试保持离线。

Android：先构建 `:androidApp:assembleDebug :androidApp:assembleDebugAndroidTest`，安装正式 / 测试 APK 后，单独执行 `adb shell am instrument -w -e class com.keno.rxplayer.BilibiliSourceDeviceTest -e runSourceProbe true com.keno.rxplayer.test/androidx.test.runner.AndroidJUnitRunner`。读取应用私有的 `files/bilibili-source-probe.json` 后清理测试包与该报告，重新启动正式应用；不清除正式应用数据。

## 下一步

将来源核心接入经确认的搜索结构与流媒体接口，再完成实际解码、队列和收藏 / 重启恢复。当前 App 仍为本地演示入口；原有四格式解码探针证据不能代替这批正式来源代码的产品播放验收。M1 其他来源 / 发布门槛及 M2 完整目标仍未通过。
