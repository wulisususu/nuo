# AGENTS.md

## Project intent

这是一个自用的超轻量 Android 悬浮激活助手。日常流程固定为：闲鱼/千牛发货 -> 用户复制整段话术 -> 点击悬浮球 -> 提取纯数字激活码 -> 调 `wulisususu/activation-code-system` 执行管理动作。

## Hard scope

V1/V2 只做：

1. 闲鱼 / 千牛 / 已学习分身的前台门禁。
2. Accessibility Overlay 悬浮球与面板。
3. 用户主动点击后一次性读取剪贴板。
4. 激活码纯数字解析。
5. 查询 + 四个动作：`activate`、`refund_disable`、`renew`、`unbind`。
6. 直接适配 `activation-code-system` 当前后台卡密 API。

不要顺手加入：OCR、截图、录屏、聊天页面节点读取、自动发消息、自动点击闲鱼/千牛、WebView、Compose、Room、Retrofit、OkHttp、统计 SDK、广告 SDK。

## Privacy / permission invariant

`accessibility_service_config.xml` 必须保持 `android:canRetrieveWindowContent="false"`。AccessibilityService 只用事件的 `packageName` 做前台 App 门禁。

不要后台监听剪贴板。Android 10+ 必须保持“用户点击悬浮球 -> ClipboardBridgeActivity 获得焦点 -> 读取一次 -> finish”的模型。

剪贴板全文只用于内存解析，不落盘、不写日志、不上传。网络层只处理最终提取出的纯数字激活码。

## Server source of truth

服务器真实实现以 `wulisususu/activation-code-system` 的 `main` 为准。当前已确认并直接复用：

```text
GET  /backend/cards?q=<code>&limit=20&offset=0
GET  /api/test-card-stock/list?q=<code>&page=1&page_size=20
POST /backend/cards/{id}/activate
POST /backend/cards/{id}/refund/disable
POST /backend/cards/{id}/renew
POST /backend/cards/{id}/unbind
```

Android 不再依赖占位的 `/api/license/query` 与 `/api/license/action`。

所有服务端差异必须集中在 `api/LicenseApi.kt` / `core/ServerContract.kt`，不要把 URL 和 JSON 字段散落到 Overlay UI。

## Exact lookup invariant

后台列表接口的 `q` 是模糊搜索，因此客户端必须二次检查：

```text
item.card_secret == code || item.card_no == code
```

只有完全匹配一条正式授权记录才可执行写动作。零条则查询库存；多条完全匹配视为服务器数据异常并停止操作。

库存码只读展示状态，不允许把 `mark-issued`、`discard` 等库存操作伪装成正式授权的“激活/退款停用/续期/解绑”。

## Action semantics

四个动作的语义必须与服务器保持一致：

- `activate` -> `/backend/cards/{id}/activate`
- `refund_disable` -> `/backend/cards/{id}/refund/disable`
- `renew` -> `/backend/cards/{id}/renew`
- `unbind` -> `/backend/cards/{id}/unbind`

不要把 `refund_disable` 改成普通 disable。它会停用卡并按现有服务端规则锁定退款设备。

### renew

服务端 `renew_card` 当前规则：

- 只允许 `status == expired` 且仍有 current binding 的卡续期；
- `hours < 999` 为有限续期；
- `hours >= 999` 变永久。

因此 Android 强制 `renewHours in 1..999`：

- `1..998`：有限小时数；
- `999`：明确的永久操作。

默认 `720` 小时（30 天）。不要把任意“天数 × 24”无上限传给服务器，否则可能因 `>=999` 意外变永久。

## Lightweight rules

优先 Android Framework + Kotlin stdlib。新增任何第三方依赖前先证明 Framework 无法合理实现。

Release 必须保持 `minifyEnabled = true` 和 `shrinkResources = true`。不要引入 AndroidX，除非出现明确且无法绕开的 API 需要。

## Target app / clone behavior

默认包：`com.taobao.idlefish`、`com.taobao.qianniu`。OEM 分身不可硬猜后缀，使用 `TargetAppRegistry` 学习实际 packageName。

若 OEM 使用独立 Profile 且剪贴板隔离，不要尝试跨 Profile 绕过；在对应 Profile 安装/运行助手并做实机验证。

## Concurrency invariants

- 写请求处理中禁用动作按钮。
- `latestRequestId` 防止旧响应覆盖新激活码。
- 多个候选激活码时禁止自动执行动作。
- `refund_disable`、`unbind` 必须保留二次确认。
- Android 客户端不要自动重试写动作，尤其是 `renew`。
- 每次写动作前重新按 code 解析 card_id，不永久缓存 code -> id 映射。

## Validation before commit

至少执行 `scripts/CoreSmokeTest.kt` 的纯 Kotlin 烟雾测试。有 Android SDK/Gradle 时执行 `gradle :app:assembleDebug`。

GitHub Actions 必须保持 Debug APK 构建与 Artifact 上传。若服务器接口发生变化，先读取 `activation-code-system` 当前 main，再修改 adapter，不要凭旧文档猜字段。
