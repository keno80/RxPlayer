# Windows 系统媒体与托盘阶段

用户授权开始 Windows 系统播放集成。复用现有单窗口与 PlaybackRuntime，不新建播放器；Java 17 保持不变。

## 本批技术范围

- 通过自有 C++/WinRT 小型 DLL + 现有 JNA 连接窗口所属的 SMTC，使用 Microsoft 的 GetForWindow 桌面入口。原生事件只入队，在 Compose 主线程调用现有播放 / 暂停 / 停止 / 切歌逻辑，不注册抢占其他播放器的全局键盘钩子。
- 曲目名、作者、状态、时间线、上一 / 下一曲可用性同步至同一会话；封面按当前曲目标识更新，不传递音频直链。
- 原生桥通过现有 Windows Build Tools / SDK 构建，随 desktopJar 打包；所有 WinRT 对象在专属线程创建 / 更新 / 释放，退出撤销事件订阅。
- 定向测试状态投影与命令分发，编译并运行 Windows 原生会话；不安装 Android 包或重走双端 UI 回归。

## 原型先行范围

在唯一完整原型内演示托盘菜单：打开 RxPlayer、播放 / 暂停、上一曲 / 下一曲、退出；关闭按钮改为收起到托盘，退出明确停止播放、保存并释放资源。托盘不支持时保留关闭即退出。所有托盘 / 关闭行为为待审核提案，确认前不接正式 UI 或改变现有关闭行为。

## 步骤

- [x] 写状态 / 命令测试并观察失败；实现后 2 项系统媒体 + 8 项运行时测试通过。
- [x] 实现原生桥、桌面绑定与生命周期清理；禁用 mpv 双媒体入口并经定向审查。
- [x] 编译及定向运行验证，Java 17 / 原生桥构建、系统会话管理器元数据与 Pause / Next 事件探针通过，正式 Windows 已启动并连接 SMTC；系统面板视觉 / 硬件媒体键待用户审核。
- [x] 同一完整原型更新托盘与退出提案，4 项相关模拟交互通过；修正用户指出的两个缺失字体图标，提交推送审核分支。
- [ ] 用户确认后接正式托盘与关闭行为，完成阶段合并。

官方依据：https://learn.microsoft.com/en-us/windows/win32/api/systemmediatransportcontrolsinterop/nf-systemmediatransportcontrolsinterop-isystemmediatransportcontrolsinterop-getforwindow
