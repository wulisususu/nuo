# AGENTS.md

## Project intent

仓库维护两个可共存的 Android 激活助手：

1. `app`：V4 无障碍版，applicationId `com.wulisu.licenseoverlay`。
2. `overlayonly`：无需无障碍 V1，applicationId `com.wulisu.licenseoverlay.overlayonly`。

两版都只在用户主动点击悬浮球后读取一次剪贴板、提取激活码，并调用 `wulisususu/activation-code-system`。

## Shared business source

`overlayonly` 必须继续复用以下已验证源码，不要复制形成第二套业务逻辑：

```text
app/src/main/java/com/wulisu/licenseoverlay/api/
app/src/main/java/com/wulisu/licenseoverlay/config/
app/src/main/java/com/wulisu/licenseoverlay/core/
```

服务器接口、Basic Auth、续期规则、解析器变化应优先改共享源码，并同时验证两个模块编译。

## V4 accessibility edition

- 使用 `TYPE_ACCESSIBILITY_OVERLAY`。
- `canRetrieveWindowContent=false`。
- 不读取第三方页面节点。
- 亮屏时显示、灭屏隐藏。

## Overlay-only V1

- **禁止加入 AccessibilityService / BIND_ACCESSIBILITY_SERVICE。**
- 使用 `SYSTEM_ALERT_WINDOW + TYPE_APPLICATION_OVERLAY`。
- 使用用户主动启停的 foreground service。
- targetSdk 34+ 时保持 `specialUse` foreground service 类型、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_SPECIAL_USE` 以及 service property 说明。
- 点击悬浮球时允许 overlay 窗口短暂移除 `FLAG_NOT_FOCUSABLE`、`requestFocus()`、读取一次剪贴板，然后立即恢复不可聚焦状态。
- 禁止为了读剪贴板启动 Activity 或把主应用拉到前台。
- 亮屏时显示、灭屏隐藏。

## Privacy invariants

两个版本都：

- 不后台监听剪贴板。
- 剪贴板全文只用于 RAM 内解析，不落盘、不上传。
- 不 OCR、不截图、不录屏。
- 不读取聊天节点。
- 不自动点击、自动发消息。
- Basic Auth 密码、Bearer Token 不得提交到公开源码。
- 私密预配置构建通过 `ACTIVATION_BASE_URL`、`ACTIVATION_BASIC_USER`、`ACTIVATION_BASIC_PASSWORD` 注入。

## Server source of truth

当前复用：

```text
GET  /backend/cards?q=<code>&limit=20&offset=0
GET  /api/test-card-stock/list?q=<code>&page=1&page_size=20
POST /backend/cards/{id}/activate
POST /backend/cards/{id}/refund/disable
POST /backend/cards/{id}/renew
POST /backend/cards/{id}/unbind
```

客户端对模糊查询结果必须二次检查 `card_secret == code || card_no == code`。

## Renew semantics

- `1..998`：有限小时数。
- `999`：永久。
- 默认 `720` 小时。
- 不允许 >999。

## Validation

任何共享业务或构建修改至少执行：

```text
:app:assembleDebug
:overlayonly:assembleDebug
```

GitHub Actions 必须保留两个 APK Artifact。不要为了其中一版修复而破坏另一版的包名、权限或安装共存能力。
