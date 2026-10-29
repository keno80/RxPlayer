# M1 技术可行性记录

- 更新日期：2026-10-29
- 状态：Windows 引擎与 YouTube 普通视频解析的样本运行已通过；M1 仍有平台、权限及有效期验证未完成。
- 设计规格：[播放页原生规格](../superpowers/specs/2026-10-29-m1-player-native-spec.md)
- 本轮步骤与后续验证：[M1 验证计划](../superpowers/plans/2026-10-29-m1-validation.md)

## 1. 当前结论

Bilibili、网易云及 YouTube 普通视频的匿名音频输入已在当前 Windows 主机上由 mpv 实际解码，暂停 / 继续、拖动、快速替换及进程退出重开通过。YouTube 采用 NewPipeExtractor 解析，分别验证了 AAC 与 Opus；Windows 播放方向选用 mpv JSON IPC。正式分发包与 Android / JDK 17 集成仍需验证，见 [本次运行报告](2026-10-29-streaming-validation.md)。

本轮没有改动 Compose 源码或 Gradle 依赖，没有构建 / 启动 Android 或 Windows 正式应用，也没有宣称三来源已接入产品。

## 2. 实测方法与边界

脚本：[probe_sources.py](../../scripts/m1/probe_sources.py)，仅使用 Python 标准库，无需服务端、API Key、用户 Cookie 或登录。匿名访问产生的会话 Cookie 仅在内存中用于对应来源。

```powershell
python scripts/m1/probe_sources.py --output docs/evidence/m1/2026-10-29-anonymous-sources.json
python scripts/m1/probe_sources.py --source netease --output docs/evidence/m1/2026-10-29-netease-https.json
```

每个请求超时 20 秒；页面 / JSON 单次最多读取 4 MiB；媒体请求带 Range 并最多读取 64 KiB。即使服务器忽略 Range，也不会读取整个文件。脚本不保存媒体、临时播放 URL、请求 Cookie、完整响应或异常消息，仅输出白名单证据字段。JSON 中 `probe.completed` 表示脚本完成对应探测流程，不等于搜索 / 播放验收通过；必须看各步骤结果。

环境为当前 Windows 主机及其当时网络；未在 Android 网络上复测，未建立不同地区或长期稳定性结论。以下网页内部接口没有获得公共开放 API 的稳定性保证。

## 3. 首轮网页探测快照

本节保留首批直接读取网页 / 接口的结果，尤其是 YouTube 当时没有可用 URL 的事实。后续 NewPipe 解析与四条格式的实际播放已取得新证据，当前结论以上述运行报告为准，不能把两批不同层级的验证混为一项。

原始记录：[匿名三来源](../evidence/m1/2026-10-29-anonymous-sources.json)、[网易云 HTTPS 补测](../evidence/m1/2026-10-29-netease-https.json)。记录内 `checked_at` 为 UTC 时间，用户侧工作日期为北京时间 2026-10-29。

| 能力 | Bilibili | 网易云 | YouTube 普通视频 |
|---|---|---|---|
| 匿名关键词搜索 | `Blender`，业务码 0，20 条 | `纯音乐`，业务码 200，3 条 | 页面初始 JSON 中取得 20 个视频 ID |
| 本次样本 | `BV1xx411c7mD` / `cid=62131`，2055 秒，1 个分 P | 搜索结果 `139774` | Blender Big Buck Bunny `aqz-KE-bpKQ` |
| 解析结果 | DASH 音频 ID 30216 / 30232，`audio/mp4`、AAC `mp4a.40.2` | MP3，128000 bit/s，返回 size=3576621、fee=8、无 trial 标记 | `playabilityStatus=OK`；含 AAC / Opus 等音轨，但没有直接 URL，也无 `signatureCipher` |
| 字节证据 | HTTPS，HTTP 206，65536 字节，`ftyp` 标识 | 返回的 HTTP 地址读到 206 / 65536 字节；同主机 HTTPS 也读到 206 / 65536 字节，ID3 标识 | 未取得资源地址，未读媒体字节 |
| 本次发送的媒体 headers | User-Agent、Bilibili Referer、Range | User-Agent、网易云 Referer、Range | 无媒体请求 |
| 有效期观察 | 地址包含 deadline 字段 | 返回 `expi=1200` 秒 | 无可用资源地址 |
| 引擎实际播放 | 未验证 | 未验证；无 trial 标记不等于完整收听已验收 | 未验证 |
| 登录 / 权限边界 | 本样本无需账号；其他内容未验证 | 单个匿名样本成功；VIP / 试听 / 下架未验证 | 本样本页面可匿名读取；账号和内容限制未验证 |

这些请求头是本次成功条件的一部分，尚未通过删减实验判断各头是否必需。有效期字段只是接口观察，尚未等待其过期并验证重新解析。

### Bilibili 路径

本轮按匿名首页会话、`/x/web-interface/search/type` 搜索、`/x/web-interface/view` 元数据、`/x/player/playurl?fnval=16` 音频解析分别取证。搜索与固定 BV 样本是分开的探测，不表示已经交付“点搜索结果即播放”的应用闭环。

网页接口存在 WBI、匿名 Cookie 及风控条件，当前旧搜索路径成功不能作为长期唯一方案。技术参考见 [分类搜索接口研究](https://github.com/Chilfish/bilibili-API/blob/master/docs/search/search_request.md)；该资料由研究者维护，非 Bilibili 官方开放 API 承诺。下一批补当前签名路径、多分 P 与 BV / AV / 链接识别；风控失败独立返回，不能无限重试或伪装成功。

### 网易云路径

本轮使用 `/api/search/get/web` 与 `/api/song/enhance/player/url` 匿名观察。返回值中的 `fee`、试听信息、资源码与实际时长需要结合判断，不能仅据搜索结果宣称完整可播放。更完整技术路径参考实现作者的 [搜索模块](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced/blob/main/module/search.js) 和 [歌曲 URL 模块](https://github.com/NeteaseCloudMusicApiEnhanced/api-enhanced/blob/main/module/song_url.js)。本轮未部署或依赖其公共中转服务，也未引入其代码。

观察到原始地址为 HTTP，因此专门补测同一主机 / 路径的 HTTPS，成功后才把 HTTPS 作为后续候选；不修改 Android 全局明文网络策略。每个新 CDN 和重定向仍需实际验证，不能假定替换 scheme 永远可用。

### YouTube 路径

普通视频搜索和播放分开记录。本次直接读取搜索页 `ytInitialData` 与视频页 `ytInitialPlayerResponse`；只取得元数据 / 音轨描述。页面播放状态 OK 不代表已获得供原生引擎使用的音频输入。

后续已验证 [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) `v0.26.5` 在独立 JVM 上可解析样本的 5 个音轨，并让 mpv 播放其中 AAC / Opus 两种格式。Android 和工程 JVM 17 尚未运行，不把桌面验证扩展为两端通过。[yt-dlp](https://github.com/yt-dlp/yt-dlp) 本轮未运行；正式引入依赖前落实版本和许可证约束。

[YouTube Data API 的 search.list](https://developers.google.com/youtube/data/reference/rest/v3/search/list) 属于搜索元数据接口，不是此处音频资源解析成功的证据。本轮未配置 API Key。YouTube Music 未验证，不能用普通视频搜索结果代替 Music 能力结论，也不能反向代替普通视频需求。

## 4. Windows 与 Android 引擎

| 方案 | 已核对的能力 / 现状 | 当前判断 |
|---|---|---|
| 现有 `DesktopAudioPlayer` | `AudioPlayer.load(ByteArray)`，`AudioSystem.getClip()` 完整载入 WAV | 仅保留演示回归，不作网络流媒体成品方案 |
| mpv 子进程 + JSON IPC | 四条真实格式的 WASAPI 播放 / 暂停 / 拖动、headers / Range、退出及异常终止重开均有证据 | 选择此技术方向；正式适配与固定分发包尚未交付 |
| vlcj / libVLC | 项目提供 Java 封装，支持 Windows，需要配套原生运行库 | 备选；尚未引入，也未验证打包与媒体请求 |
| Android Media3 | 官方提供 ExoPlayer、HTTP 数据源与独立 MediaSessionService 生命周期 | Android 优先技术方向；未添加依赖、未运行服务 |

依据：[mpv 控制与网络文档](https://mpv.io/manual/stable/)、[mpv Windows 分发来源说明](https://mpv.io/installation/)、[vlcj 项目说明](https://github.com/caprica/vlcj)、[Media3 后台服务](https://developer.android.com/media/media3/session/background-playback)、[HTTP 数据源工厂](https://developer.android.com/reference/androidx/media3/datasource/DefaultHttpDataSource.Factory)、[Media3 格式支持](https://developer.android.com/media/media3/exoplayer/supported-formats)。

首轮检查时 PATH / 标准安装路径未发现 mpv / VLC。后续将官方 mpv CI 包放到临时目录独立运行，未系统安装、未加入正式应用。ffmpeg 用于生成原创测试音频；真实播放证据来自 mpv WASAPI，而非 ffprobe。

mpv 试验要求通过本地 IPC 发送命令并接收结构化事件，不解析终端文字，不把凭据放进命令行或日志；限定 IPC 访问和子进程生命周期。引擎试验至少应覆盖：

1. Bilibili AAC / M4A、网易云 MP3、随后 YouTube 实际取得的 AAC / Opus；每项记录版本与资源格式。
2. HTTPS 和该资源要求的 headers；先验证真实流再讨论 URL 有效期刷新。
3. 暂停后位置稳定、继续后推进；拖到指定时间后有对应事件；连续切换只保留最后选择。
4. 正常退出 / 异常中止均释放进程、管道和音频设备，重开仍可播放。
5. 固定可分发二进制的来源、版本、散列和必要依赖；Windows 无额外播放器安装的机器可运行。安装包 / 打包验证需按用户授权执行，不能提前勾选。

Media3 后台播放应将 Player / MediaSession 生命周期放到 Service；Activity 退出前台不应直接销毁播放器。此架构与现有 Composable 持有演示播放器不同，迁移和系统验收放在 M2，不以“已有音频接口”略过。

## 5. 账号与本地凭据

首版保持匿名能力独立可用，不强制 RxPlayer 账号。本轮未请求或读取用户账号凭据。

| 项目 | 方案与状态 |
|---|---|
| Android 安全存储 | Keystore 保护密钥，应用私有存储只保存密文；来源独立命名。方案依据 [Android Keystore](https://developer.android.com/privacy-and-security/keystore)，尚未实现 / 测试 |
| Windows 安全存储 | 优先验证当前用户范围 DPAPI，避免机器范围共享；依据 [CryptProtectData](https://learn.microsoft.com/en-us/windows/win32/api/dpapi/nf-dpapi-cryptprotectdata)，尚未实现 / 测试 |
| 来源登录 | 具体二维码 / OAuth / 会话方式仍需按可用实现逐来源验证；不先画成已支持能力 |
| 过期与退出 | 凭据失效返回来源专属状态；退出清除内存会话和落盘凭据，保留本地歌曲引用；测试中不得泄露原始凭据 |

不能把本轮匿名 Cookie 与将来用户登录凭据混为一类，也不能把上述安全存储方案写成 AUTH-03 已通过。

## 6. M1 仍缺的退出证据

- [x] 三来源各至少一个匿名样本取得实际音频输入，YouTube AAC / Opus 的地址及播放路径成立；不等于所有内容权限均通过。
- [x] Windows 引擎的样本解码播放、HTTPS、headers / Range、暂停 / 拖动 / 退出与异常终止重开通过，选用 mpv JSON IPC 技术方向。
- [ ] 固定可分发引擎包及干净环境运行、工程 JDK 17 / Android 解析与网络适配验证。
- [ ] 音源权限 / 试听 / 过期及多分 P 等边界样本，必要登录方式和凭据生命周期可复查。
- [ ] 新增准备 / 错误视觉状态及未确认页面按原型流程完成审核。

已有运行证据支持接入方向，但 M1 尚未关闭，不能跳到 M2 的产品验收。
