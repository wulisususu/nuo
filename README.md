# Android License Overlay Assistant

仓库现在同时维护两个可共存版本。

## 版本 A：V4 无障碍版

模块：`app`

```text
applicationId: com.wulisu.licenseoverlay
version: 0.4.0
```

特点：

- 使用 `TYPE_ACCESSIBILITY_OVERLAY`。
- 启用无障碍后，亮屏时所有页面显示悬浮球。
- 点击悬浮球原地读取一次剪贴板，不再启动透明 Activity。
- 灭屏隐藏、亮屏恢复。

## 版本 B：无需无障碍 V1 悬浮窗版

模块：`overlayonly`

```text
applicationId: com.wulisu.licenseoverlay.overlayonly
version: 1.0.0-noaccessibility
```

特点：

- **不申请、也不使用无障碍服务。**
- 只需要用户授予系统“显示在其他应用上层”权限。
- 使用 `SYSTEM_ALERT_WINDOW + TYPE_APPLICATION_OVERLAY`。
- 使用 Android 14+ 要求的 `specialUse` 前台服务类型保持用户主动开启的悬浮服务。
- 亮屏时悬浮球常驻所有页面；灭屏隐藏。
- 点击“码”时悬浮窗口短暂获取输入焦点，读取一次剪贴板，随后恢复 `FLAG_NOT_FOCUSABLE`。
- 不启动 Activity，因此不会因为读取剪贴板跳回本应用。
- 和 V4 包名不同，可以同时安装；测试其中一个时关闭另一个的悬浮服务即可。

## 共用业务逻辑

两个版本共享同一套已验证源码：

```text
app/src/main/java/com/wulisu/licenseoverlay/api/
app/src/main/java/com/wulisu/licenseoverlay/config/
app/src/main/java/com/wulisu/licenseoverlay/core/
```

因此激活码解析、服务器查询、Basic Auth、激活 / 退款停用 / 续期 / 解绑规则保持一致。

服务器仍直接适配 `wulisususu/activation-code-system`：

```text
GET  /backend/cards?q=<code>&limit=20&offset=0
GET  /api/test-card-stock/list?q=<code>&page=1&page_size=20
POST /backend/cards/{id}/activate
POST /backend/cards/{id}/refund/disable
POST /backend/cards/{id}/renew
POST /backend/cards/{id}/unbind
```

支持 HTTP / HTTPS、Basic Auth，以及 Basic 未配置时的 Bearer Token 回退。

公开源码不提交真实认证密码；私密构建可注入：

```text
ACTIVATION_BASE_URL
ACTIVATION_BASIC_USER
ACTIVATION_BASIC_PASSWORD
```

## 隐私边界

两个版本都：

- 不后台监听剪贴板；
- 只在用户点击悬浮球时读取一次；
- 不上传剪贴板全文，只处理提取后的激活码；
- 不 OCR、不截图、不录屏；
- 不读取第三方 App 页面节点；
- 不自动点击或发消息；
- 无 Compose / WebView / Room / Retrofit / OkHttp。

## 构建

```text
compileSdk 36
targetSdk 36
minSdk 29
JDK 17
Gradle 8.13
```

同时构建两版：

```bash
gradle --no-daemon :app:assembleDebug :overlayonly:assembleDebug
```

产物：

```text
app/build/outputs/apk/debug/app-debug.apk
overlayonly/build/outputs/apk/debug/overlayonly-debug.apk
```
