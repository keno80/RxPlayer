# 原型 03 设计与审核说明

状态：待用户审核。用户要求学习参考应用的配色与组件运用，并结合 Google 官方 UX / UI 文档重新设计；没有批准上一版或后续原生实现。

## 设计范围

新增 [原型 03](../../../design/prototype-v3/index.html)，保留 01 / 02。本轮以任务和注意力层级重新选择组件，熟悉的播放控制采用图标，搜索 / 歌单 / 异常动作按语义保留文字；不把参考应用的文案、特殊轮廓和构图作为模板。

- 独立静态角色方案：蓝绿主色、暖杏第三色；通过 MCU 生成，记录角色与种子。
- 当前播放：独立内容视图，图标填充主操作；Android 单列，Windows 双列并提供接下来队列。
- 搜索：contained search、音源单选、最佳匹配与其余版本，保留垂直列表。
- 音乐库与设置：以内容和逻辑分组组织；必要文字与熟悉图标按情境搭配。

依据为用户指定的 [Google UX 研究](https://design.google/library/expressive-material-design-google-research)、[M3 颜色系统](https://m3.material.io/styles/color/system/overview) 与 m3-expressive 技能。具体组件选择、官方数值和自定义边界见 [原型说明](../../../design/prototype-v3/README.md)。

## 审核与交付

审核重点：主播放是否直观，蓝绿 / 暖杏角色分工是否协调，双端播放视图与返回路径是否自然，搜索和音乐库是否好用，深浅主题是否都成立。

完成浏览器自检仅表示交付的交互原型可审阅，不代表原生功能完成或设计已被用户认可。继续使用 `codex/prototype-review` 提交推送，用户确认前不合并；后续根据反馈集中修订。
