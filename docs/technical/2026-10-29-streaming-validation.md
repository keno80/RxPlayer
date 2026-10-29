# Windows 流媒体与 YouTube 解析验证

- 日期：2026-10-29，北京时间。
- 阶段：M1，独立技术验证；不代表正式应用接入完成。
- 结论：当前机器上，mpv + 本地 JSON IPC 可以播放三来源样本，NewPipeExtractor 可以解析普通 YouTube 视频的音频。建议采用这两条技术路径继续验证 / 接入。

## 1. 环境与工具

| 项目 | 实际条件 |
|---|---|
| 系统 | 当前 Windows x64 主机 |
| 引擎 | mpv `v0.41.0-dev-gdc4b285c9`，官方 GitHub CI 的 x86_64 MinGW full 包 |
| 音频输出 | Windows WASAPI，静音；没有使用 null 输出模拟播放 |
| 解析器 | NewPipeExtractor `v0.26.5`，JitPack 发布 JAR |
| Java | 独立源码启动器使用本机 JDK `23.0.1`；正式工程仍锁定 JVM 17，未运行 Gradle |
| 会话 | 全部匿名；未读取用户账号、浏览器 Cookie 或 API Key |
| 网络 | 对验证子进程沿用 Windows 已有 HTTP / HTTPS 代理配置，未修改系统设置 |

[工具来源、依赖版本及散列](../evidence/m1/2026-10-29-runtime-provenance.json) 记录了实际使用内容。mpv ZIP 的 SHA-256 与官方发布页值比对一致；JAR 的散列为下载内容记录，未校验发布者签名。所有二进制都放在仓库外的临时目录，没有加入应用依赖或 Git。

官方 Windows 安装页提供 [第一方 CI 包入口](https://mpv.io/installation/)，[JSON IPC / 网络配置文档](https://mpv.io/manual/stable/) 说明了控制方式。[NewPipe 固定版本说明](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/README.md) 提供依赖入口及 Android 低版本所需 desugaring 要求。本轮使用的 HTTP Downloader 是独立 JVM 探针，不是未来 Android 的网络适配器。

## 2. 真实资源播放结果

主要证据：[完整运行报告](../evidence/m1/2026-10-29-streaming-system-proxy.json)。各样本均使用临时 HTTPS 音频 URL，边请求边由引擎解码；不先落盘下载音频文件。

| 来源 / 格式 | 样本 | 引擎识别时长 | 解码输出 | 暂停漂移 | 拖动 / 继续 |
|---|---|---|---|---|---|
| Bilibili AAC / MP4 | `BV1xx411c7mD`，cid=62131 | 2055.64 秒 | 44100 Hz，立体声 | 0 秒 | 30.00 → 30.73 秒 |
| 网易云 MP3 | `139774` | 223.47 秒 | 48000 Hz，立体声 | 约 0.025 秒 | 30.00 → 30.61 秒 |
| YouTube AAC / MP4 | `aqz-KE-bpKQ` | 634.69 秒 | 44100 Hz，立体声 | 0 秒 | 30.00 → 30.74 秒 |
| YouTube Opus / WebM | 同一普通视频 | 634.60 秒 | 48000 Hz，立体声 | 0 秒 | 30.00 → 30.74 秒 |

通过条件：WASAPI 和音频参数已初始化、进度推进；暂停后约 1 秒内位置漂移不超过 0.1 秒；拖到 30 秒后位置误差小于 0.75 秒；继续后时间超过 30.6 秒；stop 后进入 idle。以上是本次验证阈值，不是产品性能承诺。

没有做完整时长收听、人耳音质检查、蓝牙 / 多输出设备切换、锁屏 / 系统媒体控制或长时间稳定性验收。

## 3. Headers、Range 与生命周期

[受控 HTTP 服务报告](../evidence/m1/2026-10-29-mpv-headers.json)：

- 使用本地生成的 45 秒 MP3，仅监听 `127.0.0.1` 随机端口。
- 缺少指定 User-Agent / Referer 的负对照返回 403。
- mpv 通过 JSON IPC 设置相同 headers，服务观察到两个头均匹配，以及 Range 请求，返回 206；随后实际解码、暂停、拖动和继续通过。
- 此项证明 headers 与 Range 的传递；本地服务使用 HTTP，真实来源的 HTTPS 支持由上表的独立运行证明，不把二者混称为本地 TLS 测试。

快速替换 Bilibili → 网易云 → Bilibili 后，仅保留 1 个播放项，实际输入身份等于最后一次选择；没有将临时 URL 写入报告。正常 quit 的进程退出码为 0，命名管道消失，重开进程后 WASAPI 重新初始化。

[异常终止报告](../evidence/m1/2026-10-29-mpv-termination.json)：引擎已初始化 WASAPI 后，由探针强制终止，退出码 1；旧管道消失。随后新进程可以再次初始化 WASAPI 并推进播放，正常退出码 0。这里 `forced` 字段只表示 `close()` 是否执行兜底 kill；异常测试中的终止由测试步骤主动执行。

## 4. YouTube 解析与网络问题

NewPipe 对普通视频 `aqz-KE-bpKQ` 解析得到 5 个 URL 音轨：AAC 48 / 128 kbit/s、Opus 50 / 70 / 160 kbit/s。选一个 AAC 和一个 Opus 分别运行引擎控制测试，并非只验证元数据。它不是 YouTube Music 曲库测试。

[资源读取与网络诊断](../evidence/m1/2026-10-29-youtube-network.json) 记录两种格式均经 HTTPS Range 返回 206 / 65536 字节，识别出 MP4 / WebM 容器。报告只保存格式、主机、到期字段和是否含签名等白名单信息，没有保存临时地址、签名值、来源 IP 或代理地址。

首次 mpv 运行未显式桥接 Windows 的代理，Bilibili / 网易云通过，YouTube 两种音轨加载超时，见 [首次运行记录](../evidence/m1/2026-10-29-streaming-runtime.json)。同一环境中 Python 资源请求可读取媒体；将已有代理配置仅传给 mpv 子进程后，两种格式均通过。结果说明此环境需要处理解析层和播放层的网络配置一致性，不能把首次超时归因于 AAC / Opus 解码器，也不能据此宣称所有用户都需要代理。

另一个问题发生在验证脚本本身：同步 Windows 管道句柄被后台线程阻塞读取后，主线程写入同一句柄也阻塞。探针现改为同线程写入、使用 `PeekNamedPipe` 检查可读字节并有界读取；修正后取得上面的真实控制证据。未把脚本阻塞计为引擎功能失败。

## 5. 重复验证方式

前提：Python 3、JDK（本轮为 23.0.1）、ffmpeg（用于原创本地测试音频），以及以下工具包。以下下载依据固定清单；若官方可变 CI release 删除了该文件，应报告缺失，不能静默换新版本或散列。

```powershell
$m1Root = Join-Path $env:TEMP 'rxplayer-m1-runtime'
$m1Manifest = Get-Content 'docs/evidence/m1/2026-10-29-runtime-provenance.json' -Raw | ConvertFrom-Json
New-Item -ItemType Directory -Force -Path "$m1Root/newpipe" | Out-Null
$m1Zip = Join-Path $m1Root 'mpv-verified.zip'
Invoke-WebRequest $m1Manifest.mpv_archive.url -OutFile $m1Zip
if ((Get-FileHash $m1Zip -Algorithm SHA256).Hash -ne $m1Manifest.mpv_archive.sha256) { throw 'mpv hash mismatch' }
Expand-Archive $m1Zip -DestinationPath "$m1Root/mpv" -Force
foreach ($m1Artifact in $m1Manifest.newpipe_runtime) {
    $m1Jar = Join-Path "$m1Root/newpipe" $m1Artifact.name
    Invoke-WebRequest $m1Artifact.url -OutFile $m1Jar
    if ((Get-FileHash $m1Jar -Algorithm SHA256).Hash -ne $m1Artifact.sha256) { throw 'JAR hash mismatch' }
}
python scripts/m1/validate_streaming.py --mpv "$m1Root/mpv/mpv.exe" --newpipe-dir "$m1Root/newpipe" --output "$m1Root/recheck.json"
python scripts/m1/probe_mpv_headers.py --mpv "$m1Root/mpv/mpv.exe" --output "$m1Root/header-recheck.json"
```

从仓库根目录执行，检查 JSON 各步骤，脚本退出码 0 只表示报告生成，不能代替其中 `passed`、shutdown 和错误字段。Java 子探针 stdout 含临时 URL，**仅由 Python 捕获在内存中使用，不直接运行或转存到日志**。验证不运行 Gradle、不编译 / 安装正式应用。

## 6. 选型与后续门槛

1. **Windows 播放方向采用 mpv + JSON IPC**：已有四条真实格式及请求头、Range、控制、异常恢复证据。正式适配时限定进程 / 管道访问，继承网络配置、处理引擎事件与资源生命周期；UI 不直接控制多个播放器实例。
2. **YouTube 解析方向采用 NewPipeExtractor**：匿名普通视频样本已成立，优先尝试两端共用业务结果；Android 的 Downloader / desugaring、JDK 17 运行及 R8 保留规则仍需相应平台验证，不能把 JVM 23 成功扩展成 Android 成功。
3. 当前 mpv 是可变 CI 验证包，尚未确定正式分发包；需要选择可固定保存的版本、附带对应源码 / 许可证资料，并验证干净环境运行。NewPipe 为 GPL-3.0-or-later，正式引入时落实项目的依赖与分发约束；本轮未作产品许可或发布操作。
4. 补账号、地区 / 网络、内容不可用、试听、链接过期及重新解析样本；YouTube Music 单独记录。这些仍是 M1 未完成项。

本次完成 Windows 引擎和 YouTube 普通视频解析的样本技术验证，M1 整体保持进行中。没有修改产品 UI，也没有把演示原型或这组脚本当作已交付的真实产品闭环。
