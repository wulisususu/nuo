# Android License Overlay Assistant

一个自用的超轻量 Android 悬浮激活助手。仅在**闲鱼 / 千牛及已学习的应用分身**位于前台时显示悬浮球；复制发货话术后点击悬浮球，应用读取一次剪贴板、提取纯数字激活码，并直接调用 `wulisususu/activation-code-system` 的现有后台接口。

> 当前版本：`0.3.0`。

## 已实现

- 只用 Accessibility 窗口事件判断前台 App，`canRetrieveWindowContent=false`，不读取聊天节点。
- `TYPE_ACCESSIBILITY_OVERLAY` 悬浮球，无需普通悬浮窗权限。
- 默认闲鱼 `com.taobao.idlefish`、千牛 `com.taobao.qianniu`，支持学习 OEM 应用分身包名。
- Android 10+ 下仅在用户点击悬浮球后，通过透明 `ClipboardBridgeActivity` 读取一次剪贴板。
- 激活码本地解析，多候选时禁止自动操作。
- 直接查询正式授权；未命中时只读查询库存码。
- 四个动作：激活、退款停用、续期、解绑。
- 支持 **HTTP / HTTPS**。
- 支持 **HTTP Basic Auth**；若 Basic Auth 未配置，才回退到 Bearer Token。
- Basic Auth 密码和 Bearer Token 的用户配置使用 Android Keystore + AES/GCM 保存。
- 无 Compose、WebView、Room、Retrofit、OkHttp、OCR、ML Kit。

## 服务器地址与私密构建

公开源码只保存非敏感的默认服务器地址；**Basic Auth 密码不会提交到本仓库**。

构建时可以通过 Gradle property 或环境变量注入：

```text
ACTIVATION_BASE_URL
ACTIVATION_BASIC_USER
ACTIVATION_BASIC_PASSWORD
```

例如私有 CI 可以在不修改 Kotlin 源码的情况下生成已预配置 APK。若没有注入，首次启动仍可在配置页手工填写。

## 当前真实接口映射

| 功能 | 接口 |
| --- | --- |
| 查询正式授权 | `GET /backend/cards?q=<code>&limit=20&offset=0` |
| 查询库存码 | `GET /api/test-card-stock/list?q=<code>&page=1&page_size=20` |
| 激活 | `POST /backend/cards/{id}/activate` |
| 退款停用 | `POST /backend/cards/{id}/refund/disable` |
| 续期 | `POST /backend/cards/{id}/renew` |
| 解绑 | `POST /backend/cards/{id}/unbind` |

列表查询返回后，客户端仍会再次精确检查 `card_secret == code || card_no == code`，不会直接使用模糊搜索第一条结果。

### 续期

严格跟随当前后端规则：

- `1..998`：有限小时数；
- `999`：永久；
- 默认 `720` 小时（30 天）；
- 只有后端允许的状态才能续期。

## 使用

1. 安装 APK。
2. 首次打开并启用“激活助手前台检测”无障碍服务。
3. 在闲鱼 / 千牛发送发货内容并复制整段文字。
4. 点击悬浮球“码”。
5. 自动识别激活码并查询状态。
6. 在悬浮面板执行激活 / 退款停用 / 续期 / 解绑。
7. 切到其他 App 后悬浮层自动隐藏。

## 构建

```text
compileSdk 36
targetSdk 36
minSdk 29
AGP 8.11.1
Kotlin 2.1.20
Gradle 8.13
JDK 17
```

GitHub Actions 会执行：

```bash
gradle --no-daemon :app:assembleDebug
```

并上传：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 安全边界

- 不 OCR、不截图、不录屏。
- 不读取闲鱼/千牛聊天节点。
- 不后台监听剪贴板。
- 不自动发消息或点击闲鱼/千牛 UI。
- 剪贴板全文不落盘、不上传。
- Basic Auth 密码不应提交到公开源码或公开 CI 配置。
- 当前因服务器使用普通 HTTP，Manifest 允许 cleartext；迁移 HTTPS 后应重新收紧。

后续智能体约束见 [`AGENTS.md`](AGENTS.md)，服务器适配说明见 [`docs/SERVER_CONTRACT.md`](docs/SERVER_CONTRACT.md)。
