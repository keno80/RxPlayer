# RxPlayer Expressive 技术与组件验证记录

定位修正：本文件记录此前用于启动与组件能力验证的 Compose 样例，历史文件名沿用。用户未确认该样例为产品原型；产品原型设计与审核另见 [原型 01 审核说明](../specs/2026-10-28-product-prototype-v1.md)。

目标：独立实现 Windows、Android 共用的小型 Material 3 Expressive 界面，先验证设计系统和本地音频交互，再开展在线音源接入。

本文保留技术验证阶段记录。整体范围与后续阶段以 [总需求](../../requirements.md) 和 [分阶段开发计划](../../development-plan.md) 为准。

采用已在会话中确定的 Kotlin Multiplatform / Compose Multiplatform 路线。沿用仓库 AGENTS.md 的简洁实现原则，不建立在线音源、账号或后台服务框架。

- [x] 将用户指定的 AGENTS.md 原样复制到仓库根目录，单独提交并推送。
- [x] 创建 Gradle Wrapper、共享 Compose 模块与 Android 应用模块，锁定含 Expressive API 的 Material3 版本。
- [x] 实现发现页、音乐库、搜索筛选、收藏、明暗主题和曲目配色。
- [x] 实现手机底部导航、桌面侧栏和宽屏播放面板。
- [x] 使用真实 Expressive 主题、动效方案、按钮形变、连接式切换按钮和加载指示器。
- [x] 生成原创 PCM 音频，实现 Android MediaPlayer / Windows Java Sound 播放适配、取消准备和释放资源。
- [x] 编写启动说明、原型边界与手动验收步骤。
- [x] 用户明确要求启动后，Android 已构建、安装真机并启动，Windows 已编译并打开窗口。
- [x] 完成双端部分视觉检查和源码核对，记录设计改进项。
- [ ] 完成 README 中剩余交互验收。启动成功不等于全部场景通过；界面检查因用户按 Esc 停止，键盘 / 展开播放器、窄窗口和完整播放交互仍需核实。

检查方式：核对所锁定依赖的发布元数据与 API 源码；检查 Gradle Wrapper 校验和、XML / TOML 语法、音频格式及资源引用；运行 `git diff --check`。静态检查不代替编译和设备验收。

更新日期：2026-10-28。后续构建按用户具体授权执行；本次只更新规划与记录，不额外构建。
