# Android License Overlay Assistant

仓库同时维护两个可共存版本。

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

## 版本 B：无需无障碍 V2 悬浮窗版

模块：`overlayonly`

```text
applicationId: com.wulisu.licenseoverlay.overlayonly
version: 2.0.0-noaccessibility
```

特点：

- **不申请、也不使用无障碍服务。**
- 只需要用户授予系统“显示在其他应用上层”权限。
- 使用 `SYSTEM_ALERT_WINDOW + TYPE_APPLICATION_OVERLAY`。
- 使用 Android 14+ 的 `specialUse` 前台服务类型保持用户主动开启的悬浮服务。
- 亮屏时悬浮球常驻所有页面；灭屏隐藏。
- 点击“码”时悬浮窗口短暂获取输入焦点，读取一次剪贴板，随后恢复 `FLAG_NOT_FOCUSABLE`。
- 不启动 Activity，因此不会因为读取剪贴板跳回本应用。
- 和 V4 包名不同，可以同时安装；测试其中一个时关闭另一个即可。

### V2 创建功能

悬浮面板按钮为：

```text
激活 / 退款停用 / 创建 / 解绑
```

“创建”替代了 V1 的“续期 30 天”。

当剪贴板识别到 **6–12 位纯数字**，且正式授权表、库存表都不存在该号码时，“创建”按钮会启用，并调用：

```text
POST /backend/cards
```

创建固定为测试服通用正式卡：

```text
card_no = 输入的 6–12 位数字
card_secret = 同一串数字
status = activated
game_scope = ALL
scope = ALL
duration_kind = PERMANENT
source_type = DIRECT
binding_status = unbound
```

也就是测试服 ALL 通用、永久、已激活、未绑定的正式授权卡。后台已有相同卡号/卡密时不会重复创建。

### 中文状态显示

V2 悬浮面板会把服务器状态翻译成中文，例如：

```text
unused -> 未使用
available -> 可用
issued -> 已发出
activated -> 已激活
used -> 已使用
expired -> 已过期
disabled -> 已停用
deleted -> 已删除
discarded -> 已废弃

unbound -> 未绑定
bound -> 已绑定
binding -> 绑定中
replaced -> 已替换
refunded -> 已退款
refund_blocked -> 退款锁定
blocked -> 已锁定
released -> 已解绑
```

## 共用业务逻辑

两个版本共享：

```text
app/src/main/java/com/wulisu/licenseoverlay/api/
app/src/main/java/com/wulisu/licenseoverlay/config/
app/src/main/java/com/wulisu/licenseoverlay/core/
```

服务器直接适配 `wulisususu/activation-code-system`：

```text
GET  /backend/cards?q=<code>&limit=20&offset=0
GET  /api/test-card-stock/list?q=<code>&page=1&page_size=20
POST /backend/cards
POST /backend/cards/{id}/activate
POST /backend/cards/{id}/refund/disable
POST /backend/cards/{id}/renew
POST /backend/cards/{id}/unbind
```

V4 仍保留原有续期功能；V2 悬浮窗版不显示续期按钮。

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
