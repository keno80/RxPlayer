# Android 后台播放与系统媒体控制计划

**授权：** 用户在本地持久化交付后，对“后台播放与通知栏、锁屏控制”回复“开始吧”。本轮不设计新的应用页面；采用 Media3 系统媒体通知和媒体会话，不增加自定义通知布局。

**设计：** 把当前 UI 内的播放轮询、队列、历史和持久化提升为共享 `PlaybackRuntime`。Android 由单个 `MediaSessionService` 持有它，Activity 绑定服务并观察同一对象；Windows 仍由应用窗口持有运行时。比 Activity 生命周期播放器加通知补丁更能保证后台队列推进；避免另建一套系统队列导致状态分叉。

服务使用 Media3 1.8.0 `SimpleBasePlayer` 适配既有播放状态，系统只获得当前曲目信息、队列身份及已有播放命令，不接收临时解析 URL。通知 / 锁屏的播放、暂停、切歌、拖动、模式变化分派给共享状态。ExoPlayer 保持自动音频焦点和耳机断开处理；共享状态同步实际播放意图，避免系统暂停后被误判为仍想播放。使用媒体播放前台服务及 CPU 唤醒锁权限；不加入开机自启动或后台自动恢复发声。

官方依据：[后台播放](https://developer.android.com/media/media3/session/background-playback)、[锁定版本 SimpleBasePlayer](https://github.com/androidx/media/blob/1.8.0/libraries/common/src/main/java/androidx/media3/common/SimpleBasePlayer.java)。MediaSession 默认通知负责系统样式；应用内原型保持原有布局。

## 执行步骤

- [x] 提取 `audio/PlaybackRuntime.kt`，集中队列推进、历史与数据库保存；单元测试先验证无页面仍推进、UI 刷盘不停止播放、最终关闭会释放资源。
- [x] 平台 `rememberPlaybackRuntime`：Android 绑定服务、Windows 在窗口内初始化 / 关闭；`App.kt` 只持有浏览及页面状态，不再因退后台暂停或销毁引擎。
- [x] `AndroidSessionPlayer.kt` 把共享状态映射至标准 Player；系统命令与应用命令使用同一份队列与取址路径。限制可用命令及非可信控制器访问。
- [x] `RxPlaybackService.kt` 持有运行时、会话与系统通知，服务销毁时保存后释放；声明前台媒体服务、权限和会话入口，维持现有 ExoPlayer 音频焦点 / 耳机处理并开启播放唤醒锁。
- [x] 定向公共测试和双端编译；仅安装正式应用并启动。用户明确要求直接安装正式包，本轮停止独立测试 APK 流程并写入 AGENTS.md；系统媒体完整交互由用户审核，不把静态检查或启动写成通过。按用户约定不逐页点击或截图回归。
- [x] 定向审查、更新进度、提交推送并合并。Windows 系统媒体会话、Android Auto / 外部曲库浏览、最低系统 / R8 等继续独立跟踪。
