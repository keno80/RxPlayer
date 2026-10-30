# Android 后台媒体实现记录

日期：2026-10-30。用户在持久化交付后授权下一步后台播放与通知 / 锁屏控制；本轮不新增应用内布局或自定义通知样式。

## 实现

- `PlaybackRuntime` 集中拥有播放状态、轮询、自然结束后的队列推进、过期链接有界恢复、历史、收藏 / 外观和 SQLite 保存。后台工作不依赖页面、Recomposer 或 UI 的前台标记。
- Android `RxPlaybackService` 使用 Media3 1.8.0 `MediaSessionService`，持有运行时、ExoPlayer 与会话。页面绑定同一运行时，解绑不关闭播放器；服务最终销毁时暂停、取消任务、保存并释放资源。仅用户打开页面启动服务，不增加开机或自动发声恢复。
- `AndroidSessionPlayer` 使用 `SimpleBasePlayer`，发布稳定曲目 / CID 身份、队列、标题、歌手、封面和时长。MediaItem 无播放 URI；外部不能设置任意播放项或 URL，只允许已有命令。会话仅接受本应用或系统可信媒体控制器。
- 标准媒体通知、锁屏及媒体按键由 Media3 提供；播放 / 暂停 / 停止 / 拖动 / 切歌 / 模式 / 音量映射至共享状态。系统 previous 的当前曲归零、无效索引 no-op 以及暂停切歌不发声均保留。
- ExoPlayer 自动处理音频焦点与 `audio becoming noisy`，同步实际 `playWhenReady`，防止系统暂停后共享状态仍误以为需要继续；服务另接耳机断开广播，覆盖尚在解析地址、引擎未准备时的取消。启用 `WAKE_MODE_LOCAL`，声明媒体播放前台服务及唤醒锁权限。
- Windows 仍由窗口持有同一共享运行时，关闭窗口先暂停保存；未实现 Windows 系统媒体接口或托盘。

## 验证与边界

- 新增运行时与系统暂停 / 切歌契约测试，先红后绿。覆盖无页面推进 / 记录历史、UI 刷盘不停播、最终关闭释放、系统暂停清除意图、暂停切 P 保持暂停、当前曲归零和无效索引不绕回。
- 最终定向测试共 33 项：31 通过、0 失败、2 项可选探针未启用（在线 Bilibili 与真实 mpv，本轮不重复此前引擎全链路）。持久化与既有播放状态测试通过。
- Android 正式 Debug 包编译、覆盖安装并启动；Windows 编译并运行。Android API 37 只读核对正式进程、服务记录、UI 绑定及当前 PID 的 AndroidRuntime 日志，确认服务创建，未见本次启动崩溃。
- 本轮最初尝试隔离测试包被手机拦截，用户随后明确要求直接安装正式包。已停止该流程、移除本批新增的独立媒体会话测试包用例与对应依赖，并把默认只安装正式包写入 AGENTS.md。之后仅安装正式应用。
- 定向审查提出系统 seek 不能无条件转为 step，现已修复并复查，无剩余 Critical / Important。Git 空白检查通过。
- 未操作页面逐项验收、未截取界面回归；后台真实持续播放、锁屏 / 蓝牙按键、耳机和来电 / 其他应用焦点变化、厂商后台限制由用户真机审核。服务创建不等于这些流程已通过。

## 后续

本轮仅交付该 Android 实现范围。Android Auto / 外部曲库浏览、进程死亡后系统主动播放恢复、Windows 系统媒体、歌单、最低系统 / R8 和完整 M2 验收仍单独跟踪。

官方依据：[MediaSessionService 后台播放](https://developer.android.com/media/media3/session/background-playback)、[1.8.0 SimpleBasePlayer 契约](https://github.com/androidx/media/blob/1.8.0/libraries/common/src/main/java/androidx/media3/common/SimpleBasePlayer.java)。
