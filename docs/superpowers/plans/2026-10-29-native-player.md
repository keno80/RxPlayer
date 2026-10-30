# 已确认播放页原生实现计划

目标：将 `774a6d6` 正常布局和 `608ddb2` 状态反馈落实到 Android / Windows 共用 Compose 播放页，不扩展未审核页面或在线音源范围。

执行方式：本会话顺序实施。已有原型确认和原生规格作为设计批准，不重复设计审批。用户默认不构建，因此不运行 Gradle / Kotlin 编译或依赖编译的测试；先编写状态测试，保留未执行标记，不声称 TDD 红绿循环已通过。

## 文件与步骤

- [x] `commonTest/.../audio/PlaybackStateTest.kt`：先写取消准备、快速切换、队列末尾停止、恢复播放意图及错误通知等 9 个测试；使用内存适配器与注入资源读取。未执行测试。
- [x] `audio/PlaybackState.kt`、`AudioPlayer.kt` 及平台适配器：统一阶段 / 队列 / 模式 / 音量与一次性通知；真实加载和引擎事件驱动，取消旧请求并释放旧资源。
- [x] `ui/Artwork.kt`、`ui/Theme.kt` 与本地图片资源：复用确认封面及 MCU 配色，播放器嵌套内容主题，导航保持品牌色。
- [x] `ui/Player.kt`、`ui/PlayerQueue.kt`：两套手机布局、桌面左右栏、唯一队列、轨道等待、异常 toast 及末尾停止源码。
- [x] `App.kt` 及平台入口：全页播放器、真实 Insets、返回 / Esc、空格及焦点源码；旧列表和收藏保持既有能力。
- [x] 静态检查、确认基线对照、源码审查和未执行验收记录已完成。没有把源码交付记成构建、启动或真机验收通过。
- [x] `690adce` / `babf0ca` 已提交推送，通过 `1010204` 合并并推送至 `main`。
- [ ] 用户要求运行后，执行新增测试、双端编译 / 启动和下方原生交互验收。

## 后续运行验收命令（本次不执行）

```powershell
./gradlew.bat :composeApp:desktopTest
./gradlew.bat :composeApp:compileKotlinDesktop :composeApp:compileDebugKotlinAndroid
```

运行验收：双端两套布局、队列宽窄切换、真实准备取消 / 快速切歌 / 释放、暂停与拖动、末尾及循环、短屏 / 2 倍字体、状态栏与返回、键盘和读屏。在线缓冲与链接恢复需要后续流媒体适配器，不能用本地 WAV 成功代替。
