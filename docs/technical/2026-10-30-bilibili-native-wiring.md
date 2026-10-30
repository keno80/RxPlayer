# Bilibili 双端前台接线记录

日期：2026-10-30。用户明确确认原型 `f48cf05`：“确认这版，继续正式接线”。范围为前台搜索、分 P 播放与队列，不包含完整 M2 的数据库 / 后台 / 系统集成。

## 实现

- 真实来源使用 `ContentItem`、`VideoPart`、`BilibiliTrack`；队列身份为来源 + BV + cid，临时带签名 URL 仅在播放前解析，不作持久身份。
- 双端搜索页支持关键词分页、BV / AV 及标准 HTTPS 视频链接。其他来源保持禁用，无演示结果兜底。首条 / 唯一结果同步原型补齐相同的更多操作入口，保证链接结果也能选 P 入队。
- 单 P 直接执行对应动作；多 P 先选择，再播放 / 入队 / 设为下一首。取消不调用播放操作；已有条目设下一首会重排而非重复添加。
- Windows 保留第三种浮岛及唯一队列；Android 使用同一来源状态、原生分 P 对话框和已确认播放页。真实封面加载失败使用中性图标；从封面像素提取种子，通过 Material Color Utilities 生成成对颜色角色。
- HTTP 输入接入 Android Media3 1.8.0 与 Windows mpv JSON IPC；支持暂停、进度、音量、缓冲及安全故障。准备 / 缓冲 / 重新取址沿用进度轨道，异常短提示，队列末尾结束停止。
- 自动重新取址限一次，保留当前身份和位置；恢复任务与进度轮询分离，用户取消或切曲不会停止长期同步。
- 当前收藏、队列及设置均为内存状态，关闭应用会丢失。暂无真实歌词；不注入原型歌词。

## 实际验证

| 检查 | 结果与范围 |
| --- | --- |
| 桌面测试 | 25 项通过，1 项旧来源探针未开启而跳过；其中 24 项状态 / 来源 / 分 P 离线测试，1 项本批真实引擎测试 |
| Windows 真流媒体 | 搜索 17 个结果；固定视频 36 P，前两 P 的 cid 不同并均解码、推进进度；暂停、5 秒 seek、队列去重与下一首通过 |
| Windows 引擎生命周期 | 1 秒 WAV 自然结束可识别；杀掉测试 JVM 唯一 mpv 子进程后，后续加载成功重建 |
| Android API 37 真机 | 正式来源 + Media3 前两 P 播放、暂停、seek、队列去重与下一首，1 项 instrumentation 通过 |
| 构建 | `:androidApp:assembleDebug`、`:composeApp:desktopJar` 成功；同批独立测试 APK 构建成功 |
| 启动与界面 | 正式 Android 包安装启动并从界面选 P2 播放、暂停，最终封面 / 标签 / 两行标题已复核；Windows 最终版从首条结果菜单选 P 入队，首次入队即显示浮岛，可直接启动且观察到进度推进。完整视觉 / 多 DPI / 无障碍回归未扩展 |

脱敏证据：[Windows](../evidence/bilibili-wiring/windows-playback.json)、[Android](../evidence/bilibili-wiring/android-playback.json)。Android 最终播放页：[截图](../evidence/bilibili-wiring/android-player.png)。报告不包含签名 URL、请求头或账号信息。

## 发现及修复

定向只读审查提出并修复：自动恢复取消终止轮询、首条结果菜单不可达、首次入队无入口、已有条目“下一首”无效，以及 mpv EOF 丢失、失效管道复用、释放遗留排队加载。复查无 Critical / Important 项。

Android 首次真实测试在完成播放断言后的释放阶段触发 `NetworkOnMainThreadException`，将 OkHttp 连接清理移到 IO 后重跑通过。最后定向修正真实内容仍显示“本地演示”及长标题过度换行，并重新构建、启动。

运行验收期间曾覆盖旧 Windows 进程正在使用的编译目录，引发旧类文件缺失；已停止旧实例后重新启动最终产物。后续避免一边编译覆盖同一输出目录，一边将旧进程当作新版本验收。

## 开发运行与剩余门禁

Windows `run` 依赖 `prepareDesktopEngine`，从已验证缓存或固定归档取得 mpv，并检查 SHA-256；未使用系统 PATH 搜索其他播放器。版本及 hash 沿用 [M1 溯源](../evidence/m1/2026-10-29-runtime-provenance.json)，不静默换版本。该任务服务开发运行，最终安装包的许可证 / 源码配套及干净机器验收仍待完成。

后续为数据库持久化与重启恢复、Android 后台媒体服务 / 通知 / 锁屏、Windows 系统媒体键 / 托盘。账号、WBI / 短链、最低 Android 26 / R8 及 YouTube 自然到期验证仍保持未验收；不将本批前台链路写成完整 M1 / M2 通过。

实现依据：[Android Media3 媒体来源](https://developer.android.com/media/media3/exoplayer/media-sources)、[网络栈](https://developer.android.com/media/media3/exoplayer/network-stacks)、[mpv JSON IPC 与属性](https://mpv.io/manual/master/)、[MaterialKolor 的 Google 颜色工具跨平台实现](https://github.com/jordond/MaterialKolor)。UI 仍以 Google M3 / M3 Expressive 和项目技能为基线。
