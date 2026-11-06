# Windows 系统媒体与托盘接线

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

## 托盘确认与正式接线

仍在唯一完整原型入口，使用 `?device=desktop&theme=dark&view=tray` 定位：菜单含打开 RxPlayer、播放 / 暂停、上一 / 下一曲、退出；关闭窗口收起到托盘并保持播放，明确退出才停止 / 保存 / 释放。托盘不可用时关闭即退出。此处只审核行为，正式托盘菜单采用 Windows 原生外观，不把 Material 弹层当作系统菜单的视觉规格。

用户于 2026-11-06 以“开始吧”确认托盘行为并授权正式接线。已使用 JDK AWT SystemTray / TrayIcon / PopupMenu 接入原生菜单，双击图标或选择“打开 RxPlayer”恢复并激活窗口，播放 / 暂停及切歌操作现有 runtime。空队列禁用播放，少于两曲禁用切歌，等待中的播放意图显示“暂停”；退出期间禁用菜单操作。托盘不支持或安装失败时关闭仍会退出，避免隐藏后无法恢复。

桌面 runtime 提升到 application 生命周期，Window 只改变 visible，隐藏不会销毁播放或页面上下文。明确退出先暂停，再取消并等待生产协程；其 finally 等待 SMTC / 封面任务关闭，再由 runtime.close 保存快照并释放引擎和来源连接，完成后退出应用。共享 App 支持桌面传入 runtime，Android 仍默认使用服务会话，保留主题尾随回调签名。

新增 DesktopTrayTest 2 项（等待意图、退出禁用、空 / 单曲队列），与 WindowsMediaSessionTest 2 + PlaybackRuntimeTest 8 共 12 项通过。测试任务包含必要桌面编译；按用户最新约定，不额外运行安装包构建，不安装 Android 或测试 APK。原生托盘菜单与完整隐藏 / 退出、硬件媒体键和系统面板视觉仍待用户审核，不能以单测代替。

签名修复后 12 项复测通过，静态复查无剩余 Critical / Important。随后直接启动已编译产物，编译任务均为 UP-TO-DATE，日志确认 `Windows tray connected` 与 `Windows media session connected`；只确认启动和连接，没有继续逐页或硬件交互回归。

托盘 API 依据：[Oracle SystemTray](https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/java/awt/SystemTray.html)、[Oracle TrayIcon](https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/java/awt/TrayIcon.html)。菜单使用系统外观，品牌状态图标由现有青绿色和音频条生成，不新增 UI 方案。

## 托盘中文方框修复

用户随后截图发现中文均显示方框、Latin 正常。此前 PopupMenu 没有指定字体（探测 getFont 为 null）；JDK Windows 菜单默认使用 SansSerif，而本机 win.menu.font 为 Microsoft YaHei UI。已在创建子项前显式设置系统菜单字体，子项继承；属性不可用时使用逻辑 Dialog 字体。此处恢复既有中文文案，不改变原型或交互。

使用 Unicode 转义避免命令管道编码影响，探测打开、播放、暂停、上一曲、下一曲及退出共 6 种标签，全部继承系统字体，canDisplayUpTo 均为 -1；DesktopTrayTest 2 项通过，Git 空白检查、定向审查通过。该证据只确认字体 / 字形及状态逻辑，原生菜单实际显示仍待用户审核。

依据：[OpenJDK Windows 默认菜单字体](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.desktop/windows/classes/sun/awt/windows/awtLocalization.properties)、[Oracle MenuComponent 字体继承](https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/java/awt/MenuComponent.html)。

### 后续纠正：绕过 AWT 菜单绘制

用户再次截图确认上述字体设置未解决实际方框，上一轮字形检查不能视为显示验收。临时诊断实际运行进程：标签“打开”的码点为 6253 / 5f00，菜单字体确为 Microsoft YaHei UI，canDisplayUpTo 为 -1；Windows GDI 的同字体 GetGlyphIndicesW 也成功返回全部 14 个中文字形。因此输入内容及 Windows 字形存在，失败局限在 AWT 菜单绘制路径；未宣称已定位某个具体 JDK 缺陷。

正式菜单改为现有 JNA 调用 CreatePopupMenu / AppendMenuW，使用 WString 和系统绘制字符串，不设置 MF_OWNERDRAW；保留 AWT TrayIcon 及所有既有操作。专属 worker 创建隐藏 owner 和阻塞菜单循环，应用 EDT 继续处理播放轮询、SMTC 和保存，选择结果回 EDT 分派；关闭时取消菜单并禁止晚到命令，HMENU / owner 在 finally 释放。播放 / 暂停命令取打开菜单时的明确意图，避免菜单展开期间系统状态变化后 toggle 反向操作。

新增原生菜单测试实际读回全部中文文案，检查禁用状态和没有 owner-draw 标志；既有媒体测试增加重复 Play / Pause 幂等断言。定向静态审查修复了主线程阻塞和标签 / toggle 竞争，复查无剩余 Critical / Important。本轮仅针对菜单，不扩 Android 或完整回归。

尝试通过 computer-use 的窗口状态检查最小 AWT 对照窗口，但激活失败、截图只取得背景，未取得可用的菜单视觉证据。临时探针及日志已移除，原生显示、外部点击取消及隐藏后恢复仍由用户审核，不把字形 / 原生数据测试写成视觉已通过。

依据：[Microsoft AppendMenuW](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-appendmenuw)、[TrackPopupMenu](https://learn.microsoft.com/en-us/windows/win32/api/winuser/nf-winuser-trackpopupmenu)、[OpenJDK AWT 菜单 owner-draw 链路](https://raw.githubusercontent.com/openjdk/jdk17u/master/src/java.desktop/windows/native/libawt/windows/awt_MenuItem.cpp)。
