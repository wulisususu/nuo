# Android License Overlay Assistant

当前版本：`0.4.0`

这是一个自用的超轻量 Android 悬浮激活助手。

## 当前模式

- 启用无障碍服务后，只要设备处于亮屏/可交互状态，悬浮球就在所有页面显示。
- 不再区分闲鱼、千牛、微信、桌面、设置或应用分身。
- 灭屏后自动隐藏；亮屏后自动恢复。
- 点击悬浮球不会打开本应用、不会切换 Activity。
- 点击后悬浮窗口会短暂获得输入焦点，只读取一次当前剪贴板，然后立刻恢复不可聚焦状态。
- 识别到激活码后直接在悬浮面板查询并执行激活 / 退款停用 / 续期 / 解绑。
- 多候选激活码时禁止自动写操作。

## 剪贴板实现

Android 10+ 在应用没有输入焦点时可能不允许 `ClipboardManager.getPrimaryClip()`。0.4.0 不再使用透明 `ClipboardBridgeActivity`，避免点击悬浮球时把本应用任务栈带到前台。

当前实现：

```text
用户点击悬浮球
→ 先尝试读取剪贴板
→ 如果系统拒绝，临时把 TYPE_ACCESSIBILITY_OVERLAY 设为可聚焦
→ requestFocus()
→ 读取一次剪贴板
→ 立即恢复 FLAG_NOT_FOCUSABLE
→ 在当前页面显示悬浮结果面板
```

不会后台监听剪贴板，不会上传剪贴板全文。

## 服务器

继续直接适配 `wulisususu/activation-code-system`：

```text
GET  /backend/cards?q=<code>&limit=20&offset=0
GET  /api/test-card-stock/list?q=<code>&page=1&page_size=20
POST /backend/cards/{id}/activate
POST /backend/cards/{id}/refund/disable
POST /backend/cards/{id}/renew
POST /backend/cards/{id}/unbind
```

支持 HTTP / HTTPS、Basic Auth，以及 Basic 未配置时的 Bearer Token 回退。

公开源码不提交真实认证密码。私密 APK 可通过构建环境变量注入：

```text
ACTIVATION_BASE_URL
ACTIVATION_BASIC_USER
ACTIVATION_BASIC_PASSWORD
```

## 技术边界

- Kotlin + Android Framework
- `TYPE_ACCESSIBILITY_OVERLAY`
- `PowerManager.isInteractive()` + `ACTION_SCREEN_ON/OFF`
- 不 OCR、不截图、不录屏
- 不读取页面节点
- 不自动点击第三方 App
- 不后台监听剪贴板
- 无 Compose / WebView / Room / Retrofit / OkHttp

## 构建

```text
compileSdk 36
targetSdk 36
minSdk 29
JDK 17
Gradle 8.13
```

GitHub Actions 构建：

```bash
gradle --no-daemon :app:assembleDebug
```
