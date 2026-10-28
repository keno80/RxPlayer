# RxPlayer

独立实现的多音源音乐播放器，目标音源为网易云、Bilibili 和 YouTube，首批平台为 Windows 和 Android。

当前是 **Material 3 Expressive 双端界面原型**，不是已接入在线平台的音乐客户端。

## 当前能力

- Windows 与 Android 共用 Compose Multiplatform 界面。
- 显式使用 `MaterialExpressiveTheme`、`MotionScheme.expressive()`、可形变按钮、连接式 ToggleButton 来源筛选和 Expressive LoadingIndicator。
- 发现页、音乐库、关键词搜索、来源筛选、收藏、空状态。
- 明暗主题、跟随曲目切换配色、自绘几何封面。
- 小于 720dp 使用底部导航；720dp 起使用侧边导航；1100dp 起同时显示独立播放面板。
- 迷你播放器和可展开播放面板，真实播放、暂停、上一首、下一首和拖动进度。
- 内置三首 16 秒原创合成音频；Android 使用 MediaPlayer，Windows 使用 Java Sound。
- Android 退到后台暂停，关闭窗口或销毁界面释放音频资源。

**演示边界：** 网易云、Bilibili、YouTube 标签仅用于展示分类。尚未接入平台 API、登录、在线搜索、下载、系统媒体会话或后台播放服务。收藏、主题和队列为内存状态，重启后恢复默认。播放器结束后停止，不自动切歌。

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

这些步骤是待执行的验收清单，不代表已通过运行验证：

1. Windows 启动后，将窗口在 1200、900、400dp 附近缩放，检查侧栏、播放面板与底部导航的切换。
2. Android 在竖屏、横屏、深浅色模式下检查安全区、滚动和底部弹层；放大系统字体检查内容是否可访问。
3. 搜索“夜航”，切换来源，清空搜索；验证没有匹配内容时可以恢复列表。
4. 收藏曲目，在音乐库查看，再取消收藏；退出重开后应恢复默认状态。
5. 播放三首内置音频，暂停、继续、切换、拖动进度到开头及结尾；快速切歌应只保留最后一次请求。
6. 切换深浅主题和“跟随曲目配色”，观察按钮按压形变、来源选中形变和配色。
7. Android 按 Home 后声音停止，返回后不自动播放；退出 Windows 后声音停止。
8. 无音频输出设备时应显示播放错误，允许重新点击播放重试。

LoadingIndicator 仅在真实资源准备期间显示，准备很快时可能不易观察，不人为延迟播放。

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

按当前任务约定未运行 Gradle 构建、编译测试、Android 模拟器或 Windows 应用；尚未证明双端可运行。

已完成：TOML / XML 解析、曲目与资源引用核对、三段 WAV 的时长 / PCM 格式 / 波形无削波检查、Gradle Wrapper 官方 SHA256 校验、AGENTS.md 与用户原文件逐字节比对，以及 `git diff --cached --check`。同时核对所锁定 Material3 发布包的 Expressive API 签名。以上均为静态检查，实际设备验收按上方清单执行。
