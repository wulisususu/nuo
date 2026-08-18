# Android License Overlay Assistant

当前仓库只保留**无需无障碍的悬浮窗版**。

## 分支结构

- `main`：继续开发、升级；当前已进入 V3。
- `floating-window`：悬浮窗 V2 稳定基线，后续默认不修改。

## 当前版本（main）

```text
applicationId: com.wulisu.licenseoverlay.overlayonly
version: 3.0.0-noaccessibility
```

## V3 核心能力

- 不申请、不使用 AccessibilityService。
- 使用 `SYSTEM_ALERT_WINDOW + TYPE_APPLICATION_OVERLAY`。
- 用户主动开启前台悬浮服务；亮屏显示、灭屏隐藏。
- 点击“码”时悬浮窗口短暂获得焦点，只读取一次剪贴板，不启动 Activity。
- 从整段发货文字中提取 6–12 位纯数字激活码。
- 从剪贴板上下文识别已适配游戏：
  - 绝区零 / ZZZ / Zenless / Remielle -> `ZZZ`
  - 鸣潮 / WUWA / Wuthering Waves / Zigrika -> `WUWA`
- 查询时同时检查正式授权表和库存表；只有两边都不存在时才允许创建。
- 已存在正式授权时继续显示：激活 / 退款停用 / 解绑。
- 已存在库存卡时显示库存状态，不允许重复创建。
- 未创建且识别到 ZZZ/WUWA 时，只显示：`创建通用 / 创建专属`。
- 未识别到已适配游戏时，保持 V2 的普通创建页面。
- 新建正式卡状态为 `unused`（未使用），不再提前写成 `activated`。
- 新卡第一次被真实客户端成功验证时，再由服务器自动激活并绑定机器。
- 状态、绑定、授权范围在悬浮窗内显示为中文。

## V3 创建规则

创建调用：

```text
POST /backend/cards
```

共同字段：

```text
card_no = 输入数字
card_secret = 同一串数字
status = unused
duration_kind = PERMANENT
source_type = DIRECT
binding_status = unbound
```

### 创建通用

```text
game_scope = ALL
scope = ALL
legacy_compatible = true
```

### 创建绝区零专属

```text
game_scope = ZZZ
scope = ZZZ
legacy_compatible = false
```

### 创建鸣潮专属

```text
game_scope = WUWA
scope = WUWA
legacy_compatible = false
```

## 服务器游戏校验规则

服务器不信任客户端直接传入的 `game` 字段，实际授权范围由服务端 `app_id -> scope` 映射决定。

当前相关映射：

```text
zzz-remielle -> ZZZ
zzz          -> ZZZ
wuwa-zigrika-commercial -> WUWA
wuwa                  -> WUWA
```

客户端走 `/api/verify` 新协议时，`verify` 阶段至少要带：

```text
stage=verify
hwid=<机器码>
card=<卡号>
password=<卡密>
app_id=<服务端已登记的 app_id>
request_id=<8~128 字符、同一次重试保持一致>
```

服务端会检查：app_id 是否已映射、设备是否被封禁/退款锁定、卡号和卡密、卡的 scope 是否允许当前 app_id、是否已经绑定其他设备、当前设备对应 scope 槽位是否被占用，以及卡的状态/到期时间。

## 管理接口

```text
GET  /backend/cards?q=<code>&limit=20&offset=0
GET  /api/test-card-stock/list?q=<code>&page=1&page_size=20
POST /backend/cards
POST /backend/cards/{id}/activate
POST /backend/cards/{id}/refund/disable
POST /backend/cards/{id}/unbind
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
