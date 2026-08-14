# Android License Overlay Assistant

一个面向自用场景的超轻量 Android 悬浮激活助手。它只在**闲鱼 / 千牛及已学习的应用分身**位于前台时显示悬浮球；用户复制发货话术后点击悬浮球，应用只在这次明确交互中读取一次剪贴板、提取纯数字激活码，并在悬浮层完成服务器操作。

> 当前版本：`0.2.0`。已直接适配 `wulisususu/activation-code-system` 当前后台卡密接口。

## 已实现

- 仅监听 `TYPE_WINDOW_STATE_CHANGED`，不读取页面节点，不读取聊天内容。
- 使用 `TYPE_ACCESSIBILITY_OVERLAY`，无需普通悬浮窗权限。
- 默认识别闲鱼 `com.taobao.idlefish` 和千牛 `com.taobao.qianniu`。
- 支持学习 OEM 应用分身实际包名。
- Android 10+ 剪贴板限制下，通过用户点击后启动透明 `ClipboardBridgeActivity` 获取一次前台焦点并读取剪贴板。
- 激活码优先按 `激活码 / 卡密 / 授权码` 标签提取，支持纯数字兜底并过滤常见密码/手机号/订单号上下文。
- 多个候选码时不执行服务器动作。
- 直接查询 `activation-code-system` 的正式授权和库存码。
- 服务器 Token 使用 Android Keystore + AES/GCM 保存。
- 网络层只接受 HTTPS，不关闭证书校验。
- 无 Compose、WebView、Room、Retrofit、OkHttp、OCR、ML Kit。
- Release 开启 R8 与资源压缩。

## 已对接的真实服务器接口

客户端不再使用占位的 `/api/license/query` / `/api/license/action`，而是直接复用 `activation-code-system` 当前后台 API。

| 中文动作 | 服务器接口 | 请求体 |
| --- | --- | --- |
| 查询 | `GET /backend/cards?q=<code>&limit=20&offset=0` | - |
| 激活 | `POST /backend/cards/{id}/activate` | `{}` |
| 退款停用 | `POST /backend/cards/{id}/refund/disable` | `{}` |
| 续期 | `POST /backend/cards/{id}/renew` | `{"hours": N}` |
| 解绑 | `POST /backend/cards/{id}/unbind` | - |

正式授权查询不到时，客户端会额外只读查询：

```text
GET /api/test-card-stock/list?q=<code>&page=1&page_size=20
```

如果命中库存码，悬浮窗会显示库存状态，但四个正式授权动作保持禁用。

完整适配说明见 [`docs/SERVER_CONTRACT.md`](docs/SERVER_CONTRACT.md)。

## 续期规则

这里严格跟随现有 `activation-code-system`，不自行发明时间规则：

- 只有**已过期且仍保留当前设备绑定**的卡可以续期；
- `1..998` 表示续期小时数；
- `999` 表示转成永久授权；
- APK 默认是 `720` 小时，即 30 天；
- 为避免服务端把 `>=999` 都解释为永久，APK 不允许配置大于 999 的数值。

## 使用流程

1. 安装 APK，首次打开“激活助手”。
2. 配置激活码系统的 HTTPS Base URL、Bearer Token（可空）、默认续期小时数。
3. 打开系统无障碍设置，启用“激活助手前台检测”。
4. 在闲鱼或千牛里发送发货话术并复制整段消息。
5. 点击右侧“码”悬浮球。
6. 自动提取激活码，并精确查询服务器正式授权；未命中时再查库存。
7. 正式授权卡可直接执行：激活 / 退款停用 / 续期 / 解绑。
8. 切到其他 App 后悬浮层自动隐藏。

### 应用分身

不同 OEM 的分身实现不同：有的保留原 packageName，有的暴露新的包名。主界面提供“学习闲鱼分身 / 学习千牛分身”：点击后切到对应分身，AccessibilityService 会记录下一个外部 App 的 packageName 并加入白名单。

## 架构

```text
LicenseAccessibilityService
  ├─ TargetAppRegistry      # 闲鱼/千牛/分身白名单
  └─ OverlayController
       ├─ ClipboardBridge   # 用户点击后一次性读取剪贴板
       ├─ ActivationCodeParser
       └─ LicenseApi
            ├─ resolve backend card by exact code
            ├─ fallback read-only stock lookup
            └─ activation-code-system adapter
                 ├─ activate
                 ├─ refund/disable
                 ├─ renew(hours)
                 └─ unbind
```

## 为什么需要透明 ClipboardBridgeActivity

Android 10（API 29）起，非默认输入法且当前没有焦点的应用不能访问剪贴板。因此本项目**不后台监听剪贴板**。用户点击悬浮球后，透明 Activity 瞬时获取前台焦点、读取一次剪贴板并立刻 `finish()`，然后结果返回悬浮层。

官方文档：

- Android 10 clipboard privacy: <https://developer.android.com/about/versions/10/privacy/changes>
- AccessibilityService: <https://developer.android.com/guide/topics/ui/accessibility/service>
- AccessibilityEvent: <https://developer.android.com/reference/android/view/accessibility/AccessibilityEvent>

## 权限

Manifest 只声明：

```text
android.permission.INTERNET
android.permission.BIND_ACCESSIBILITY_SERVICE（系统绑定权限）
```

Accessibility 配置明确：

```text
canRetrieveWindowContent = false
```

即前台门禁只看窗口事件携带的 packageName，不遍历页面节点。

## 构建

当前工程版本：

```text
compileSdk 36
targetSdk 36
minSdk 29
AGP 8.11.1
Kotlin 2.1.20
Gradle 8.13
JDK 17
```

仓库 CI 使用 `gradle/actions/setup-gradle` 提供 Gradle 8.13，并在构建成功后上传 `app-debug.apk` Artifact。

本地已有 Gradle 8.13 时：

```bash
gradle :app:assembleDebug
```

如需标准 Gradle Wrapper：

```bash
gradle wrapper --gradle-version 8.13
./gradlew :app:assembleDebug
```

APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 核心烟雾测试

纯 Kotlin 的激活码解析和四动作定义可以脱离 Android SDK 测试。`scripts/CoreSmokeTest.kt` 同时校验 `renewHours` 的 `1..999` 约束。

## 当前边界

- 不 OCR、不截图、不录屏。
- 不自动读取闲鱼/千牛聊天页面。
- 不后台监听剪贴板。
- 不自动发消息或点击闲鱼/千牛界面。
- 不上传剪贴板全文，只把解析后的激活码用于配置服务器的精确查询。
- 不改变 `activation-code-system` 现有业务状态机；APK 只是轻量管理入口。
- 分身的剪贴板是否跨 Profile 可见取决于 OEM；必须在实际手机上验证。

## 目录

源码集中在 `app/src/main/java/com/wulisu/licenseoverlay/`，真实服务器适配见 `docs/SERVER_CONTRACT.md`，后续智能体约束见 `AGENTS.md`。
