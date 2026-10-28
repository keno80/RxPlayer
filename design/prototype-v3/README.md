# RxPlayer 原型 03

状态：待用户审核。根据“学习风格与组件运用，避免照搬参考应用”的反馈，重新组织配色、组件和布局；01 / 02 保留对照，不代表已获批准。

## 预览

仓库根目录启动本地服务：

```powershell
python -m http.server 8766 --bind 127.0.0.1 --directory design
```

- 完整原型：`http://127.0.0.1:8766/prototype-v3/`
- 从播放页开始：`http://127.0.0.1:8766/prototype-v3/?view=player`
- 工具栏支持 Android / Windows、深浅主题和搜索场景切换。返回播放页之前的页面可用左上角返回按钮或 Esc。

全部曲目、账号、播放、搜索和存储均为模拟。本版没有连接音源或播放音频，不修改原生工程。原型用于审核设计，未进行正式用户可用性研究。

## 依据与设计判断

- [Google Expressive UX 研究](https://design.google/library/expressive-material-design-google-research)：通过层级和分组引导注意，保持熟悉的任务结构；视觉表现须服务可用性。
- [Material 3 颜色系统](https://m3.material.io/styles/color/system/overview)：使用成对颜色角色建立深浅主题与组件层级。已在浏览器读取页面正文，并对照技能的 color / color-schemes 资料。
- `m3-expressive` 技能：tokens、component-tokens、color、color-schemes、buttons、shape、layout、interaction 的组件与数值指导。
- 从用户提供的案例学习近色表面、强调色分工、主次操作的组件选择；参考应用的文案、形状、构图和具体色值不作为模板。

| 任务 | 原型 03 的选择 | 选择理由 |
|---|---|---|
| 播放 / 暂停 / 切歌 | 熟悉的 Material Symbols 图标、醒目的填充主播放、较小的 tonal 切歌 | 图标语义成熟，主操作通过大小与颜色突出；提供无障碍名称和桌面提示 |
| 搜索与音源选择 | contained search、带名称的单选按钮组、最佳匹配与其余垂直列表 | 保留来源与版本信息，使查找路径稳定；最佳匹配不在下方重复 |
| 音乐库播放 | 收藏摘要使用图标播放，歌单详情使用“播放全部”文字及次要随机图标 | 根据上下文明确性决定是否保留文字，不统一强制文字或图标 |
| 当前播放 | 独立播放视图；Windows 封面 / 控制双列，Android 单列 | 提供专注收听空间，避免把大控制面板压在仍可见的列表上 |
| 队列、创建与错误恢复 | 必要文字说明，图标辅助；队列按熟悉的列表组织 | 降低陌生操作和异常状态的理解成本 |
| 表现力 | 蓝绿主色、暖杏小面积点缀、分层表面、强调标题和状态变化 | 把注意力放在内容和主操作，保留 RxPlayer 自身的视觉节奏 |

图标与文字的取舍是本版针对任务的产品决定，研究文章没有规定音乐播放器必须使用哪一种。

## 颜色方案与组件数值

使用官方 Material Color Utilities `0.3.0` 的 `SchemeTonalSpot`、标准对比度 `0` 生成静态深浅主题。主色种子 `#006B63`；第三色使用 `TonalPalette.fromInt(#C57D30)` 替换 scheme 的第三色调色板，再通过 scheme 的角色 getter 输出。具体种子、方法与角色值保存在 [palette.json](palette.json)，[colors.css](colors.css) 为生成结果。没有从参考截图取色。

这是 RxPlayer 的静态设计方案，不宣称已经实现 Android 壁纸取色、专辑取色或实时动态颜色。生成工具仅用于制作原型；运行时没有 MCU 依赖或外部字体请求。

- 主操作使用 primary / on primary，来源选择与模式选中使用 secondary container / on secondary container，小面积状态及收藏图形使用 tertiary container / on tertiary container。
- 页面主体使用 surface，导航使用 surface container；同一区域的角色映射在两个平台保持一致。on surface / on surface variant 用于各层表面。
- 主播放采用 Large icon button 的 96 dp 容器、32 dp 图标和 28 dp 方形圆角比例；切歌采用 Medium 的 56 dp 容器和 24 dp 图标。具体核对技能的 Compose component token 参考。
- 标题使用 45/52、36/44、32/40、28/36 和 22/28 等排版角色，正文与标签使用 16/24、14/20、12/16；强调字重按对应角色设置。
- 进度滑块以 XS 的 16 dp 轨道和 44 × 4 dp 滑块比例表达，保持 48 CSS px 交互区域。
- 网页以 CSS px 近似表达 dp / sp 的审核比例；窗口画框、两列宽度、间距、素材舞台、标签放置和操作编排均为 RxPlayer 自定义设计决定，不是官方音乐应用模板。
- 使用技能给出的 spatial / effects 网页回退曲线表达形状和进入反馈；减少动态效果偏好下关闭动画。

## 资源与验证

字体与许可复用 [原型 01 的本地资源](../prototype-v1/README.md#静态资源来源)；四张原创场景 SVG 复用 [原型 02](../prototype-v2/README.md)。插画仅表达示例音乐内容，不承担实际歌曲元数据。

2026-10-28 浏览器检查：

- 29 项核心交互通过：搜索结果组织、三音源、播放 / 暂停、切歌、收藏、拖动、队列、歌单、模式状态、主题、模拟账号、异常恢复、链接域名边界和输入模式。
- 320 / 390 CSS px Android 预览、900 / 1440 CSS px Windows 预览，各深浅主题下的搜索 / 音乐库 / 设置及播放页均无水平溢出；播放页按钮目标至少 48 CSS px，Tab 循环正常。
- 深浅主题各 8 组主要文字颜色对超过 4.5:1，最低约 6.18:1；字体加载正常，资源请求和最后控制台检查没有错误。
- 视觉检查桌面播放 / 浅色搜索、手机播放 / 音乐库；复测空格播放、状态变化后的焦点保留与返回恢复、主按钮形状变化、面板进入动画及减少动态效果偏好。主播放 96 × 96、切歌 56 × 56 CSS px；独立播放视图在进入动画结束后覆盖产品内容区域。

检查不代表真机软键盘、系统返回、原生播放或屏幕阅读器已验收。本轮不构建原生应用；设计待用户审核，继续推送审核分支，确认前不合并主分支。
