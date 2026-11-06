# Bilibili 歌曲信息、歌词与封面可行性

日期：2026-11-06。范围：匿名只读请求、现有代码核对及完整原型提案。**尚未实现正式自动匹配，未构建或安装双端。**

## 结论

已有真实请求证明：部分音乐视频的标题 / 简介包含歌名与演唱者，可据此从网络查到歌曲详情、专辑封面和 LRC。三条视频样本不足以承诺识别成功率；本轮没有试听比对歌词同步，也没有运行自动提取算法。推荐规则提取与版本校验，明确结果自动补充，不确定时保留视频信息并提供人工选择。

## 本次真实请求

使用 Python 标准库 urllib，匿名 GET，不使用用户账号 / Cookie 或公共代理服务。Bilibili 详情沿用工程 User-Agent / Referer 与匿名首页会话。歌词请求只记录是否含时间戳及数量，不保存或在原型展示真实歌词正文。

| 视频 | 实际读取的信息 | 可提取线索 |
| --- | --- | --- |
| [BV1Ekub6zE3D](https://www.bilibili.com/video/BV1Ekub6zE3D/) | 标题「李佳薇 《甲乙丙丁》百万豪装录音棚大声听」；UP 主 JLRS-LeoFM；单 P 211 秒；简介说明歌曲由李佳薇演唱 | 标题和简介一致；UP 主不能当歌手 |
| [BV1D8bV6cEpz](https://www.bilibili.com/video/BV1D8bV6cEpz/) | 标题含李佳薇、甲乙丙丁 Strangers、粤语版；UP 主驰放音乐分享；单 P 210 秒 | 语言 / 版本标记必须保留 |
| [BV1pWaY6mEct](https://www.bilibili.com/video/BV1pWaY6mEct/) | 标题含甲乙丙丁；UP 主黎歌Neeko；单 P 211 秒；简介明确标注原唱李佳薇、翻唱黎歌Neeko | 区分作品原唱与本次演唱者；不得自动冒充原版录音 |

这三条 `/x/web-interface/view?bvid=...` 均返回 `code=0`；返回顶层未见名称包含 music 的字段。只说明本次详情响应没有独立歌曲字段，不代表 Bilibili 所有视频或其他接口均无音乐元数据。两条匿名关键词搜索尝试均返回 HTTP 412，本轮不处理或绕过该限制。

网易云请求结果：

- `/api/search/get?s=李佳薇 甲乙丙丁&type=1&limit=5&offset=0` 返回 `code=200`。原版候选 ID `3399839173`，歌名「甲乙丙丁 (你我怎么两清)」，歌手李佳薇，时长 210461 ms；另有合唱、粤语、伴奏候选。
- `/api/song/detail/?id=3399839173&ids=[3399839173]` 返回专辑「甲乙丙丁」与 HTTPS `picUrl`。请求其 `?param=500y500` 返回 HTTP 200、`image/jpg`、24057 字节；未做像素尺寸或最终原生显示校验。
- `/api/song/lyric?id=3399839173&lv=-1&kv=-1&tv=-1` 返回 `code=200`，LRC 正文 1521 字符，70 行以 `[` 开头，无翻译正文。该计数包含可能的元信息行，**不能称作 70 行已解析歌词**。
- 查询响应不总含专辑封面，必须读取歌曲详情；以上为未公开承诺稳定性的接口，不把一次匿名可用等同于正式开放 API。

LRCLIB 请求结果：

- `/api/search?track_name=甲乙丙丁&artist_name=李佳薇`：HTTP 200，19 条；前列记录包括 ID `37647540`、`39683842`、`38658534`，均含同步 / 普通歌词，210 秒。存在多版本 / 重复记录，不能取第一条即宣称准确。
- `/api/search?track_name=Linger&artist_name=The Cranberries`：HTTP 200，20 条；前列有 275、310、271 秒记录，包含同步歌词。
- 请求标识 `RxPlayer v0.1 (https://github.com/keno80/RxPlayer)`，顺序调用，中间间隔约 300 ms。官方要求标识客户端、控制请求并遵守 `Retry-After`；正式实现应据此处理限流。[LRCLIB 文档](https://lrclib.net/docs.txt)

## 代码现状与接入点

- `BilibiliSource.details` 当前保存标题、UP 主、视频封面、pages 的具体 cid / 标题 / 时长，没有保存简介。需要有界提取简介中的歌曲线索。
- `BilibiliTrack.artist` 现在直接使用 UP 主；`artworkUrl` 为视频封面，尚无联网歌词。应保留原内容字段，另存歌曲匹配结果。
- `PlaybackTrack` 已具备 `lyrics` / `artworkUrl`；双端歌词布局、按 `atMs` 高亮和点击拖动已存在，可复用。普通无时间戳歌词仍需单独支持，不能编造时间戳。
- 当前详情 / 标题 / 页面信息足以支撑首批试验，不必先接大模型、ASR 或音频指纹。

## 来源选择

推荐先验证网易云歌曲详情 / 封面 / 歌词，再以 LRCLIB 补歌词；两个来源的歌曲身份与版本仍需分别核对。参考第三方维护者自己的 [搜索实现](https://github.com/neteasecloudmusicapienhanced/api-enhanced/blob/main/module/search.js)、[歌词实现](https://github.com/neteasecloudmusicapienhanced/api-enhanced/blob/main/module/lyric.js) 及 [歌曲详情实现](https://github.com/neteasecloudmusicapienhanced/api-enhanced/blob/main/module/song_detail.js)，无需为此引入完整 Node 服务。正式实现前继续以实际响应验证契约。

iTunes 可提供歌曲信息与图片字段，但其现行说明将图片等素材用途限定于商店推广，因此不选作本功能的默认封面来源。[Apple Search API](https://performance-partners.apple.com/search-api)

## 验证边界

已证实三个视频详情可读、一个中文歌曲可查详情 / LRC / 图片、LRCLIB 中英文查询可用。未证实自动提取 / 排名准确率、歌词时间轴与视频音频相符、Android 网络可达性、持久化、离线恢复或原生 UI。原型仅使用原创演示候选 / 歌词 / 封面；本轮不升级依赖、不构建正式包。
