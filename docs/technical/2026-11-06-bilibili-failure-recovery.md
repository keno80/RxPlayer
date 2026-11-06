# Bilibili 异常链路收口记录

日期：2026-11-06；分支 `codex/bilibili-failure-recovery`；基点 `a075581`。

## 修复与边界

1. 分页失败原先清空 hasMore，后续无法继续。现保留成功页、结果与 hasMore，使用独立 pageError 阻止自动加载；retryMore 显式重试捕获的 submitted 关键词及 nextPage。searching 在启动协程前置位，防止重复调用占有同一页。
2. 来源返回正常空数组保持空结果；第 1000 页不再提供非法第 1001 页。详情校验 BV、正 page / cid、非负时长、非空且唯一的分 P 身份；非法响应不冒充用户输入错误或可播放内容。
3. 播放 SourceFailure 使用已有 friendlySourceError 文案。网络、风控 / 访问限制、限流和无效响应可重试；当前下架、需登录及无支持音轨进入 Unavailable，重复点击保留具体原因，不泛化成网络失败。
4. 地址刷新遇到可重试 SourceFailure 保持 Expired 上下文、位置和播放意图；若新响应明确是下架 / 需登录 / 无音轨，则进入 Unavailable。手动和自动 select(refreshing=true) 均消耗本轮自动补偿预算，避免保留 Expired 后引入额外自动循环。用户仍可明确重试。
5. 取消或新选择保留现有 generation / ensureActive 隔离；不重写原队列，旧搜索或旧音频解析晚返回均不能覆盖新上下文。

## 界面确认

先在唯一完整原型补底部三态入口，用户明确“确认，接入双端”后接入共用 SourceWorkspace。分页失败只有已有 toast 和“重试加载”文字按钮，成功追加结果；没有新增播放错误卡片或另一套原型。确认范围见 [规格](../superpowers/specs/2026-11-06-bilibili-pagination-retry.md)。

## 实际检查

- 新分页 API 测试先因缺少 pageError / retryMore 而失败；补分页实现后，4 个新增播放错误 / 刷新用例在旧播放实现上失败，再修复转绿。
- Java 17 desktopTest：BilibiliBrowseStateTest 5、BilibiliSourceTest 12、OnlinePlaybackStateTest 16、PlaybackStateTest 13、PlaybackRuntimeTest 8，共 54 项通过，无失败 / 跳过。
- 覆盖同页重试与去重、草稿不参与分页、并发分页去重、新搜索 / 非协作晚回隔离、真实空结果、HTTP 登录 / 下架 / 受限 / 网络详情错误无播放回调、源响应边界、具体提示、刷新失败位置 / 暂停意图、刷新预算及取消后选择新曲。
- 模拟引擎和 MockEngine 只验证可控错误契约；未把这些测试写为已对线上平台触发下架 / 风控、真实断网或全设备验收。
- 原型定向结果见规格；JS 语法、Git 空白检查及静态审查通过，无剩余 Critical / Important。
- 按用户最新约定不额外构建安装包、不安装 Android 正式 / 测试包；共用源码经桌面测试编译，Android 原生安装 / 网络故障流程仍由用户审核。
- Windows 使用现有编译产物启动（编译任务 UP-TO-DATE），日志确认托盘与 Windows 媒体会话连接；未继续逐页点击或完整播放回归。

本批不接账号、WBI 或短链，不表示 M1 发布门禁及 M2 整体集中验收已完成。
