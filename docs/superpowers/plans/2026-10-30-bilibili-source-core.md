# Bilibili 真实来源核心

目标：将已验证的匿名 Bilibili 路径写入正式共享源码，用独立内容模型替代将真实数据塞进 DemoTrack 的做法。本批不改变未确认页面；现有 Android / Windows 界面和本地播放保持可用。

依据：用户授权推进真实在线播放；M1 的 Bilibili 分页、AV / BV、多分 P、自然到期重解析及两端 AAC 引擎已有证据。M1 其他来源和发布门禁仍未整体通过。

- [x] 添加共享 ContentItem / VideoPart / AudioStream 模型，内容身份不保存临时播放 URL。
- [x] 实现 HTTPS / BV / AV 识别、匿名会话、搜索分页、元数据分 P 和 DASH 音频解析；区分不可用、登录要求、风控、网络 / 格式失败。
- [x] 使用与 Kotlin 2.2.20 匹配的固定 Ktor 3.3.1 和 kotlinx.serialization JSON，Android / Windows 使用 OkHttp 引擎；无日志拦截器、不内置用户凭据。
- [x] 先写边界 / HTTP 模拟测试，再实现；10 项离线与 1 项 opt-in 桌面真请求通过，不打印签名 URL。
- [x] 构建双端并启动，Android 正式来源 instrumentation 单项真请求通过；不扩大完整播放和系统流程回归。
- [x] 更新进度和真实能力边界，按仓库规则提交推送 / 合并。

后续完整闭环还需要流媒体播放器接线、真实结果封面和双端入口审核、持久化及系统服务。本批构建或来源测试通过不等于这些能力已经交付。
