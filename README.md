# RxPlayer

独立实现的多音源音乐播放器，目标音源为网易云、Bilibili 和 YouTube，首批平台为 Windows 和 Android。

当前已将用户确认的双端播放页与状态交互落实为 Compose 源码，**本轮尚未构建、启动或完成原生交互验收**。列表、搜索和音乐库仍沿用技术样例；在线音源尚未接入正式应用。

设计依据为 [产品交互原型 03](design/prototype-v3/index.html)：正常播放布局基线 `774a6d6`、播放状态基线 `608ddb2` 已获确认。搜索、音乐库和设置完整设计仍待审核；使用方式与边界见 [原型 03 说明](design/prototype-v3/README.md)。

## 产品需求与开发规划

- [当前进度](docs/progress.md)：当前阶段、已完成内容、未验收项和下一步。
- [总需求](docs/requirements.md)：已确认要求、首版范围建议、三音源能力、Expressive 设计与验收条件。
- [分阶段开发计划](docs/development-plan.md)：M0 原型至 M5 首版交付的顺序、依赖和退出条件，以及首版后的扩展。
- [产品原型 03 审核说明](docs/superpowers/specs/2026-10-28-product-prototype-v3.md)：本轮设计提案、审核范围和确认流程。
- [技术验证历史记录](docs/superpowers/plans/2026-10-28-expressive-prototype.md)：已有 Compose 样例的实施与验证记录，不代表产品设计获批。

三音源、Windows / Android 和 Material 3 Expressive 是已确认要求；其余首版范围及阶段顺序以文档中标明的规划建议为准。

## 当前技术验证样例的能力

- Windows 与 Android 共用 Compose Multiplatform 界面。
- 显式使用 `MaterialExpressiveTheme`、`MotionScheme.expressive()`、状态形变按钮、连接式 ToggleButton 来源筛选；播放等待仅在原进度条显示不定缓冲效果。
- 发现页、音乐库、关键词搜索、来源筛选、收藏、空状态。
- 明暗主题、品牌导航配色、已确认原创封面及其 MCU 派生的播放器配色。
- 迷你播放器展开为独立全页：Android 有歌词使用压暗贴边封面与底部控制，无歌词使用分区卡；Windows 左侧播放控制、右侧歌词，宽窗口队列替换同一右栏。
- 单一队列、移除、顺序 / 随机 / 列表循环、音量、取消准备、重试及恢复位置，播放 / 暂停 / 拖动由现有本地音频引擎驱动。
- 播放错误使用单条短提示，顺序播放到队列末尾直接停止；窗口缩放和队列开闭不重建引擎。
- 内置三首 16 秒原创合成音频；Android 使用 MediaPlayer，Windows 使用 Java Sound。
- Android 退到后台暂停，关闭窗口或销毁界面释放音频资源。

**演示边界：** 网易云、Bilibili、YouTube 标签仅用于展示分类。尚未接入平台 API、登录、在线搜索、下载、系统媒体会话或后台播放服务。收藏、主题和队列为内存状态。夜航歌词是显式的本地演示夹具，不是音频识别或在线歌词；其余曲目展示无歌词布局。缓冲 / 失效 / 权限状态已定义引擎契约，真实在线状态和地址重新解析仍需后续适配器接入，不使用假等待伪装实现。

## 环境

- JDK 17，设置 `JAVA_HOME`。
- Android Studio / Android SDK：Android SDK Platform 36、Build Tools 36.0.0，Android 8.0 / API 26 及以上设备或模拟器。
- Windows 10/11（64 位）。开发运行无需安装额外音频服务。
- 首次使用 Gradle Wrapper 需要网络下载 Gradle 和 Maven 依赖。
- 设置 `ANDROID_HOME`，或在本地 `local.properties` 中配置 `sdk.dir`；该文件不提交。

版本锁定在 `gradle/libs.versions.toml`：Kotlin 2.2.20、Compose Multiplatform 1.9.3、Material3 1.9.0-alpha04、AGP 8.11.2、Gradle 8.14.3。

Material3 单独锁定，是因为该 Expressive 发布包含所需 API；不能直接替换成默认 stable 别名。其 Android 变体依赖 Jetpack Material3 1.4.0-alpha17，两端均需保留 Expressive API。参考 [JetBrains 官方说明](https://kotlinlang.org/docs/multiplatform/whats-new-compose-190.html#material-3-expressive-theme)。

## 启动 Windows

在仓库根目录执行：

```powershell
.\gradlew.bat :composeApp:run
```

首次 Gradle 配置也会解析 Android 模块，请先完成 SDK 配置。

## 启动 Android

用 Android Studio 打开仓库根目录，完成 Gradle 同步，选择 `androidApp` 配置及设备运行。也可执行：

```powershell
.\gradlew.bat :androidApp:assembleDebug
```

APK 输出：`androidApp/build/outputs/apk/debug/androidApp-debug.apk`。

## 手动验收

以下是技术验证样例的交互检查清单。双端已完成构建与启动，清单中的完整交互场景尚未全部检查；这些记录不代替产品原型审核，后续成品验收以总需求为准：

1. Windows 播放并展开播放器，将窗口在 1200、900、400dp 附近缩放，检查左右两栏 / 纵向重排和队列停靠 / 模态切换，始终只有一份队列。
2. Android 在竖屏、横屏、深浅色模式下检查安全区、滚动和底部弹层；放大系统字体检查内容是否可访问。
3. 搜索“夜航”，切换来源，清空搜索；验证没有匹配内容时可以恢复列表。
4. 收藏曲目，在音乐库查看，再取消收藏；退出重开后应恢复默认状态。
5. 播放三首内置音频，暂停、继续、切换、拖动进度到开头及结尾；快速切歌应只保留最后一次请求。
6. 切换深浅主题和“跟随曲目配色”，观察按钮按压形变、来源选中形变和配色。
7. Android 按 Home 后声音停止，返回后不自动播放；退出 Windows 后声音停止。
8. 无音频输出设备时应显示播放错误，允许重新点击播放重试。

进度条缓冲效果只在引擎等待期间显示，准备很快时可能不易观察，不人为延迟播放。普通播放与暂停没有提示卡，错误 toast 消退后仍可通过主按钮重试。

## 目录

```text
androidApp/                         Android 入口与生命周期
composeApp/src/commonMain/          共享界面、主题、曲目和播放状态
composeApp/src/androidMain/         Android 原生音频适配
composeApp/src/desktopMain/         Windows 入口与 Java Sound 音频适配
scripts/generate_demo_audio.py      可复现的原创音频生成脚本
```

本阶段自行实现，不包含所参考播放器的源代码或品牌素材。音频和封面为本项目生成。

## 验证状态

截至 2026-10-28：

以下构建 / 启动事实属于旧样例；2026-10-29 新增播放页源码尚未重新构建和运行。状态测试已编写但未执行，不能将静态语法检查当作测试或链接通过。

- 已完成 TOML / XML、资源引用、WAV 格式与无削波检查、Gradle Wrapper 官方 SHA256、AGENTS.md 原文比对及 Git 差异检查；已核对锁定版本的 Expressive API。
- 在用户明确要求启动后，Android Debug 构建成功，APK 已安装到真机并进入前台，当时未发现启动崩溃。
- Windows 桌面目标已成功编译并打开 RxPlayer 窗口。
- 双端已进行部分视觉检查与源码核对，发现导航、播放状态、圆角和桌面空格交互等问题，见 [开发计划的问题追踪](docs/development-plan.md#10-当前问题与需求追踪)。
- 界面检查因用户按 Esc 停止。手机键盘 / 展开播放器、桌面窄窗口和完整播放交互仍未完成验收。

构建和启动成功不等于全部功能验收通过。本次规划文档更新未重新构建或操作设备；后续继续遵守用户默认不自动构建的约定。
