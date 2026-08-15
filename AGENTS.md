# AGENTS.md

## Current scope

仓库只维护无需无障碍的悬浮窗版。禁止重新引入 AccessibilityService、BIND_ACCESSIBILITY_SERVICE、TYPE_ACCESSIBILITY_OVERLAY 或旧 V4 实现。

## Branch policy

- `main`：继续开发。
- `floating-window`：当前悬浮窗 V2 稳定基线。

除非用户明确要求同步稳定分支，后续修改只进入 `main`。

## Overlay rules

- 使用 `SYSTEM_ALERT_WINDOW + TYPE_APPLICATION_OVERLAY`。
- 使用用户主动启停的 foreground service。
- Android 14+ 保持 `specialUse` foreground service 类型。
- 点击悬浮球时允许 overlay 短暂获取焦点并读取一次剪贴板，随后恢复不可聚焦。
- 禁止为了读取剪贴板启动 Activity。
- 亮屏显示、灭屏隐藏。

## Privacy

- 不后台监听剪贴板。
- 剪贴板全文不落盘、不上传。
- 不 OCR、不截图、不录屏。
- 不读取第三方页面节点。
- Basic Auth/Token 不得提交到公开源码；私密构建通过环境变量注入。

## Create semantics

未存在的 6–12 位纯数字允许创建正式测试服通用卡：

```text
POST /backend/cards
status=activated
game_scope=ALL
scope=ALL
duration_kind=PERMANENT
source_type=DIRECT
binding_status=unbound
```

已存在的卡不得重复创建。

## Validation

构建只需要：

```text
:app:assembleDebug
```
