# Android License Overlay Assistant

当前仓库只保留**无需无障碍的悬浮窗版**。

## 分支结构

- `main`：后续继续开发、升级。
- `floating-window`：当前悬浮窗 V2 的稳定基线。

创建 `floating-window` 时，两条分支内容完全一致；后续新功能优先只改 `main`。

## 当前版本

```text
applicationId: com.wulisu.licenseoverlay.overlayonly
version: 2.0.0-noaccessibility
```

## 核心能力

- 不申请、不使用 AccessibilityService。
- 使用 `SYSTEM_ALERT_WINDOW + TYPE_APPLICATION_OVERLAY`。
- 用户主动开启前台悬浮服务；亮屏显示、灭屏隐藏。
- 点击“码”时悬浮窗口短暂获得焦点，只读取一次剪贴板，不启动 Activity。
- 识别 6–12 位纯数字激活码。
- 已存在授权可执行：激活 / 退款停用 / 解绑。
- 未创建的 6–12 位纯数字可直接“创建”为测试服 ALL 通用永久正式卡，创建后状态为已激活、未绑定。
- 状态与绑定信息在悬浮窗内显示为中文。
- 支持 HTTP / HTTPS、Basic Auth，以及 Basic 未配置时的 Bearer Token 回退。

## 服务器接口

```text
GET  /backend/cards?q=<code>&limit=20&offset=0
GET  /api/test-card-stock/list?q=<code>&page=1&page_size=20
POST /backend/cards
POST /backend/cards/{id}/activate
POST /backend/cards/{id}/refund/disable
POST /backend/cards/{id}/unbind
```

“创建”调用 `POST /backend/cards`，固定创建：

```text
card_no = 输入数字
card_secret = 同一串数字
status = activated
game_scope = ALL
scope = ALL
duration_kind = PERMANENT
source_type = DIRECT
binding_status = unbound
```

## 隐私边界

- 不后台监听剪贴板。
- 剪贴板全文只在 RAM 内解析，不落盘、不上传。
- 不 OCR、不截图、不录屏。
- 不读取第三方 App 页面节点。
- 不自动点击、自动发消息。
- Basic Auth 密码和 Token 不提交到公开源码。

私密构建可通过以下环境变量注入：

```text
ACTIVATION_BASE_URL
ACTIVATION_BASIC_USER
ACTIVATION_BASIC_PASSWORD
```

## 构建

```bash
gradle --no-daemon :app:assembleDebug
```

产物：

```text
app/build/outputs/apk/debug/app-debug.apk
```
