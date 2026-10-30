# 双端播放页运行验收

日期：2026-10-29。用户已明确授权启动 Android / Windows 并进行实际验收。本轮复用 `codex/native-player`，不把原型截图或旧样例运行结果用作当前版本证据。

## 环境与初始状态

- JDK：既有 Temurin 17，Gradle 使用项目 Wrapper。
- Android SDK：既有 `ANDROID_SDK_ROOT`；手机连接正常，API 37，物理分辨率 1236 × 2750。
- 手机初始字体比例 1.0，自动旋转 0、方向 0；若为验收临时调整，结束恢复这些值。
- Windows 开始时只有浏览器原型，没有旧的原生 RxPlayer 窗口。

## 构建与测试

命令：`:composeApp:desktopTest :androidApp:assembleDebug :composeApp:desktopJar`。

- 首次构建失败：`Player.kt` 在嵌套 `Column` 中隐式访问外层 `BoxWithConstraints.maxWidth`，触发 Compose DSL receiver 限制；Android / Desktop 均报告同一处类型检查错误。
- 已修正：在外层读取 `availableWidth`，嵌套布局使用该值。属于已确认设计的等价实现修正，不改变断点或布局。
- 第二次构建进一步发现 JVM 签名冲突：`volume` 的 setter 与 `setVolume()` 同名，以及 common / actual 文件产生同名 facade。分别将状态命令改名 `changeVolume()`、平台文件使用 `.android.kt` / `.desktop.kt` 后重跑。
- 修复后两端构建成功，9 个状态测试执行通过。随后为 Android 结束回调的毫秒尾差扩展既有测试，先复现断言失败，再将真实结束事件的显示进度对齐总时长；全量 9 个测试复测通过。

## 实际交互

- 双端均已实际启动。初次运行发现下一首被主按钮挤出：锁定 Material3 的 Tooltip 将传入 modifier 放在内部锚点，导致 Row 的 weight 没有作用到直接子节点。已把尺寸 / 权重放到外层 Box，重新构建后在两端确认下一首可见。
- Android API 37：16 项核心 UI 检查通过，见 `docs/evidence/native-player-runtime/android-core-results.json`。覆盖五按钮同排 / 触控目标、播放暂停和进度、拖动与暂停位置稳定、单队列、快速选择最后一首生效、无歌词分区卡、真实队列末尾静默停止、移除数量同步、返回队列 / 返回原页及主题后的歌词恢复。
- Windows：已观察到本地曲目真实进度、自然推进 / 末尾停止、独立全页、下一首、单队列替换右栏和选曲；尚未完成尺寸 / 键盘全量验收。
- 用户指出桌面模式及音量行被底边裁切，已调整为控制区保留在左栏底部，封面随可用高度缩小，短窗口只滚动封面 / 曲目信息；修复版构建成功，实际尺寸复测尚未完成。
- 设备状态：手机调试连接随后断开，电脑进入锁屏；未继续输入锁屏界面。正在等待用户恢复设备，再检查最终修复版、大字体、横屏和键盘。字体比例调整命令因设备离线而失败，没有修改原始 1.0 / 0 / 0 设置。

真实网络缓冲 / 权限 / 地址恢复继续依赖后续在线引擎适配，不以本地 WAV 或类型模拟检查宣称其通过。
