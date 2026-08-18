# AGENTS.md

## Current scope

仓库只维护无需无障碍的悬浮窗版。禁止重新引入 AccessibilityService、BIND_ACCESSIBILITY_SERVICE、TYPE_ACCESSIBILITY_OVERLAY 或旧 V4 实现。

## Branch policy

- `main`：继续开发；当前为 V3。
- `floating-window`：V2 稳定基线。

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

## Game detection

V3 当前只适配：

- `ZZZ`：绝区零 / ZZZ / Zenless / Remielle。
- `WUWA`：鸣潮 / WUWA / Wuthering Waves / Zigrika。

未识别到这两类时必须保持 V2 默认页面行为，不要擅自把其他游戏映射到 ZZZ/WUWA。

## Create semantics

只有正式授权表和库存表都不存在该 6–12 位纯数字时才能创建。

共同字段：

```text
POST /backend/cards
status=unused
duration_kind=PERMANENT
source_type=DIRECT
binding_status=unbound
```

创建通用：

```text
game_scope=ALL
scope=ALL
legacy_compatible=true
```

创建专属：

```text
ZZZ  -> game_scope=ZZZ,  scope=ZZZ,  legacy_compatible=false
WUWA -> game_scope=WUWA, scope=WUWA, legacy_compatible=false
```

禁止在创建阶段预先写 `status=activated`。首次真实客户端验证成功时，服务器会从 `unused` 自动切换为 `activated` 并建立机器绑定。

## Server protocol source of truth

服务端根据 `app_id` 映射 scope，不信任客户端 `game` 字段。

```text
zzz-remielle -> ZZZ
zzz -> ZZZ
wuwa-zigrika-commercial -> WUWA
wuwa -> WUWA
```

新协议 `/api/verify` 的 verify 阶段带 app_id 时必须同时带 request_id。服务端还校验 hwid、card、password、设备状态、卡 scope、卡状态/到期、绑定关系和设备 scope 槽位。

## Validation

构建至少执行：

```text
:app:assembleDebug
```
