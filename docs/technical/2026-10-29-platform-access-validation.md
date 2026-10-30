# JDK 17、Android、权限与链接有效期验证

- 日期：2026-10-29，北京时间。
- 阶段：M1；正式应用的 UI、播放接口和依赖未修改。
- 授权：用户明确允许构建并安装独立 Android 验证 APK；USB 安装被手机拦截后，用户手动允许，再安装成功。

## 1. JDK 17

使用本机 Temurin **17.0.17+10** 运行原有 NewPipe 源码启动探针，而非依据 classfile 版本推断。随后由 mpv 实际播放 Bilibili AAC、网易云 MP3、YouTube AAC / Opus，四条格式的暂停、30 秒拖动、继续、stop、快速替换和退出重开均通过。

见 [JDK 17 运行记录](../evidence/m1/2026-10-29-jdk17-runtime.json)。这证明当前独立验证路径可以在 Java 17 执行，不等于正式 Compose 工程已经接入或重新构建。

## 2. Android 构建与真机

测试工程位于 [android-probe](../../scripts/m1/android-probe/app/build.gradle.kts)，包名 `com.keno.rxplayer.m1probe`，与正式应用隔离。构建使用 Gradle 8.14.3、AGP 8.11.2、JDK 17；compile / target SDK 36、min SDK 26，Media3 1.8.0、OkHttp 4.12.0、NewPipeExtractor v0.26.5、`desugar_jdk_libs_nio` 2.1.5。

NewPipe 作者说明 minSdk <33 时需要 nio desugaring，见 [固定版本 README](https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/README.md)；配置方式参考 [Android 官方 desugaring 指南](https://developer.android.com/studio/write/java8-support)。本轮构建完成 desugar / DEX / 打包，不以配置文件代替运行证据。

`assembleDebug` 成功，有两条依赖的 `RestrictTo.Scope.LIBRARY_GROUP` 注解枚举警告；未造成编译或本轮运行失败。APK 的散列、大小及版本见 [构建来源记录](../evidence/m1/2026-10-29-android-provenance.json)。APK、构建输出、JAR 和签名文件不加入 Git。

当前手机运行 **Android 17 / API 37**。探针只声明普通 `INTERNET` 权限，不请求媒体读取、外部存储、联系人或账号访问。前台静音运行，不建立后台播放服务或申请通知权限。

| 运行方式 | Bilibili AAC | 网易云 MP3 | YouTube AAC / Opus |
|---|---|---|---|
| 手机当前网络直连 | 通过 | 通过 | 解析超时 |
| 仅测试 APK 经 USB 转发使用电脑已有代理 | 通过 | 通过 | 两种格式均通过，解析出 5 条音轨 |

证据：[手机直连](../evidence/m1/2026-10-29-android-direct.json)、[网络一致后的真机结果](../evidence/m1/2026-10-29-android-proxy.json)。Android 的解析 Downloader 和 Media3 数据源共用同一 OkHttp 网络配置，未改手机系统代理或 VPN。

四条格式均取得 HTTPS Range 206 / 65536 字节，Media3 AudioSession 已创建，解码 MIME 与输入相符，进度推进；暂停约 0.9 秒漂移均为 0 ms，拖到 30000 ms，继续后超过 30700 ms，随后 release 返回。

测试完成后停止并卸载该 APK、移除本次 USB 端口转发，见 [清理记录](../evidence/m1/2026-10-29-android-cleanup.json)。没有做人耳音质、Android 8 / API 26 真机、R8 release、后台 / 通知 / 锁屏或系统媒体会话验收；当前结果不能扩展成这些项目通过。

## 3. 来源权限与不可用状态

全部使用匿名请求，未读取用户的登录会话。见 [来源访问记录](../evidence/m1/2026-10-29-access-expiry.json)。

| 来源 / 样本 | 实际结果 | 可得结论 |
|---|---|---|
| 网易云 `139774` | 资源码 200、音频 URL 存在、无 trial 标记，已实际播放 | 当前匿名样本可播放；不是所有歌曲均可播放的证据 |
| 网易云 `509781655`、`210049` | 搜索 fee=1，资源码 -110、URL 缺失、size=0 | 搜索命中但当前会话不可取得音频；不能仅凭 fee 或 -110 推断具体是会员、版权还是地区原因 |
| 网易云 `5257138` | 资源码 200、URL 存在、无 trial 标记 | 不同结果应独立判断，不能统一按搜索状态标记 |
| 网易云 ID=0 | 资源码 404，无 URL | 内容不存在 / 输入不可用，应与网络错误区分 |
| Bilibili 匿名 nav | 业务码 -101 | 会话未登录，不能伪装为已登录 |
| Bilibili aid=1 | 业务码 62002 | 该内容不可用，不能当成临时网络错误无限重试 |
| YouTube `aaaaaaaaaaa` | 页面 HTTP 200，但 `playabilityStatus=ERROR`、无 streamingData | HTTP 成功不等于可播放 |

YouTube 不可用样本分别用 [JDK 17 NewPipe](../evidence/m1/2026-10-29-youtube-unavailable.json) 与 [页面状态](../evidence/m1/2026-10-29-youtube-unavailable-page.json) 观察。此样本 NewPipe 抛出 `ParsingException`，没有直接给出专门的权限类型；正式错误映射不能把所有解析异常都翻译成“需要登录”。需要保留解析失败与明确平台不可用状态的区别。

本轮未取得实际“试听片段”样本，未验证持有会员权益的账号登录前后差异、二维码登录、地区切换或退出后凭据失效；这些项目保持未通过。没有通过跨源替换、第三方解锁或换账号掩盖原平台不可用结果。

## 4. 凭据存储基础能力

- **Windows DPAPI**：模拟字符串按 CurrentUser 范围加密、写入临时文件、读回解密一致；篡改密文被拒绝，临时文件清除，见 [DPAPI 记录](../evidence/m1/2026-10-29-dpapi.json)。依据 [Microsoft CryptProtectData](https://learn.microsoft.com/en-us/windows/win32/api/dpapi/nf-dpapi-cryptprotectdata)。
- **Android Keystore**：应用专属 AES-GCM 密钥完成模拟数据往返，`getEncoded()` 为空，测试结束删除专用 alias。两次手机运行均通过，依据 [Android Keystore](https://developer.android.com/privacy-and-security/keystore)。

这验证加密原语，不代表真实 Cookie / OAuth 持久化、应用重启恢复或退出清理流程已经实现；AUTH-01～03 不能整体勾选通过。

## 5. 有效期与重新解析

### 受控硬过期

[受控 HTTP 过期记录](../evidence/m1/2026-10-29-controlled-expiry.json)：本地服务签发短时有效的测试 URL，首次返回 206，自然等待有效期结束后同一 URL 返回 410，重新签发同一内容的新 URL 后恢复 206，并由 mpv 实际播放 / 拖动 / 继续。

这证明独立技术探针可以区分资源失效与刷新后的可用资源，**不是三个真实 CDN 已在到期后拒绝旧链接的证据，也不是正式应用的自动恢复功能已完成**。没有篡改系统时间或把修改签名参数当作自然过期。

### 网易云标称 TTL

对接口标称 `expi=1200` 秒的原始 URL，保持其只在观察进程内存，等待实际时间超过 1200 秒再加 30 秒余量后重读；同时重新解析相同歌曲并读取新结果。[完整记录](../evidence/m1/2026-10-29-access-expiry.json) 保存原始与刷新后的 HTTP 状态，不保存 URL。

实际从 14:31:44 等到 **14:52:14** 后，原始地址仍返回 206；重新解析得到不同地址，也返回 206。因此本样本中 `expi=1200` 没有表现为该时刻的 CDN 硬拒绝。可以据它安排重新解析，但不能只因时间超过 TTL 就断言原资源必已失效。

### Bilibili / YouTube 长时效观察

只读观察已启动，当前 [待验收快照](../evidence/m1/2026-10-29-signed-expiry-pending.json) 记录了签发后的成功读取与将来的检查时刻。Bilibili 样本到期后检查约为北京时间 **16:48**，YouTube AAC / Opus 约为 **20:50**。

完整运行结果写入仓库外 `%TEMP%/rxplayer-m1-runtime/signed-expiry-observation.json` 和 `youtube-signed-expiry-observation.json`，不会在后台改动已提交的仓库。原始签名 URL 仅在两个观察进程内存中；进程若中断，不能恢复该旧链接的到期验证，须如实重新采样。没有持续播放，也不占用手机。

截至本批交付，长时效旧链接仍未到期，相关验收保持未完成。后续读取结果后再更新证据、判断旧资源是否拒绝及刷新是否恢复。

## 6. 复现入口

从仓库根目录执行；本轮构建授权仅针对独立探针，正式应用不在命令范围。

```powershell
$m1Java = "$env:USERPROFILE/.gradle/jdks/eclipse_adoptium-17-amd64-windows.2/bin/java.exe"
$m1Runtime = "$env:TEMP/rxplayer-m1-runtime"
python scripts/m1/validate_streaming.py --java $m1Java --mpv "$m1Runtime/mpv/mpv.exe" --newpipe-dir "$m1Runtime/newpipe" --output "$m1Runtime/jdk17-recheck.json"
./gradlew.bat -p scripts/m1/android-probe :app:assembleDebug -Pm1RuntimeDir="$m1Runtime/newpipe"
./scripts/m1/probe_dpapi.ps1 -OutputPath "$m1Runtime/dpapi-recheck.json"
python scripts/m1/probe_access_expiry.py --wait-ttl --output "$m1Runtime/access-expiry-recheck.json"
python scripts/m1/probe_expiry_recovery.py --mpv "$m1Runtime/mpv/mpv.exe" --output "$m1Runtime/controlled-expiry-recheck.json"
```

Android 构建也需让 `JAVA_HOME` 指向已验证 JDK 17。安装探针后通过 `adb shell am start -n com.keno.rxplayer.m1probe/.ProbeActivity` 启动；`adb shell run-as com.keno.rxplayer.m1probe cat files/report.json` 读取白名单报告。网络桥接仅用于本轮独立验证：将 USB 测试端口转发到已有主机代理，并给探针传 `--ei proxy_port`，不改手机全局设置。重复时先确认目标设备，结束后仅移除自己创建的转发和测试包。

签名长时效观察可使用 `probe_signed_expiry.py --source bilibili|youtube --java ... --newpipe-dir ... --output ...`，结果路径放在仓库外。所有脚本均需读取报告中的具体状态，不以退出码或“报告已生成”当作平台通过。
