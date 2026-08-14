# Android License Overlay Assistant

一个面向自用场景的超轻量 Android 悬浮激活助手。它只在**闲鱼 / 千牛及已学习的应用分身**位于前台时显示悬浮球；用户复制发货话术后点击悬浮球，应用只在这次明确交互中读取一次剪贴板、提取纯数字激活码，并在悬浮层完成服务器操作。

> 当前版本：`0.1.0`，重点是主框架和服务器协议。服务器端尚未绑定具体实现。

## 已实现

- 仅监听 `TYPE_WINDOW_STATE_CHANGED`，不读取页面节点，不读取聊天内容。
- 使用 `TYPE_ACCESSIBILITY_OVERLAY`，无需普通悬浮窗权限。
- 默认识别闲鱼 `com.taobao.idlefish` 和千牛 `com.taobao.qianniu`。
- 支持学习 OEM 应用分身实际包名。
- Android 10+ 剪贴板限制下，通过用户点击后启动透明 `ClipboardBridgeActivity` 获取一次前台焦点并读取剪贴板。
- 激活码优先按 `激活码 / 卡密 / 授权码` 标签提取，支持纯数字兜底并过滤常见密码/手机号/订单号上下文。
- 多个候选码时不执行服务器动作。
- 服务器 Token 使用 Android Keystore + AES/GCM 保存。
- 网络层只接受 HTTPS，不关闭证书校验。
- 无 Compose、WebView、Room、Retrofit、OkHttp、OCR、ML Kit。
- Release 开启 R8 与资源压缩。

## 四个正式动作

客户端和服务器之间固定使用以下动作名：

| 中文动作 | wire action | 参数 |
| --- | --- | --- |
| 激活 | `activate` | `code` |
| 退款停用 | `refund_disable` | `code` |
| 续期 | `renew` | `code`, `days` |
| 解绑 | `unbind` | `code` |

另外保留只读查询：`POST /api/license/query`。

完整协议见 [`docs/SERVER_CONTRACT.md`](docs/SERVER_CONTRACT.md)。

## 使用流程

1. 安装 APK，首次打开“激活助手”。
2. 配置 HTTPS 服务器地址、Bearer Token（可空）、默认续期天数。
3. 打开系统无障碍设置，启用“激活助手前台检测”。
4. 在闲鱼或千牛里发送发货话术并复制整段消息。
5. 点击右侧“码”悬浮球。
6. 自动提取激活码并查询服务器状态。
7. 在悬浮面板执行：激活 / 退款停用 / 续期 / 解绑。
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
            ├─ query
            └─ action
                 ├─ activate
                 ├─ refund_disable
                 ├─ renew(days)
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

仓库 CI 使用 `gradle/actions/setup-gradle` 提供 Gradle 8.13，因此不依赖仓库内的二进制 wrapper JAR。

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

纯 Kotlin 的激活码解析和四动作定义可以脱离 Android SDK 测试。

## 当前边界

- 不 OCR、不截图、不录屏。
- 不自动读取闲鱼/千牛聊天页面。
- 不后台监听剪贴板。
- 不自动发消息或点击闲鱼/千牛界面。
- 不上传剪贴板全文，只把解析后的激活码发给配置的服务器。
- 分身的剪贴板是否跨 Profile 可见取决于 OEM；必须在实际手机上验证。

## 目录

源码集中在 `app/src/main/java/com/wulisu/licenseoverlay/`，服务器协议见 `docs/SERVER_CONTRACT.md`，后续智能体约束见 `AGENTS.md`。
