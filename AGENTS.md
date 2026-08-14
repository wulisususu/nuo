# AGENTS.md

## Project intent

这是一个自用的超轻量 Android 悬浮激活助手：闲鱼/千牛发货 -> 复制整段话术 -> 点击悬浮球 -> 提取纯数字激活码 -> 调 `wulisususu/activation-code-system` 执行管理动作。

## Hard scope

只做：

1. 闲鱼 / 千牛 / 已学习分身的前台门禁。
2. Accessibility Overlay 悬浮球与面板。
3. 用户主动点击后一次性读取剪贴板。
4. 激活码纯数字解析。
5. 查询 + 激活 / 退款停用 / 续期 / 解绑。
6. 直接适配 `activation-code-system` 当前 API。
7. HTTP/HTTPS + Basic Auth/Bearer 的最小网络适配。

不要加入 OCR、截图、录屏、聊天节点读取、自动发消息、自动点击目标 App、WebView、Compose、Room、Retrofit、OkHttp、统计或广告 SDK。

## Privacy invariants

- `accessibility_service_config.xml` 必须保持 `android:canRetrieveWindowContent="false"`。
- 不后台监听剪贴板。
- 剪贴板全文只用于 RAM 内解析，不落盘、不上传。
- Basic Auth 密码、Bearer Token 等秘密不得提交到公开源码。
- 构建预配置 APK 时通过 `ACTIVATION_BASE_URL`、`ACTIVATION_BASIC_USER`、`ACTIVATION_BASIC_PASSWORD` 注入。

## Server source of truth

服务器真实实现以 `wulisususu/activation-code-system` 的 `main` 为准。当前复用：

```text
GET  /backend/cards?q=<code>&limit=20&offset=0
GET  /api/test-card-stock/list?q=<code>&page=1&page_size=20
POST /backend/cards/{id}/activate
POST /backend/cards/{id}/refund/disable
POST /backend/cards/{id}/renew
POST /backend/cards/{id}/unbind
```

`q` 是模糊搜索，因此客户端必须二次检查：

```text
item.card_secret == code || item.card_no == code
```

只有正式授权记录可以执行四个写动作。库存码只读展示。

## Authentication

网络层支持：

1. Basic Auth：用户名和密码都非空时优先使用。
2. Bearer Token：仅在 Basic Auth 未配置时使用。

不要同时发两个 `Authorization` 值。

当前服务端入口仍可能是普通 HTTP，因此 Manifest 暂时允许 cleartext。若生产入口迁移 HTTPS，应删除或收紧 cleartext 配置。

## Renew semantics

- `1..998`：有限小时数。
- `999`：永久。
- 默认 `720` 小时。
- 不允许 >999，防止后端 `>=999` 语义导致意外永久。

## Lightweight rules

优先 Android Framework + Kotlin stdlib。新增第三方依赖前先证明 Framework 无法合理实现。Release 保持 R8 + shrinkResources。

## Concurrency invariants

- 写请求处理中禁用动作按钮。
- `latestRequestId` 防止旧响应覆盖新激活码。
- 多候选码禁止写操作。
- `refund_disable`、`unbind` 保留二次确认。
- 不自动重试写动作，尤其是 `renew`。
- 写动作前重新根据 code 解析 card_id，不永久缓存映射。

## Validation

至少执行已有纯 Kotlin smoke test；Android 完整构建以 GitHub Actions `:app:assembleDebug` 为准，并保留 APK Artifact。
