# M1 首批规格与技术验证计划

**目标：** 启动暂定原型，并以可复查的规格与真实请求进入 M1；本轮完成不代表整个 M1 退出。

**执行：** 当前会话顺序推进，使用 `codex/m1-feasibility` 阶段分支；不自动创建新会话或调度代理。使用 writing-plans 整理执行步骤，并按仓库规则维护证据与交付。

**技术栈：** 既有 Kotlin / Compose Multiplatform；Python 标准库用于匿名网络探测。不开启原生构建，不修改未确认的正式 UI。

## 1. 文件与职责

| 文件 | 职责 |
|---|---|
| `docs/superpowers/specs/2026-10-29-m1-player-native-spec.md` | 暂定播放页的组件、状态、窗口适配和验收映射 |
| `docs/technical/m1-feasibility.md` | 音源实测、引擎候选、凭据边界与尚缺证据 |
| `scripts/m1/probe_sources.py` | 无账号、限量读取、隐去临时地址的可复现探测 |
| `docs/evidence/m1/` | 带时间的探测 JSON、确切 Material3 API 检查及原型截图 |
| `docs/progress.md`、`docs/development-plan.md` | 实时状态与阶段复选框 |

## 2. 本轮步骤

- [x] 在 `127.0.0.1:8766` 启动静态服务，打开 `prototype-v3/?view=player&revision=queue-fade`，核对双端画面并记录截图。
- [x] 固定已确认范围，将 Android 两套播放器、Windows 双栏及单一队列映射到现有 Compose 文件和状态契约。
- [x] 读取工程锁定版本，从 Maven Central 的 `material3-1.9.0-alpha04-sources.jar` 核对实际声明并记录散列；不执行 Gradle。
- [x] 运行 `python scripts/m1/probe_sources.py --output docs/evidence/m1/2026-10-29-anonymous-sources.json`；分别记录搜索、解析、格式及最多 64 KiB 的媒体读取，失败也保留。
- [x] 对网易云返回的 HTTP 地址补同主机 HTTPS 探测，记录在独立 JSON；不修改 Android 全局明文策略。
- [x] 输出技术矩阵，区分“文档支持”“匿名样本成功”“引擎实际播放”和“正式应用通过”。
- [x] 检查 Python 语法、JSON、证据脱敏、本地文档链接与 `git diff --check`：3 份 JSON、36 个本地链接通过，非 HTTP URL 拒绝与 HTTP 异常脱敏检查通过。

本轮首批产物按仓库规则提交、推送并合并；实际交付状态以 Git 远端核对和本次会话结果为准。M1 完整退出项仍保持未完成。

## 3. 下一批 M1 验证

1. **Windows 引擎**：mpv 本地 JSON IPC 已通过 Bilibili AAC、网易云 MP3 与 YouTube AAC / Opus 的真实控制、headers / Range、退出与异常终止重开，选择此方向；接着固定正式分发包并验证干净环境，不把 CI 临时包直接视作产品运行库。
2. **YouTube 普通视频**：NewPipeExtractor `v0.26.5` 已在 JDK 17 和 Android API 37 解析样本的 5 个音轨，AAC / Opus 由 mpv / Media3 实际播放通过；Android 最低版本和 R8 边界仍需验证。YouTube Music 另列能力，不能代替普通视频。
3. **来源边界**：补 Bilibili 多分 P / AV / 链接与签名路径；网易云完整 / 试听 / 无权限样本；来源地址过期重新解析；登录前后的独立能力和凭据生命周期。不因一个匿名样本成功就勾选整个平台通过。
4. **UI 审核余项**：准备 / 缓冲 / 错误等新增视觉状态、未审核页面先在原型补齐并由用户确认。已暂定确认的播放器等价实现不用重复请求相同确认。

已取得的样本证据按上述状态记录，平台、来源边界和 UI 审核余项仍属于 M1。M1 的退出仍以总开发计划为准，不提前进入 M2 的真实产品闭环。

## 4. 第二批运行记录（2026-10-29）

- [x] 固定并记录 mpv CI 包与 NewPipe JAR 来源、散列、版本和验证范围。
- [x] 独立 JVM 解析普通 YouTube 视频，并由 mpv 分别验证 AAC / Opus；Bilibili / 网易云一并验证真实流。
- [x] 定位初次验证脚本的 Windows 同步管道死锁，改为有界 Peek / 读取；保留网络配置差异导致的首次播放失败记录。
- [x] 受控 headers / Range 的负对照与成功解码，正常退出与异常中止后重开。
- [x] 本轮 3 个 Python 脚本语法、9 份 JSON、40 个本地链接、运行结果断言、URL 脱敏与 diff 检查通过；无遗留 mpv 进程。工程与 Android 构建不在本轮范围，交付按仓库流程核对远端。

详细证据与重复步骤见 [运行报告](../../technical/2026-10-29-streaming-validation.md)。第 3 节中的平台、来源边界和 UI 审核余项仍未完成，不以本批样本成功关闭 M1。

## 5. 平台与来源边界补测（2026-10-29）

- [x] Temurin JDK 17.0.17 实际运行四条音频格式的解析 / 播放控制。
- [x] 用户明确授权临时 Android APK 构建 / 安装；API 37 真机检查 Media3 / NewPipe，分别保留直连与测试网络桥接结果，结束后清理测试包和转发。
- [x] Android Keystore 与 Windows DPAPI 的模拟数据基础验证。
- [x] 匿名可取得 / 不可取得资源、未登录和内容不可用样本；具体原因未由平台明确给出时，不伪造会员或地区结论。
- [x] 网易云原始 URL 实际等待标称 1200 秒 TTL +30 秒，旧地址仍 206；刷新地址亦 206，记录此 TTL 非本样本硬过期证据。
- [x] 受控硬过期 410 → 重新签发 → 206 → 实际恢复播放，明确不冒充真实 CDN 过期。
- [ ] Bilibili / YouTube 签名地址自然到期观察，已启动，约北京时间 16:48 / 20:50 检查。
- [ ] 真实会员 / 试听 / 登录及退出凭据生命周期，最低 Android / R8 与分发余项。
- [x] 本批 Python / PowerShell 语法、运行证据、JSON、文档链接和 URL 脱敏检查；当前以阶段分支保存，长时效结果未齐不提前标记任务达标或合并。

详细结果见 [平台与访问验证](../../technical/2026-10-29-platform-access-validation.md)。
