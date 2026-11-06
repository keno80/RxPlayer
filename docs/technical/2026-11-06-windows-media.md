# Windows 系统媒体接线与托盘提案

日期：2026-11-06；分支：`codex/windows-media-integration`；基点：`c32ef96`。

## 技术实现

使用 Microsoft `ISystemMediaTransportControlsInterop::GetForWindow`，取得属于当前 ComposeWindow HWND 的原生 SMTC。C++/WinRT DLL 自有 C ABI，由现有 Visual Studio 2022 Build Tools 与 Windows SDK 构建，JNA 使用标准 `win32-x86-64` 资源装载，保持 Java 17。desktopJar 只包含 DLL，不打包 obj / lib / exp。安装器、最低 Windows、MSVC 运行库与最终分发继续作为发布门禁，不把本机成功写为已验证所有目标机器。

专属 MTA worker 创建 / 更新 / 释放 WinRT；ButtonPressed 只写入有界线程安全队列，由主线程调用现有 runtime。系统暂停不会切换播放意图，暂停后的系统切歌保持暂停；停止取消解析，未知命令忽略。标题 / 作者、状态、时间线和切歌可用性同步，封面最多 4MiB，通过曲目 key 拒绝过期结果。不传递音频流 URL。失败给出短提示，应用内播放继续可用；退出先取消并 join 封面工作，再撤销原生事件和释放会话。

实际锁定 mpv 的 `--list-options` 确认 `media-controls` / `input-media-keys` 默认均为 yes，启动参数已显式关闭两者，保证只有 RxPlayer 负责系统会话和命令。

## 依据

- [Microsoft 桌面 GetForWindow](https://learn.microsoft.com/en-us/windows/win32/api/systemmediatransportcontrolsinterop/nf-systemmediatransportcontrolsinterop-isystemmediatransportcontrolsinterop-getforwindow)：窗口必须属于调用进程，不使用 UWP 的 GetForCurrentView。
- [Microsoft SMTC 集成](https://learn.microsoft.com/en-us/windows/apps/develop/media-playback/integrate-with-systemmediatransportcontrols)。
- [mpv 原生媒体控制说明](https://mpv.io/manual/stable/#options-media-controls)。

## 验证

- desktopTest：WindowsMediaSessionTest 2 + PlaybackRuntimeTest 8，共 10 项通过，无失败 / 跳过；覆盖同会话元数据 / 操作、暂停切歌意图及空队列不开放控制。新增 API 测试先在实现缺失时失败，再转绿。
- 自有 `windows_media_smoke.cpp` 使用独立窗口及 GlobalSystemMediaTransportControlsSessionManager 读取本探针的标题 / 作者，发出系统 Pause / Next 并收到原生事件，实际通过；未向其他播放器发送命令。
- Java 17 构建成功，Windows `:composeApp:run` 启动，日志出现 `RxPlayer: Windows media session connected`，核对 RxPlayer 窗口进程。该记录不等于硬件按键或系统面板视觉已人工验收。
- 原型 4 项定向检查通过：托盘菜单、关闭保持播放、退出停止、重新打开恢复窗口；JS 语法与 Git 空白检查通过。两个未被精简字体收录的图标曾导致 ligature 名称显示，用户指出后替换为现有 arrow_back / close 并更新缓存。自动截图工具未完成，不宣称截图复核通过。
- 定向只读审查发现的 mpv 双系统媒体入口已修复并复查，无剩余 Critical / Important。

重跑原生探针：`pwsh -NoProfile -File tools/build-windows-media.ps1 -SourcePath <绝对路径>/windows_media.cpp -OutputPath <build目录>/rxplayer-media.dll -SmokeSourcePath <绝对路径>/windows_media_smoke.cpp`。

## 待审核提案

仍在唯一完整原型入口，使用 `?device=desktop&theme=dark&view=tray` 定位：菜单含打开 RxPlayer、播放 / 暂停、上一 / 下一曲、退出；关闭窗口收起到托盘并保持播放，明确退出才停止 / 保存 / 释放。托盘不可用时关闭即退出。此处只审核行为，正式托盘菜单采用 Windows 原生外观，不把 Material 弹层当作系统菜单的视觉规格。

**正式 Main.kt 未修改关闭行为，托盘尚未接入。** 用户确认前仅推送审核分支，不将提案标为通过或合并本批原型。下一步是确认托盘行为后接入正式桌面并集中审核。
