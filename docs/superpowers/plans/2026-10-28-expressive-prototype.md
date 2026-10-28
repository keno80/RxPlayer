# RxPlayer Expressive 原型实现计划

目标：独立实现 Windows、Android 共用的小型 Material 3 Expressive 界面，先验证设计系统和本地音频交互，再开展在线音源接入。

采用已在会话中确定的 Kotlin Multiplatform / Compose Multiplatform 路线。沿用仓库 AGENTS.md 的简洁实现原则，不建立在线音源、账号或后台服务框架。

- [x] 将用户指定的 AGENTS.md 原样复制到仓库根目录，单独提交并推送。
- [x] 创建 Gradle Wrapper、共享 Compose 模块与 Android 应用模块，锁定含 Expressive API 的 Material3 版本。
- [x] 实现发现页、音乐库、搜索筛选、收藏、明暗主题和曲目配色。
- [x] 实现手机底部导航、桌面侧栏和宽屏播放面板。
- [x] 使用真实 Expressive 主题、动效方案、按钮形变、连接式切换按钮和加载指示器。
- [x] 生成原创 PCM 音频，实现 Android MediaPlayer / Windows Java Sound 播放适配、取消准备和释放资源。
- [x] 编写启动说明、原型边界与手动验收步骤。
- [ ] 按 README 进行双端构建与运行验收：遵循用户指令，本次不执行。

检查方式：核对所锁定依赖的发布元数据与 API 源码；检查 Gradle Wrapper 校验和、XML / TOML 语法、音频格式及资源引用；运行 `git diff --check`。静态检查不代替编译和设备验收。
